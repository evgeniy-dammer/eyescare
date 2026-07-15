package com.eyecare

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Range

/**
 * Считывает физические параметры фронтальной камеры (фокусное расстояние и ширину сенсора)
 * и сохраняет их в [SettingsRepository]. Нужны для перевода IPD из пикселей в сантиметры
 * ([DistanceCalculator.getFocalLengthInPixels]).
 *
 * Вынесено из [CameraAnalyzer], чтобы и мониторинг, и калибровка ([CalibrationController])
 * могли гарантировать наличие этих данных — иначе авто-калибровка при первом запуске
 * (до первого старта мониторинга) молча ничего не сохраняла бы.
 */
object CameraHardware {

    private const val TAG = "CameraHardware"

    /** Идемпотентно: перечитывает и сохраняет параметры фронтальной камеры. */
    fun fetchAndSave(context: Context, settings: SettingsRepository) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val frontCameraId = cameraManager.cameraIdList.find {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }

            if (frontCameraId == null) {
                Log.e(TAG, "No front camera found.")
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(frontCameraId)
            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

            if (focalLengths?.isNotEmpty() == true && sensorSize != null) {
                val focalLength = focalLengths[0]
                val sensorWidth = sensorSize.width
                settings.saveCameraHardwareProperties(focalLength, sensorWidth)
                if (BuildConfig.DEBUG) Log.d(TAG, "Saved camera properties: f=$focalLength, sensorW_mm=$sensorWidth")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not get camera characteristics.", e)
        }
    }

    /**
     * Возвращает поддерживаемый фронтальной камерой диапазон частоты кадров для ограничения захвата
     * (экономия батареи/CPU): выбираем наименьший верхний предел, но не ниже 15 к/с — чтобы не терять
     * отзывчивость (наш анализ доходит до ~10 к/с). `null`, если данные недоступны — тогда не ограничиваем.
     */
    fun frontCameraLowFpsRange(context: Context): Range<Int>? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val frontId = cameraManager.cameraIdList.find {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: return null
            val ranges = cameraManager.getCameraCharacteristics(frontId)
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList() ?: return null
            val atLeast15 = ranges.filter { it.upper >= 15 }
            (atLeast15.ifEmpty { ranges }).minByOrNull { it.upper * 100 + it.lower }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read AE fps ranges", e)
            null
        }
    }
}
