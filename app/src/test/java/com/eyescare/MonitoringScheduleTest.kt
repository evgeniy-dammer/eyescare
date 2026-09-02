package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringScheduleTest {

    private val MON = 1
    private val TUE = 2
    private val FRI = 5
    private val SAT = 6
    private val SUN = 7

    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `disabled schedule allows monitoring at any time`() {
        val s = MonitoringSchedule(enabled = false, days = setOf(MON), startMinuteOfDay = h(9), endMinuteOfDay = h(18))
        assertTrue(s.isMonitoringAllowedAt(SAT, h(3)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(23)))
    }

    @Test
    fun `daytime window allows only inside it`() {
        val s = MonitoringSchedule(enabled = true, days = setOf(MON), startMinuteOfDay = h(9), endMinuteOfDay = h(18))
        assertFalse(s.isMonitoringAllowedAt(MON, h(8, 59)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(9)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(17, 59)))
        assertFalse(s.isMonitoringAllowedAt(MON, h(18))) // конец окна не включаем
    }

    @Test
    fun `unchecked day is never allowed`() {
        val s = MonitoringSchedule(enabled = true, days = setOf(MON), startMinuteOfDay = h(9), endMinuteOfDay = h(18))
        assertFalse(s.isMonitoringAllowedAt(TUE, h(12)))
    }

    @Test
    fun `no days selected means never`() {
        val s = MonitoringSchedule(enabled = true, days = emptySet(), startMinuteOfDay = h(9), endMinuteOfDay = h(18))
        assertFalse(s.isMonitoringAllowedAt(MON, h(12)))
    }

    @Test
    fun `equal bounds mean the whole day on selected days`() {
        val s = MonitoringSchedule(enabled = true, days = setOf(MON), startMinuteOfDay = h(9), endMinuteOfDay = h(9))
        assertTrue(s.isMonitoringAllowedAt(MON, h(0)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(23, 59)))
        assertFalse(s.isMonitoringAllowedAt(TUE, h(12)))
    }

    @Test
    fun `overnight window covers the evening of the selected day`() {
        // 22:00–02:00, отмечен только понедельник.
        val s = MonitoringSchedule(enabled = true, days = setOf(MON), startMinuteOfDay = h(22), endMinuteOfDay = h(2))
        assertFalse(s.isMonitoringAllowedAt(MON, h(21, 59)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(22)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(23, 59)))
    }

    @Test
    fun `overnight window carries into the next morning`() {
        val s = MonitoringSchedule(enabled = true, days = setOf(MON), startMinuteOfDay = h(22), endMinuteOfDay = h(2))
        // Ночь с понедельника на вторник принадлежит понедельнику.
        assertTrue(s.isMonitoringAllowedAt(TUE, h(0, 30)))
        assertTrue(s.isMonitoringAllowedAt(TUE, h(1, 59)))
        assertFalse(s.isMonitoringAllowedAt(TUE, h(2)))
        // А вечер вторника — уже нет: вторник не отмечен.
        assertFalse(s.isMonitoringAllowedAt(TUE, h(23)))
    }

    @Test
    fun `overnight window wraps around the week boundary`() {
        // Отмечено воскресенье, окно 22:00–02:00: ночь воскресенья переходит в понедельник.
        val s = MonitoringSchedule(enabled = true, days = setOf(SUN), startMinuteOfDay = h(22), endMinuteOfDay = h(2))
        assertTrue(s.isMonitoringAllowedAt(SUN, h(23)))
        assertTrue(s.isMonitoringAllowedAt(MON, h(1)))
        assertFalse(s.isMonitoringAllowedAt(MON, h(3)))
    }

    @Test
    fun `weekdays preset excludes the weekend`() {
        val s = MonitoringSchedule(enabled = true, days = MonitoringSchedule.DEFAULT_DAYS)
        assertTrue(s.isMonitoringAllowedAt(FRI, h(12)))
        assertFalse(s.isMonitoringAllowedAt(SAT, h(12)))
        assertFalse(s.isMonitoringAllowedAt(SUN, h(12)))
    }

    @Test
    fun `previous day wraps from monday to sunday`() {
        assertEquals(7, MonitoringSchedule.previousDay(1))
        assertEquals(1, MonitoringSchedule.previousDay(2))
        assertEquals(6, MonitoringSchedule.previousDay(7))
    }
}
