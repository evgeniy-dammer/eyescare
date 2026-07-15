package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHysteresisTest {

    private val threshold = 30

    @Test
    fun `engages only after three consecutive frames below threshold`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)

        // 2 кадра ниже порога — ещё рано
        assertNull(h.update(25f, threshold))
        assertNull(h.update(25f, threshold))
        assertFalse(h.isEngaged)

        // 3-й кадр ниже порога — включаем
        assertEquals(true, h.update(25f, threshold))
        assertTrue(h.isEngaged)
    }

    @Test
    fun `single frame above resets the engage counter`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)

        assertNull(h.update(25f, threshold))
        assertNull(h.update(25f, threshold))
        // Кадр выше порога сбрасывает счётчик
        assertNull(h.update(35f, threshold))
        // Снова нужно 3 подряд
        assertNull(h.update(25f, threshold))
        assertNull(h.update(25f, threshold))
        assertEquals(true, h.update(25f, threshold))
    }

    @Test
    fun `releases only after five consecutive frames above threshold`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)

        // Включаем оверлей
        repeat(2) { h.update(25f, threshold) }
        assertEquals(true, h.update(25f, threshold))
        assertTrue(h.isEngaged)

        // 4 кадра выше порога — ещё держим
        repeat(4) { assertNull(h.update(35f, threshold)) }
        assertTrue(h.isEngaged)

        // 5-й кадр выше — выключаем
        assertEquals(false, h.update(35f, threshold))
        assertFalse(h.isEngaged)
    }

    @Test
    fun `single frame below resets the release counter`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)

        repeat(2) { h.update(25f, threshold) }
        h.update(25f, threshold) // engaged

        repeat(4) { h.update(35f, threshold) }
        // Кадр ниже порога сбрасывает счётчик выключения
        assertNull(h.update(25f, threshold))
        assertTrue(h.isEngaged)

        // Снова нужно 5 подряд выше
        repeat(4) { assertNull(h.update(35f, threshold)) }
        assertEquals(false, h.update(35f, threshold))
    }

    @Test
    fun `value exactly at threshold counts as above`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)
        // distance == threshold не считается "слишком близко"
        repeat(5) { assertNull(h.update(30f, threshold)) }
        assertFalse(h.isEngaged)
    }

    @Test
    fun `face loss releases the overlay after framesToRelease frames`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)
        // Включаем оверлей
        repeat(2) { h.update(25f, threshold) }
        assertEquals(true, h.update(25f, threshold))
        assertTrue(h.isEngaged)

        // 4 кадра без лица — ещё держим
        repeat(4) { assertNull(h.onFaceLost()) }
        assertTrue(h.isEngaged)
        // 5-й кадр без лица — снимаем баннер
        assertEquals(false, h.onFaceLost())
        assertFalse(h.isEngaged)
    }

    @Test
    fun `face loss does nothing when overlay is not engaged`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)
        repeat(10) { assertNull(h.onFaceLost()) }
        assertFalse(h.isEngaged)
    }

    @Test
    fun `face returning before release keeps the overlay and resets loss counter`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)
        repeat(2) { h.update(25f, threshold) }
        h.update(25f, threshold) // engaged
        assertTrue(h.isEngaged)

        // 4 кадра без лица, затем лицо вернулось (близко) — счётчик потери сбрасывается
        repeat(4) { assertNull(h.onFaceLost()) }
        assertNull(h.update(25f, threshold))
        assertTrue(h.isEngaged)

        // Снова нужно 5 кадров без лица подряд
        repeat(4) { assertNull(h.onFaceLost()) }
        assertEquals(false, h.onFaceLost())
    }

    @Test
    fun `reset clears state and counters`() {
        val h = OverlayHysteresis(framesToEngage = 3, framesToRelease = 5)
        repeat(2) { h.update(25f, threshold) }
        h.update(25f, threshold) // engaged
        assertTrue(h.isEngaged)

        h.reset()
        assertFalse(h.isEngaged)
        // После сброса снова нужно 3 кадра
        assertNull(h.update(25f, threshold))
        assertNull(h.update(25f, threshold))
        assertEquals(true, h.update(25f, threshold))
    }
}
