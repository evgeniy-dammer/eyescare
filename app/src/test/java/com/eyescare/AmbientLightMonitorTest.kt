package com.eyescare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientLightMonitorTest {

    /** Тестовый монитор с короткими интервалами: темно <10, светло >30, выдержка 1 с, пауза 10 с. */
    private fun monitor() = AmbientLightMonitor(
        darkLux = 10f,
        brightLux = 30f,
        dwellMs = 1000,
        cooldownMs = 10_000,
    )

    @Test
    fun `warns after continuous darkness`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))      // стало темно — начали отсчёт выдержки
        assertFalse(m.update(500, 5f))    // выдержка ещё не прошла
        assertTrue(m.update(1000, 5f))    // темно ровно 1000 мс → предупреждение
    }

    @Test
    fun `no warning while it is bright`() {
        val m = monitor()
        assertFalse(m.update(0, 200f))
        assertFalse(m.update(5000, 200f))
        assertFalse(m.update(60_000, 200f))
    }

    @Test
    fun `darkness timer restarts when light comes back`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))       // темно
        assertFalse(m.update(800, 100f))   // включили свет, не дождавшись выдержки
        assertFalse(m.update(900, 5f))     // снова темно — отсчёт с нуля
        assertFalse(m.update(1500, 5f))    // от 900 прошло 600 < 1000
        assertTrue(m.update(1900, 5f))     // от 900 прошло 1000 → предупреждение
    }

    @Test
    fun `hysteresis keeps state between thresholds`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))       // темно
        // 20 лк — между darkLux и brightLux: состояние «темно» сохраняется, отсчёт не сбрасывается.
        assertFalse(m.update(500, 20f))
        assertTrue(m.update(1000, 20f))
    }

    @Test
    fun `hysteresis does not turn bright room dark`() {
        val m = monitor()
        assertFalse(m.update(0, 200f))     // светло
        // Тот же промежуточный уровень при движении сверху означает «всё ещё светло».
        assertFalse(m.update(1000, 20f))
        assertFalse(m.update(5000, 20f))
    }

    @Test
    fun `no repeated warning during cooldown`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))
        assertTrue(m.update(1000, 5f))     // первое предупреждение
        assertFalse(m.update(3000, 5f))    // темно по-прежнему, но пауза не вышла
        assertFalse(m.update(9000, 5f))
    }

    @Test
    fun `warns again after cooldown`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))
        assertTrue(m.update(1000, 5f))     // первое предупреждение в 1000
        assertFalse(m.update(9000, 5f))    // пауза ещё идёт
        assertTrue(m.update(11_000, 5f))   // 10 000 мс после первого → можно снова
    }

    @Test
    fun `cooldown survives the light being switched on and off`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))
        assertTrue(m.update(1000, 5f))     // предупредили
        assertFalse(m.update(2000, 200f))  // включили свет
        assertFalse(m.update(3000, 5f))    // и снова выключили
        assertFalse(m.update(5000, 5f))    // выдержка прошла, но пауза — нет: молчим
        assertTrue(m.update(11_000, 5f))   // пауза вышла
    }

    @Test
    fun `reset restarts the dwell timer`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))       // темно, копим выдержку
        m.reset()                          // например, экран погас на середине выдержки
        assertFalse(m.update(1000, 5f))    // отсчёт начинается заново отсюда
        assertFalse(m.update(1500, 5f))    // от 1000 прошло 500 < 1000
        assertTrue(m.update(2000, 5f))     // от 1000 прошло 1000 → предупреждение
    }

    @Test
    fun `reset keeps the cooldown`() {
        val m = monitor()
        assertFalse(m.update(0, 5f))
        assertTrue(m.update(1000, 5f))     // предупредили
        m.reset()                          // экран погас и снова зажёгся
        assertFalse(m.update(2000, 5f))
        assertFalse(m.update(5000, 5f))    // выдержка прошла, но пауза с 1000 — нет: молчим
        assertTrue(m.update(11_000, 5f))   // пауза вышла
    }
}
