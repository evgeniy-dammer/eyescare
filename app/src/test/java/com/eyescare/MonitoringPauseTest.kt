package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringPauseTest {

    private val tenMin = 10 * 60_000L

    @Test
    fun `starts running`() {
        val p = MonitoringPause()
        assertFalse(p.isPaused)
        assertFalse(p.isSnoozed)
        assertNull(p.snoozeUntilMs())
    }

    @Test
    fun `first pause reports that the camera must be released`() {
        val p = MonitoringPause()
        assertTrue(p.pause(PauseReason.SCREEN_OFF))
        assertTrue(p.isPaused)
    }

    @Test
    fun `repeated pause for the same reason does not report again`() {
        val p = MonitoringPause()
        assertTrue(p.pause(PauseReason.SCREEN_OFF))
        assertFalse(p.pause(PauseReason.SCREEN_OFF))
    }

    @Test
    fun `second reason does not report a fresh pause`() {
        val p = MonitoringPause()
        assertTrue(p.snooze(0, tenMin))
        assertFalse(p.pause(PauseReason.SCREEN_OFF)) // уже стоим — камера и так отпущена
    }

    @Test
    fun `camera comes back only when the last reason is gone`() {
        val p = MonitoringPause()
        p.snooze(0, tenMin)
        p.pause(PauseReason.SCREEN_OFF)

        assertFalse(p.resume(PauseReason.SCREEN_OFF)) // снуз ещё держит
        assertTrue(p.isPaused)
        assertTrue(p.resume(PauseReason.SNOOZE))      // причин не осталось
        assertFalse(p.isPaused)
    }

    @Test
    fun `resuming a reason that is not set changes nothing`() {
        val p = MonitoringPause()
        p.pause(PauseReason.SCREEN_OFF)
        assertFalse(p.resume(PauseReason.SNOOZE))
        assertTrue(p.isPaused)
    }

    @Test
    fun `snooze remaining counts down and never goes negative`() {
        val p = MonitoringPause()
        p.snooze(1000, tenMin)
        assertEquals(tenMin, p.snoozeRemainingMs(1000))
        assertEquals(tenMin - 5000, p.snoozeRemainingMs(6000))
        assertEquals(0L, p.snoozeRemainingMs(1000 + tenMin + 5000))
    }

    @Test
    fun `snooze called again extends the deadline`() {
        val p = MonitoringPause()
        p.snooze(0, tenMin)
        assertFalse(p.snooze(5000, tenMin)) // уже на паузе — второй раз камеру отпускать не надо
        assertEquals(5000 + tenMin, p.snoozeUntilMs())
    }

    @Test
    fun `snooze expires and resumes monitoring`() {
        val p = MonitoringPause()
        p.snooze(0, tenMin)
        assertFalse(p.expireSnoozeIfDue(tenMin - 1)) // ещё рано
        assertTrue(p.isSnoozed)
        assertTrue(p.expireSnoozeIfDue(tenMin))
        assertFalse(p.isSnoozed)
        assertFalse(p.isPaused)
    }

    @Test
    fun `snooze expiring with the screen off does not resume the camera`() {
        val p = MonitoringPause()
        p.snooze(0, tenMin)
        p.pause(PauseReason.SCREEN_OFF)

        assertFalse(p.expireSnoozeIfDue(tenMin)) // снуз снят, но экран выключен — камеру не поднимаем
        assertFalse(p.isSnoozed)
        assertTrue(p.isPaused)

        assertTrue(p.resume(PauseReason.SCREEN_OFF)) // экран зажёгся — вот теперь поднимаем
        assertFalse(p.isPaused)
    }

    @Test
    fun `expire does nothing without a snooze`() {
        val p = MonitoringPause()
        assertFalse(p.expireSnoozeIfDue(999_999))
    }

    @Test
    fun `schedule stacks with the other reasons`() {
        val p = MonitoringPause()
        assertTrue(p.pause(PauseReason.SCHEDULE))   // вышли из окна — камеру отпустить
        assertTrue(p.isPausedBySchedule)

        assertFalse(p.snooze(0, tenMin))            // уже стоим, второй раз отпускать нечего
        assertFalse(p.resume(PauseReason.SCHEDULE)) // окно открылось, но снуз ещё держит
        assertFalse(p.isPausedBySchedule)
        assertTrue(p.isPaused)

        assertTrue(p.resume(PauseReason.SNOOZE))    // причин не осталось
        assertFalse(p.isPaused)
    }

    @Test
    fun `manual resume clears the snooze deadline`() {
        val p = MonitoringPause()
        p.snooze(0, tenMin)
        assertTrue(p.resume(PauseReason.SNOOZE))
        assertNull(p.snoozeUntilMs())
        assertNull(p.snoozeRemainingMs(0))
    }
}
