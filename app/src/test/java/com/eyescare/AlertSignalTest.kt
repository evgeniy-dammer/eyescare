package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSignalTest {

    @Test
    fun `silent does nothing beyond the banner`() {
        assertFalse(AlertSignal.SILENT.vibrates)
        assertFalse(AlertSignal.SILENT.plays)
    }

    @Test
    fun `each signal enables exactly what it promises`() {
        assertTrue(AlertSignal.VIBRATION.vibrates)
        assertFalse(AlertSignal.VIBRATION.plays)

        assertTrue(AlertSignal.SOUND.plays)
        assertFalse(AlertSignal.SOUND.vibrates)

        assertTrue(AlertSignal.BOTH.vibrates)
        assertTrue(AlertSignal.BOTH.plays)
    }

    @Test
    fun `default keeps the behaviour the app had before the setting`() {
        assertEquals(AlertSignal.VIBRATION, AlertSignal.DEFAULT)
    }

    @Test
    fun `stored names round-trip`() {
        AlertSignal.entries.forEach { assertEquals(it, AlertSignal.fromName(it.name)) }
    }

    @Test
    fun `unknown or missing value falls back to the default`() {
        // Испорченное хранилище или откат на старую версию не должны ронять старт.
        assertEquals(AlertSignal.DEFAULT, AlertSignal.fromName(null))
        assertEquals(AlertSignal.DEFAULT, AlertSignal.fromName(""))
        assertEquals(AlertSignal.DEFAULT, AlertSignal.fromName("HAPTIC_PULSE"))
        assertEquals(AlertSignal.DEFAULT, AlertSignal.fromName("vibration")) // регистр значим
    }
}
