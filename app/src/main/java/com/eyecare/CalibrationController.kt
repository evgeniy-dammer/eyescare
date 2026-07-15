package com.eyecare

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Инкапсулирует камеру, распознавание лица и логику калибровки для экрана калибровки.
 * Отделён от Composable, чтобы UI мог стартовать/останавливать камеру по жизненному циклу
 * вкладки ([start]/[stop]). Compose-состояние ([uiState], [ipdInput], [ipdError]) наблюдается
 * из [CalibrationScreen].
 */
class CalibrationController(
    private val activity: AppCompatActivity,
    private val settings: SettingsRepository,
) {
    private val calculator = DistanceCalculator(settings)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: FaceDetector? = null

    // Защита от гонки с асинхронным колбэком future (аналогично CameraAnalyzer).
    @Volatile
    private var active = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentState = CalibrationState.SEARCHING
    private var stableFrames = 0
    private var onFinished: (() -> Unit)? = null

    var uiState by mutableStateOf(CalibrationUiState())
        private set
    var ipdInput by mutableStateOf("")
        private set
    var ipdError by mutableStateOf<String?>(null)
        private set

    /** Запускает камеру и анализ. [onFinished] вызывается после успешной калибровки. */
    @SuppressLint("UnsafeOptInUsageError")
    fun start(previewView: PreviewView, onFinished: () -> Unit) {
        active = true
        this.onFinished = onFinished
        // Гарантируем наличие аппаратных параметров камеры до анализа кадров — иначе авто-калибровка
        // (перевод IPD из пикселей) молча ничего не сохранит при первом запуске. Выполняется на
        // том же single-thread executor, что и анализ, поэтому завершится до первого кадра.
        if (!cameraExecutor.isShutdown) cameraExecutor.execute { CameraHardware.fetchAndSave(activity, settings) }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            // stop()/shutdown() мог выполниться, пока future разрешался — тогда камеру не поднимаем.
            if (!active || cameraExecutor.isShutdown) return@addListener
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            setupImageAnalysis()
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    /** Останавливает камеру и сбрасывает состояние (вызывается при уходе с вкладки). */
    fun stop() {
        active = false
        mainHandler.removeCallbacksAndMessages(null) // отменяем отложенный finishSoon
        onFinished = null
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera", e)
        }
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        detector?.close()
        detector = null
        currentState = CalibrationState.SEARCHING
        stableFrames = 0
        uiState = CalibrationUiState()
        ipdError = null
    }

    /** Освобождает executor (вызывается при уничтожении Activity). */
    fun shutdown() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        if (!cameraExecutor.isShutdown) cameraExecutor.shutdown()
    }

    fun onIpdChange(value: String) {
        ipdInput = value
        ipdError = null
    }

    /**
     * Сохраняет введённый вручную IPD. Имеет приоритет над автокалибровкой: анализатор
     * отключается, значение валидируется и сохраняется.
     */
    fun saveManualIpd() {
        // numberDecimal в части локалей использует запятую — принимаем оба разделителя.
        val ipd = ipdInput.trim().replace(',', '.').toFloatOrNull()
        if (ipd == null || ipd < SettingsRepository.MIN_IPD_MM || ipd > SettingsRepository.MAX_IPD_MM) {
            ipdError = activity.getString(R.string.calib_ipd_invalid, SettingsRepository.MIN_IPD_MM, SettingsRepository.MAX_IPD_MM)
            return
        }
        ipdError = null
        imageAnalysis?.clearAnalyzer()
        settings.setCalibratedIpd(ipd)
        finishSoon()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupImageAnalysis() {
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        detector = FaceDetection.getClient(options)

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            val activeDetector = detector
            if (mediaImage != null && activeDetector != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                processImage(activeDetector, inputImage) { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun processImage(detector: FaceDetector, image: InputImage, onComplete: () -> Unit) {
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    if (isFaceInGuide(face, image.width, image.height)) {
                        if (currentState != CalibrationState.LOCKING) {
                            stableFrames++
                            if (stableFrames > 5) {
                                setState(CalibrationState.LOCKING)
                            }
                        }
                        if (currentState == CalibrationState.LOCKING) {
                            stableFrames++
                            if (stableFrames >= STABLE_FRAMES_THRESHOLD) {
                                calibrate(face, image.width)
                            }
                        }
                    } else {
                        setState(CalibrationState.ADJUSTING)
                        stableFrames = 0
                    }
                } else {
                    setState(CalibrationState.SEARCHING)
                    stableFrames = 0
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Face detection failed", e) }
            .addOnCompleteListener { onComplete() }
    }

    private fun setState(newState: CalibrationState) {
        if (currentState == newState) return
        currentState = newState
        val instructionRes = when (newState) {
            CalibrationState.SEARCHING -> R.string.calib_face_not_found
            CalibrationState.ADJUSTING -> R.string.calib_hold_still
            CalibrationState.LOCKING -> R.string.calib_calibrating
        }
        activity.runOnUiThread { uiState = CalibrationUiState(instructionRes, newState) }
    }

    private fun isFaceInGuide(face: Face, imageWidth: Int, imageHeight: Int): Boolean {
        // ML Kit отдаёт координаты рамки лица в «выпрямленном» (портретном) пространстве, где по
        // горизонтали идёт УЗКАЯ сторона кадра. Нормируем именно по ней (min из сторон), а не по
        // «сырой» ширине ландшафтного кадра — иначе порог завышен и лицо приходится приближать
        // сильнее, чем показывает экранный овал. min() устойчив к тому, как ML Kit трактует width/height.
        val guideWidth = minOf(imageWidth, imageHeight)
        val faceBox = face.boundingBox
        val guideCenterX = guideWidth / 2f

        // Рамка лица плотнее визуального овала, поэтому порог размера чуть мягче (30%), чтобы захват
        // срабатывал примерно тогда, когда лицо заполняет овал на экране.
        val isCenteredHorizontally = abs(faceBox.centerX() - guideCenterX) < guideWidth * 0.22
        val isLargeEnough = faceBox.width() > guideWidth * 0.30

        return isCenteredHorizontally && isLargeEnough
    }

    private fun calibrate(face: Face, imageWidth: Int) {
        val pixelIPD = FaceUtils.pixelIpd(face)
        if (pixelIPD == null) {
            // Глаза не распознаны в кадре захвата — не завершаем и НЕ отключаем анализатор,
            // иначе экран залипнет: сбрасываем счётчик и ждём следующих кадров.
            stableFrames = 0
            activity.runOnUiThread { setState(CalibrationState.SEARCHING) }
            return
        }

        // Успех возможен только при наличии аппаратных параметров камеры.
        val calibrated = calculator.calibrate(pixelIPD, imageWidth, KNOWN_DISTANCE_CM)
        if (calibrated) {
            imageAnalysis?.clearAnalyzer()
            activity.runOnUiThread { finishSoon() }
        } else {
            // Нет параметров камеры для авто-расчёта — прекращаем и просим ввести IPD вручную.
            imageAnalysis?.clearAnalyzer()
            activity.runOnUiThread {
                ipdError = activity.getString(R.string.calib_auto_unavailable)
                setState(CalibrationState.SEARCHING)
            }
        }
    }

    private fun finishSoon() {
        mainHandler.postDelayed({ onFinished?.invoke() }, 500)
    }

    companion object {
        private const val TAG = "CalibrationController"
        private const val KNOWN_DISTANCE_CM = 30f
        private const val STABLE_FRAMES_THRESHOLD = 15
    }
}
