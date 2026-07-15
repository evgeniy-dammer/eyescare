package com.eyecare

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.hypot

/**
 * Прототип: обёртка над MediaPipe Face Landmarker для замера горизонтального диаметра радужки
 * в пикселях. On-device, оффлайн. Модель — `assets/face_landmarker.task` (478 landmarks, включая
 * радужку 468–477).
 *
 * НЕ потокобезопасен: [measureIrisDiameterPx] и [close] должны вызываться с одного потока
 * (в проекте — `cameraExecutor`), на котором был создан [FaceLandmarker].
 *
 * Диаметр возвращается в пиксельном масштабе переданного [Bitmap]. Если это неотмасштабированный
 * (только повёрнутый) кадр анализа, масштаб совпадает с `image.width`, на котором строится `focal_px`
 * в [IrisDistanceCalculator] — тогда сравнение с IPD-методом честное.
 */
class IrisMeasurer(private val context: Context) {

    private var landmarker: FaceLandmarker? = null
    private var initFailed = false

    private fun ensureLandmarker(): FaceLandmarker? {
        if (landmarker != null) return landmarker
        if (initFailed) return null
        return try {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .build()
            FaceLandmarker.createFromOptions(context, options).also { landmarker = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init FaceLandmarker", e)
            initFailed = true
            null
        }
    }

    /**
     * @return средний горизонтальный диаметр радужки (обоих глаз, если оба видны) в пикселях
     *         [bitmap], или `null`, если лицо/радужка не распознаны или модель недоступна.
     */
    fun measureIrisDiameterPx(bitmap: Bitmap): Double? {
        val lm = ensureLandmarker() ?: return null
        val result: FaceLandmarkerResult = try {
            lm.detect(BitmapImageBuilder(bitmap).build())
        } catch (e: Exception) {
            Log.e(TAG, "detect failed", e)
            return null
        }

        val faces = result.faceLandmarks()
        if (faces.isEmpty()) return null
        val pts = faces[0]
        if (pts.size <= RIGHT_IRIS.last()) return null

        val w = bitmap.width
        val h = bitmap.height

        val left = irisDiameterPx(pts, LEFT_IRIS, w, h)
        val right = irisDiameterPx(pts, RIGHT_IRIS, w, h)

        return when {
            left != null && right != null -> (left + right) / 2.0
            else -> left ?: right
        }
    }

    /**
     * Диаметр как максимум попарных расстояний между 4 точками контура радужки. Точки лежат ~на
     * окружности вокруг центра, поэтому наибольшая пара ≈ диаметр — робастно к порядку индексов.
     */
    private fun irisDiameterPx(
        pts: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        idx: IntRange,
        w: Int,
        h: Int,
    ): Double? {
        // idx[0] — центр; idx[1..4] — контур.
        val perimeter = (idx.first + 1..idx.last).map { i ->
            val p = pts[i]
            floatArrayOf(p.x() * w, p.y() * h)
        }
        if (perimeter.size < 2) return null
        var max = 0.0
        for (a in perimeter.indices) {
            for (b in a + 1 until perimeter.size) {
                val d = hypot((perimeter[a][0] - perimeter[b][0]).toDouble(), (perimeter[a][1] - perimeter[b][1]).toDouble())
                if (d > max) max = d
            }
        }
        return if (max > 0.0) max else null
    }

    fun close() {
        try {
            landmarker?.close()
        } catch (e: Exception) {
            Log.e(TAG, "close failed", e)
        }
        landmarker = null
    }

    companion object {
        private const val TAG = "IrisMeasurer"
        private const val MODEL_ASSET = "face_landmarker.task"
        // Индексы radужки в 478-точечной сетке MediaPipe: [центр, +4 точки контура].
        private val LEFT_IRIS = 468..472
        private val RIGHT_IRIS = 473..477
    }
}
