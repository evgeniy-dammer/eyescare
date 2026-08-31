package com.eyescare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Общий механизм проверяется в обе стороны сравнения: «условие снизу» (как у освещённости — плохо,
 * когда мало) и «условие сверху» (как у наклона головы — плохо, когда много).
 */
class SustainedThresholdTest {

    private fun lower() = SustainedThreshold(enterAt = 10f, exitAt = 30f, dwellMs = 1000, cooldownMs = 10_000)
    private fun higher() = SustainedThreshold(enterAt = 30f, exitAt = 20f, dwellMs = 1000, cooldownMs = 10_000)

    @Test
    fun `lower direction fires after dwell`() {
        val t = lower()
        assertFalse(t.update(0, 5f))
        assertFalse(t.update(500, 5f))
        assertTrue(t.update(1000, 5f))
    }

    @Test
    fun `higher direction fires after dwell`() {
        val t = higher()
        assertFalse(t.update(0, 45f))
        assertFalse(t.update(500, 45f))
        assertTrue(t.update(1000, 45f))
    }

    @Test
    fun `higher direction stays quiet below the threshold`() {
        val t = higher()
        assertFalse(t.update(0, 10f))
        assertFalse(t.update(5000, 10f))
        assertFalse(t.update(60_000, 10f))
    }

    @Test
    fun `hysteresis band holds the active state`() {
        val t = higher()
        assertFalse(t.update(0, 45f))      // условие включилось
        assertFalse(t.update(500, 25f))    // 25 — между 20 и 30: держим «активно»
        assertTrue(t.update(1000, 25f))
    }

    @Test
    fun `hysteresis band holds the inactive state`() {
        val t = higher()
        assertFalse(t.update(0, 5f))       // условие выключено
        assertFalse(t.update(1000, 25f))   // тот же уровень при движении снизу — всё ещё выключено
        assertFalse(t.update(5000, 25f))
    }

    @Test
    fun `leaving the condition restarts the dwell`() {
        val t = higher()
        assertFalse(t.update(0, 45f))
        assertFalse(t.update(800, 5f))     // выпрямился, не дождавшись выдержки
        assertFalse(t.update(900, 45f))    // снова наклонился — отсчёт с нуля
        assertFalse(t.update(1500, 45f))
        assertTrue(t.update(1900, 45f))
    }

    @Test
    fun `cooldown suppresses repeats`() {
        val t = higher()
        assertFalse(t.update(0, 45f))
        assertTrue(t.update(1000, 45f))
        assertFalse(t.update(3000, 45f))
        assertFalse(t.update(9000, 45f))
        assertTrue(t.update(11_000, 45f))
    }

    @Test
    fun `reset restarts dwell but keeps cooldown`() {
        val t = higher()
        assertFalse(t.update(0, 45f))
        assertTrue(t.update(1000, 45f))
        t.reset()
        assertFalse(t.update(2000, 45f))
        assertFalse(t.update(5000, 45f))   // выдержка прошла, но пауза с 1000 — нет
        assertTrue(t.update(11_000, 45f))
    }

    @Test
    fun `isActive reflects the current condition`() {
        val t = higher()
        assertFalse(t.isActive)
        t.update(0, 45f)
        assertTrue(t.isActive)
        t.update(100, 5f)
        assertFalse(t.isActive)
    }
}
