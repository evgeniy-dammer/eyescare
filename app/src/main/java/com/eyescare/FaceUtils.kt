package com.eyescare

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Утилиты геометрии лица поверх ML Kit. Держат зависимость от `Face` в одном месте,
 * чтобы её не дублировали `CameraAnalyzer` и `CalibrationActivity`.
 */
object FaceUtils {

    /**
     * Межзрачковое расстояние в пикселях (расстояние между центрами глаз) или `null`,
     * если хотя бы один глаз не распознан.
     */
    fun pixelIpd(face: Face): Double? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        return if (leftEye != null && rightEye != null) {
            sqrt((rightEye.x - leftEye.x).pow(2) + (rightEye.y - leftEye.y).pow(2).toDouble())
        } else {
            null
        }
    }
}
