package com.eyecare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class IrisDistanceCalculatorTest {

    @Mock
    private lateinit var mockSettings: SettingsRepository

    private lateinit var calculator: IrisDistanceCalculator

    @Before
    fun setUp() {
        calculator = IrisDistanceCalculator(mockSettings)
    }

    @Test
    fun `calculate returns correct value for ideal conditions`() {
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(4.74f)
        whenever(mockSettings.getHardwareSensorWidth()).thenReturn(4.8f)

        val imageWidth = 1280
        val irisPx = 28.0
        val focalPixels = (4.74f / 4.8f) * imageWidth
        val expected = (focalPixels * IrisDistanceCalculator.IRIS_DIAMETER_MM) / irisPx / 10.0

        // Первый замер инициализирует EMA, второй с тем же входом сходится к нему.
        assertNotNull(calculator.calculate(irisPx, 0f, 0f, imageWidth))
        val distance = calculator.calculate(irisPx, 0f, 0f, imageWidth)

        assertNotNull(distance)
        assertEquals(expected.toFloat(), distance!!, 0.1f)
    }

    @Test
    fun `calculate is larger with head tilt (foreshortened iris)`() {
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(4.74f)
        whenever(mockSettings.getHardwareSensorWidth()).thenReturn(4.8f)

        val imageWidth = 1280
        val irisPx = 28.0

        val noTilt = calculator.calculate(irisPx, 0f, 0f, imageWidth)
        val withTilt = calculator.calculate(irisPx, 30f, 0f, imageWidth)

        assertNotNull(noTilt)
        assertNotNull(withTilt)
        assert(withTilt!! > noTilt!!)
    }

    @Test
    fun `calculate returns null when iris diameter is zero`() {
        assertNull(calculator.calculate(0.0, 0f, 0f, 1280))
    }

    @Test
    fun `calculate returns null when camera hardware data is missing`() {
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(0f)
        assertNull(calculator.calculate(28.0, 0f, 0f, 1280))
    }

    @Test
    fun `reset clears EMA so a fresh value is not blended with a stale one`() {
        whenever(mockSettings.getHardwareFocalLength()).thenReturn(4.74f)
        whenever(mockSettings.getHardwareSensorWidth()).thenReturn(4.8f)

        val imageWidth = 1280
        // Заполняем EMA близким значением, затем сбрасываем.
        calculator.calculate(28.0, 0f, 0f, imageWidth)
        calculator.reset()

        // После reset первый замер = сырое значение без подмешивания старого.
        val focalPixels = (4.74f / 4.8f) * imageWidth
        val irisPx = 56.0
        val expected = (focalPixels * IrisDistanceCalculator.IRIS_DIAMETER_MM) / irisPx / 10.0
        val distance = calculator.calculate(irisPx, 0f, 0f, imageWidth)

        assertNotNull(distance)
        assertEquals(expected.toFloat(), distance!!, 0.1f)
    }
}
