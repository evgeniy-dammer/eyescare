package com.eyecare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DistanceCalculatorTest {

    @Mock
    private lateinit var mockSettings: SettingsRepository

    private lateinit var calculator: DistanceCalculator

    @Before
    fun setUp() {
        calculator = DistanceCalculator(mockSettings)
    }

    @Test
    fun `calculate distance returns correct value for ideal conditions`() {
        // --- Arrange ---
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(4.74f)
        whenever(mockSettings.getHardwareSensorWidth()).thenReturn(4.8f)
        whenever(mockSettings.getIpdMm()).thenReturn(63f)

        val headEulerAngleX = 0f
        val headEulerAngleY = 0f
        val imageWidth = 1280
        val pixelIPD = 150.0

        val focalPixels = (4.74f / 4.8f) * imageWidth
        val expectedDistance = (focalPixels * 63f) / pixelIPD / 10.0

        // --- Act ---
        val initialDistance = calculator.calculate(pixelIPD, headEulerAngleX, headEulerAngleY, imageWidth)
        assertNotNull("First calculation should not be null", initialDistance)

        val finalDistance = calculator.calculate(pixelIPD, headEulerAngleX, headEulerAngleY, imageWidth)

        // --- Assert ---
        assertNotNull("Final distance should not be null", finalDistance)
        assertEquals(expectedDistance.toFloat(), finalDistance!!, 0.1f)
    }
    
    @Test
    fun `calculate distance is adjusted for head tilt`() {
        // --- Arrange ---
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(4.74f)
        whenever(mockSettings.getHardwareSensorWidth()).thenReturn(4.8f)
        whenever(mockSettings.getIpdMm()).thenReturn(63f)

        val imageWidth = 1280
        val pixelIPD = 150.0
        
        // --- Act ---
        // Расчет без наклона
        val distanceNoTilt = calculator.calculate(pixelIPD, 0f, 0f, imageWidth)
        assertNotNull(distanceNoTilt)

        // Расчет с наклоном 30 градусов по вертикали
        val distanceWithTilt = calculator.calculate(pixelIPD, 30f, 0f, imageWidth)
        assertNotNull(distanceWithTilt)

        // --- Assert ---
        // Расстояние при наклоне должно быть больше, т.к. проекция pixelIPD уменьшается
        assertNotEquals(distanceNoTilt, distanceWithTilt)
        assert(distanceWithTilt!! > distanceNoTilt!!)
    }

    @Test
    fun `calculate distance returns null when pixelIPD is zero`() {
        // --- Act ---
        val distance = calculator.calculate(0.0, 0f, 0f, 1280)

        // --- Assert ---
        assertEquals(null, distance)
    }

    @Test
    fun `calibrate sets correct IPD based on known distance`() {
        // --- Arrange ---
        val knownDistanceCm = 30f
        val imageWidth = 1920
        val pixelIPD = 200.0
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(4.74f)
        whenever(mockSettings.getHardwareSensorWidth()).thenReturn(4.8f)

        val focalPixels = (4.74f / 4.8f) * imageWidth
        val expectedIpd = (knownDistanceCm * 10 * pixelIPD) / focalPixels

        // --- Act ---
        val ok = calculator.calibrate(pixelIPD, imageWidth, knownDistanceCm)

        // --- Assert ---
        assertTrue("calibrate should report success when camera data is present", ok)
        verify(mockSettings).setCalibratedIpd(expectedIpd.toFloat())
    }

    @Test
    fun `calculate returns null when camera hardware data is missing`() {
        // Нет аппаратных параметров камеры → focalPixels == 0 → делить не на что.
        // (getFocalLengthInPixels уходит в короткое замыкание на нулевом фокусе.)
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(0f)

        val distance = calculator.calculate(pixelIPD = 150.0, headEulerAngleX = 0f, headEulerAngleY = 0f, imageWidth = 1280)

        assertEquals(null, distance)
    }

    @Test
    fun `calibrate returns false and does not save when camera hardware data is missing`() {
        // Регрессия бага: без аппаратных данных авто-калибровка не должна «тихо» завершаться успехом.
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(0f)

        val ok = calculator.calibrate(pixelIPD = 200.0, imageWidth = 1920, realDistanceCm = 30f)

        assertFalse("calibrate must report failure without camera data", ok)
        verify(mockSettings, never()).setCalibratedIpd(any())
    }
}