package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Test

class PostureMathTest {

    private val g = 9.81f

    @Test
    fun `upright phone has zero tilt`() {
        // Экран вертикален: гравитация направлена вдоль корпуса вниз, то есть по −Y.
        assertEquals(0f, PostureMath.deviceTiltFromVerticalDeg(gravityY = -g, gravityZ = 0f), 0.5f)
    }

    @Test
    fun `phone lying face up is tilted ninety degrees`() {
        // Лежит на столе экраном вверх: гравитация уходит «в спину» устройства, то есть по −Z.
        assertEquals(90f, PostureMath.deviceTiltFromVerticalDeg(gravityY = 0f, gravityZ = -g), 0.5f)
    }

    @Test
    fun `phone leaned back forty five degrees`() {
        val c = g * 0.7071f
        assertEquals(45f, PostureMath.deviceTiltFromVerticalDeg(gravityY = -c, gravityZ = -c), 0.5f)
    }

    @Test
    fun `phone leaned toward the user gives negative tilt`() {
        val c = g * 0.7071f
        assertEquals(-45f, PostureMath.deviceTiltFromVerticalDeg(gravityY = -c, gravityZ = c), 0.5f)
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
