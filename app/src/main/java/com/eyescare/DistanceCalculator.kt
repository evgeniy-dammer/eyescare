package com.eyescare

import kotlin.math.cos

class DistanceCalculator(private val settings: SettingsRepository) {

    private var lastDistance: Float? = null
    private val ALPHA = 0.3f

    /** Сбрасывает EMA-сглаживание (например, при потере лица), чтобы новое измерение
     *  не смешивалось с устаревшим. */
    fun reset() {
        lastDistance = null
    }

    /**
     * Вычисляет расстояние на основе простых типов, не зависит от ML Kit Face.
     */
    fun calculate(pixelIPD: Double, headEulerAngleX: Float, headEulerAngleY: Float, imageWidth: Int): Float? {
        if (pixelIPD == 0.0) return null

        val verticalTiltAngle = Math.toRadians(headEulerAngleX.toDouble())
        val horizontalTiltAngle = Math.toRadians(headEulerAngleY.toDouble())
        
        val correctedPixelIPD = pixelIPD * cos(verticalTiltAngle) * cos(horizontalTiltAngle)

        if (correctedPixelIPD == 0.0) return null

        val focalPixels = getFocalLengthInPixels(imageWidth).toDouble()
        if (focalPixels == 0.0) {
            // Возвращаем null если нет данных о камере, чтобы избежать деления на ноль
            return null
        }

        val realIpdMm = settings.getIpdMm().toDouble()

        val rawDistance = (focalPixels * realIpdMm) / correctedPixelIPD

        val newDistance = (lastDistance?.let { ALPHA * rawDistance.toFloat() + (1 - ALPHA) * it } ?: rawDistance.toFloat())
        lastDistance = newDistance

        return newDistance / 10f // convert mm to cm
    }

    /**
     * Вычисляет и сохраняет откалиброванный IPD.
     *
     * @return `true`, если IPD рассчитан и сохранён; `false`, если данных недостаточно
     *         (нулевой pixelIPD или отсутствуют аппаратные параметры камеры).
     */
    fun calibrate(pixelIPD: Double, imageWidth: Int, realDistanceCm: Float): Boolean {
        if (pixelIPD > 0) {
            val focalPixels = getFocalLengthInPixels(imageWidth).toDouble()
            if (focalPixels > 0) {
                val calculatedIpd = (realDistanceCm * 10 * pixelIPD) / focalPixels
                settings.setCalibratedIpd(calculatedIpd.toFloat())
                return true
            }
        }
        return false
    }

    private fun getFocalLengthInPixels(imageWidth: Int): Float {
        val hwFocal = settings.getHardwareFocalLength()
        val hwSensorWidthMm = settings.getHardwareSensorWidth()
        if (hwFocal > 0.0f && hwSensorWidthMm > 0.0f) {
            return (hwFocal / hwSensorWidthMm) * imageWidth
        }
        return 0.0f
    }
}