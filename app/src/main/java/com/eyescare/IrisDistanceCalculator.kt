package com.eyescare

import kotlin.math.cos

/**
 * Прототип расчёта дистанции по диаметру радужки (MediaPipe Face Mesh, ТЗ п. «MediaPipe Face Mesh —
 * опционально, для точности»). Геометрия та же, что в [DistanceCalculator], но вместо межзрачкового
 * расстояния используется горизонтальный диаметр радужки — почти константа у всех людей
 * ([IRIS_DIAMETER_MM] ≈ 11.7 мм, разброс ~±3%), поэтому калибровка IPD не нужна.
 *
 * `focal_px` считается ровно как в [DistanceCalculator], чтобы сравнение двух методов было честным
 * (различается только физический размер ориентира и сам пиксельный замер).
 *
 * Класс изолирован от Android/MediaPipe (принимает примитивы) и покрыт unit-тестами.
 */
class IrisDistanceCalculator(private val settings: SettingsRepository) {

    private var lastDistance: Float? = null
    private val alpha = 0.3f

    /** Сбрасывает EMA-сглаживание при потере лица. */
    fun reset() {
        lastDistance = null
    }

    /**
     * @param irisDiameterPx горизонтальный диаметр радужки в пикселях (в том же пиксельном масштабе,
     *        что и [imageWidth] — т.е. в масштабе кадра анализа).
     * @return дистанция в сантиметрах или `null`, если данных недостаточно.
     */
    fun calculate(irisDiameterPx: Double, headEulerAngleX: Float, headEulerAngleY: Float, imageWidth: Int): Float? {
        if (irisDiameterPx <= 0.0) return null

        val verticalTilt = Math.toRadians(headEulerAngleX.toDouble())
        val horizontalTilt = Math.toRadians(headEulerAngleY.toDouble())
        val correctedIrisPx = irisDiameterPx * cos(verticalTilt) * cos(horizontalTilt)
        if (correctedIrisPx <= 0.0) return null

        val focalPixels = getFocalLengthInPixels(imageWidth).toDouble()
        if (focalPixels == 0.0) return null

        val rawDistanceMm = (focalPixels * IRIS_DIAMETER_MM) / correctedIrisPx

        val newDistance = lastDistance?.let { alpha * rawDistanceMm.toFloat() + (1 - alpha) * it }
            ?: rawDistanceMm.toFloat()
        lastDistance = newDistance

        return newDistance / 10f // мм → см
    }

    private fun getFocalLengthInPixels(imageWidth: Int): Float {
        val hwFocal = settings.getHardwareFocalLength()
        val hwSensorWidthMm = settings.getHardwareSensorWidth()
        if (hwFocal > 0.0f && hwSensorWidthMm > 0.0f) {
            return (hwFocal / hwSensorWidthMm) * imageWidth
        }
        return 0.0f
    }

    companion object {
        /**
         * Горизонтальный диаметр видимой радужки человека. Анатомическая константа: ~11.7 мм со
         * стандартным отклонением ~0.4 мм у взрослых, слабо зависит от возраста/этноса — на этом
         * основан метод «depth from iris». Даёт метрическую дистанцию без калибровки под пользователя.
         */
        const val IRIS_DIAMETER_MM = 11.7
    }
}
