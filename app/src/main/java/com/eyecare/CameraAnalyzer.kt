package com.eyecare

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class CameraAnalyzer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settingsRepository: SettingsRepository,
    private val onDistanceUpdate: (Float?) -> Unit,
    private val onStatusUpdate: (String) -> Unit,
    private val onThresholdExceeded: (Boolean) -> Unit
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val distanceCalculator = DistanceCalculator(settingsRepository)

    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceDetector: FaceDetector? = null

    // Защита от гонки: колбэк ProcessCameraProvider.getInstance() приходит асинхронно; если к тому
    // моменту вызван stop()/shutdown(), не привязываем камеру заново (и не трогаем гашёный executor).
    @Volatile
    private var active = false

    // Динамический FPS (экономия батареи): пропускаем кадры, если с последнего обработанного прошло
    // меньше интервала, который зависит от текущей дистанции (см. FrameRateGovernor).
    private var lastProcessedMs = 0L
    private var lastPacingDistanceCm: Float? = null

    // Гистерезис оверлея (ТЗ п. 6.5): включение по 3 кадрам подряд ниже порога,
    // выключение по 5 кадрам подряд выше порога — чтобы баннер не мигал на границе.
    private val overlayHysteresis = OverlayHysteresis()

    private val orientationEventListener by lazy {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageAnalysis?.targetRotation = rotation
            }
        }
    }

    private val distanceThresholdCm: Int
        get() = settingsRepository.getDistanceThreshold()

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalCamera2Interop::class)
    fun start() {
        active = true
        orientationEventListener.enable()
        CameraHardware.fetchAndSave(context, settingsRepository)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            // stop()/shutdown() мог выполниться, пока future разрешался — тогда ничего не привязываем.
            if (!active || cameraExecutor.isShutdown) return@addListener
            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider

            // ~480p достаточно для детекции лица на 40–60 см — меньше нагрузка на камеру/ISP/ML
            // (экономия батареи). Полная ширина сенсора сохраняется, поэтому расчёт дистанции не меняется.
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()

            val analysisBuilder = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Ограничиваем частоту захвата (~15 к/с вместо 30) — камера снимает непрерывно, и это
            // основной расход CPU при включённом экране. Наш анализ доходит до ~10 к/с, так что 15 хватает.
            CameraHardware.frontCameraLowFpsRange(context)?.let { range ->
                Camera2Interop.Extender(analysisBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
            }
            imageAnalysis = analysisBuilder.build()

            // Классификация (улыбка/открытые глаза) НЕ используется — только зрачки и наклон головы,
            // поэтому CLASSIFICATION_MODE не включаем ради экономии батареи.
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
            faceDetector?.close()
            val detector = FaceDetection.getClient(options)
            faceDetector = detector

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                // Троттлинг частоты анализа ради батареи: на безопасной дистанции обрабатываем реже.
                val now = SystemClock.elapsedRealtime()
                if (now - lastProcessedMs < FrameRateGovernor.intervalMsFor(lastPacingDistanceCm, distanceThresholdCm)) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastProcessedMs = now

                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            if (faces.isNotEmpty()) {
                                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                                if (face != null) {
                                    // Извлекаем простые типы данных из объекта Face
                                    val pixelIPD = FaceUtils.pixelIpd(face)
                                    if (pixelIPD != null) {
                                        val distance = distanceCalculator.calculate(
                                            pixelIPD = pixelIPD,
                                            headEulerAngleX = face.headEulerAngleX,
                                            headEulerAngleY = face.headEulerAngleY,
                                            imageWidth = image.width
                                        )
                                        lastPacingDistanceCm = distance
                                        onDistanceUpdate(distance)
                                        if (distance != null) {
                                            handleDistance(distance)
                                        }
                                    } else {
                                        onFaceLost()
                                    }
                                }
                            } else {
                                onFaceLost()
                            }
                        }
                        .addOnFailureListener { e -> Log.e("CameraAnalyzer", "Face detection failed", e); onFaceLost() }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }

            bindCameraUseCases(cameraProvider)

        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        try {
            cameraProvider.unbindAll()
            imageAnalysis?.let { cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, it) }
            onStatusUpdate(context.getString(R.string.status_active)) // Сообщаем, что все хорошо
        } catch (e: Exception) {
            if (e.javaClass.name == "androidx.camera.core.CameraInUseException") {
                Log.w("CameraAnalyzer", "Камера уже используется: " + e.message)
                onStatusUpdate(context.getString(R.string.status_camera_busy))
                scheduleRetry()
            } else {
                Log.e("CameraAnalyzer", "Use case binding failed", e)
                onDistanceUpdate(null)
            }
        }
    }
    
    private fun scheduleRetry() {
        retryRunnable = Runnable { start() }
        handler.postDelayed(retryRunnable!!, 5000)
    }

    /**
     * Освобождает камеру и детектор, но НЕ трогает executor — можно снова вызвать [start]
     * (например, при выключении/включении экрана ради экономии батареи).
     */
    fun stop() {
        active = false
        retryRunnable?.let { runnable -> handler.removeCallbacks(runnable) } // Отменяем повтор
        orientationEventListener.disable()
        try {
            // Используем уже полученный провайдер — не блокируем поток на .get().
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("CameraAnalyzer", "Error unbinding camera", e)
        }
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider = null
        faceDetector?.close()
        faceDetector = null
        lastProcessedMs = 0L
        lastPacingDistanceCm = null
    }

    /** Финальное освобождение ресурсов при уничтожении сервиса (executor больше не нужен). */
    fun shutdown() {
        stop()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
    }

    /** Лицо не найдено/глаза не распознаны: сбрасываем сглаживание, чтобы при возврате не
     *  смешивать с устаревшим значением, и сообщаем UI об отсутствии данных. */
    private fun onFaceLost() {
        distanceCalculator.reset()
        lastPacingDistanceCm = null
        onDistanceUpdate(null)
        // Если лицо пропало надолго, пока висел баннер «слишком близко» — снимаем его.
        overlayHysteresis.onFaceLost()?.let { onThresholdExceeded(it) }
    }

    private fun handleDistance(distance: Float) {
        // Дистанция уже сглажена EMA в DistanceCalculator; гистерезис (3/5 кадров) сам
        // защищает от дребезга на границе порога — отдельный медианный фильтр не нужен.
        overlayHysteresis.update(distance, distanceThresholdCm)?.let { onThresholdExceeded(it) }
    }
}