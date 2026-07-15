package com.eyescare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakReminderTest {

    @Test
    fun `break becomes due after continuous work`() {
        val br = BreakReminder(workMs = 1000, breakResetMs = 300)
        assertFalse(br.update(0, true))       // первый замер — только фиксируем лицо
        assertFalse(br.update(500, true))     // +500 = 500
        assertTrue(br.update(1000, true))     // +500 = 1000 → перерыв
    }

    @Test
    fun `no break before work threshold`() {
        val br = BreakReminder(workMs = 1000, breakResetMs = 300)
        assertFalse(br.update(0, true))
        assertFalse(br.update(300, true))
        assertFalse(br.update(600, true))     // всего 600 < 1000
    }

    @Test
    fun `looking away long enough resets accumulation`() {
        val br = BreakReminder(workMs = 1000, breakResetMs = 300)
        br.update(0, true)
        br.update(500, true)                  // накоплено 500
        br.update(600, false)                 // лицо пропало
        br.update(950, false)                 // 350 мс без лица (>=300) → сброс
        br.update(1000, true)
        assertFalse(br.update(1400, true))    // после сброса всего 400 < 1000
    }

    @Test
    fun `brief glance away does not reset accumulation`() {
        val br = BreakReminder(workMs = 1000, breakResetMs = 300)
        br.update(0, true)
        br.update(500, true)                  // 500
        br.update(600, false)                 // короткое отсутствие (<300)
        br.update(700, true)                  // лицо вернулось
        assertTrue(br.update(1200, true))     // +500 = 1000 → перерыв всё же наступает
    }

    @Test
    fun `reset clears state`() {
        val br = BreakReminder(workMs = 1000, breakResetMs = 300)
        br.update(0, true)
        br.update(900, true)                  // 900
        br.reset()
        br.update(1000, true)
        assertFalse(br.update(1900, true))    // после reset всего 900 < 1000
    }
}
