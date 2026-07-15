package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [MonitoringStateHolder] — процесс-синглтон, поэтому сбрасываем состояние перед каждым тестом.
 * Проверяем, что независимые обновления полей не затирают друг друга (`copy`) и что `reset`
 * возвращает всё к значениям по умолчанию — на это состояние опирается синхронизация тумблера
 * мониторинга в UI.
 */
class MonitoringStateHolderTest {

    @Before
    fun setUp() {
        MonitoringStateHolder.reset()
    }

    @Test
    fun `default state is idle`() {
        val s = MonitoringStateHolder.state.value
        assertFalse(s.running)
        assertNull(s.distanceCm)
        assertFalse(s.tooClose)
    }

    @Test
    fun `field updates are independent`() {
        MonitoringStateHolder.setRunning(true)
        MonitoringStateHolder.setDistance(37.8f)
        MonitoringStateHolder.setTooClose(true)

        val s = MonitoringStateHolder.state.value
        assertTrue(s.running)
        assertEquals(37.8f, s.distanceCm)
        assertTrue(s.tooClose)

        // Обновление одного поля не сбрасывает остальные.
        MonitoringStateHolder.setDistance(null)
        val s2 = MonitoringStateHolder.state.value
        assertTrue(s2.running)
        assertNull(s2.distanceCm)
        assertTrue(s2.tooClose)
    }

    @Test
    fun `reset returns to defaults`() {
        MonitoringStateHolder.setRunning(true)
        MonitoringStateHolder.setDistance(20f)
        MonitoringStateHolder.setTooClose(true)

        MonitoringStateHolder.reset()

        val s = MonitoringStateHolder.state.value
        assertFalse(s.running)
        assertNull(s.distanceCm)
        assertFalse(s.tooClose)
    }
}
