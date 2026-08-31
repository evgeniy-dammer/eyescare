package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Test

class PostureMathTest {

    private val g = 9.81f

    // Знак осей взят не из документации, а с устройства: лежащий экраном вверх телефон даёт
    // gravity = (0.40, 0.71, 9.77) — вектор направлен ВВЕРХ, поэтому «лежит» это +Z, а
    // «вертикально» это +Y (Redmi 2201117TG, Android 15, 2026-08-31).

    @Test
    fun `upright phone has zero tilt`() {
        assertEquals(0f, PostureMath.deviceTiltFromVerticalDeg(gravityY = g, gravityZ = 0f), 0.5f)
    }

    @Test
    fun `phone lying face up is tilted ninety degrees`() {
        assertEquals(90f, PostureMath.deviceTiltFromVerticalDeg(gravityY = 0f, gravityZ = g), 0.5f)
    }

    @Test
    fun `real reading from a phone on a desk is almost flat`() {
        // Ровно тот замер, которым проверялся знак осей.
        assertEquals(86f, PostureMath.deviceTiltFromVerticalDeg(gravityY = 0.71f, gravityZ = 9.77f), 1f)
    }

    @Test
    fun `phone leaned back forty five degrees`() {
        val c = g * 0.7071f
        assertEquals(45f, PostureMath.deviceTiltFromVerticalDeg(gravityY = c, gravityZ = c), 0.5f)
    }

    @Test
    fun `phone leaned toward the user gives negative tilt`() {
        val c = g * 0.7071f
        assertEquals(-45f, PostureMath.deviceTiltFromVerticalDeg(gravityY = c, gravityZ = -c), 0.5f)
    }

    @Test
    fun `upright phone and frontal face means no flexion`() {
        assertEquals(0f, PostureMath.neckFlexionDeg(deviceTiltDeg = 0f, headEulerXDeg = 0f), 0.001f)
    }

    @Test
    fun `phone on the table and frontal face means the neck is fully bent`() {
        // Ровно тот случай, который один только headEulerAngleX не поймал бы: камера видит лицо
        // фронтально, а шея согнута на 90°.
        assertEquals(90f, PostureMath.neckFlexionDeg(deviceTiltDeg = 90f, headEulerXDeg = 0f), 0.001f)
    }

    @Test
    fun `head lowered in front of an upright phone`() {
        // Положительный headEulerAngleX = лицо смотрит вверх, поэтому опущенная голова даёт минус.
        assertEquals(20f, PostureMath.neckFlexionDeg(deviceTiltDeg = 0f, headEulerXDeg = -20f), 0.001f)
    }

    @Test
    fun `raised face compensates a leaned back phone`() {
        // Телефон отклонён на 30°, но человек держит голову ровно и смотрит вверх на 30° —
        // шея не согнута.
        assertEquals(0f, PostureMath.neckFlexionDeg(deviceTiltDeg = 30f, headEulerXDeg = 30f), 0.001f)
    }
}
