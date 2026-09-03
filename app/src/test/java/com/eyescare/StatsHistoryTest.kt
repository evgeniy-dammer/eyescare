package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsHistoryTest {

    private val today = 20_000L // произвольный epoch-day: класс не знает про календарь

    private fun day(
        epochDay: Long,
        monitoring: Long = 0,
        tooClose: Long = 0,
        events: Int = 0,
        distanceSum: Long = 0,
        samples: Long = 0,
    ) = DailyStats(epochDay, monitoring, tooClose, events, distanceSum, samples)

    @Test
    fun `empty history has no days`() {
        assertTrue(StatsHistory.EMPTY.days.isEmpty())
        assertNull(StatsHistory.EMPTY.forDay(today))
    }

    @Test
    fun `adding to a new day creates the record`() {
        val h = StatsHistory.EMPTY.plus(day(today, monitoring = 60), today)
        assertEquals(60L, h.forDay(today)?.monitoringSeconds)
    }

    @Test
    fun `adding to an existing day sums the fields`() {
        val h = StatsHistory.EMPTY
            .plus(day(today, monitoring = 60, tooClose = 10, events = 1, distanceSum = 300, samples = 10), today)
            .plus(day(today, monitoring = 30, tooClose = 5, events = 2, distanceSum = 100, samples = 5), today)

        val d = h.forDay(today)!!
        assertEquals(90L, d.monitoringSeconds)
        assertEquals(15L, d.tooCloseSeconds)
        assertEquals(3, d.tooCloseEvents)
        assertEquals(400L, d.distanceSumCm)
        assertEquals(15L, d.distanceSamples)
    }

    @Test
    fun `average distance comes from the sum and the sample count`() {
        val d = day(today, distanceSum = 450, samples = 10)
        assertEquals(45f, d.averageDistanceCm!!, 0.001f)
        assertNull(day(today).averageDistanceCm) // замеров не было
    }

    @Test
    fun `average survives being written in several chunks`() {
        // Смысл хранения суммой: дописывание не теряет вес прошлых замеров.
        val h = StatsHistory.EMPTY
            .plus(day(today, distanceSum = 300, samples = 10), today) // среднее 30
            .plus(day(today, distanceSum = 500, samples = 10), today) // среднее 50
        assertEquals(40f, h.forDay(today)!!.averageDistanceCm!!, 0.001f)
    }

    @Test
    fun `too close share is a fraction of monitoring time`() {
        assertEquals(0.25f, day(today, monitoring = 400, tooClose = 100).tooCloseShare!!, 0.001f)
        assertNull(day(today).tooCloseShare) // не мониторили — доли нет
    }

    @Test
    fun `old days are dropped on write`() {
        var h = StatsHistory.EMPTY
        for (i in 0 until 40) h = h.plus(day(today - i, monitoring = 60), today)

        assertEquals(StatsHistory.KEEP_DAYS, h.days.size)
        assertNull(h.forDay(today - StatsHistory.KEEP_DAYS))
        assertEquals(60L, h.forDay(today - StatsHistory.KEEP_DAYS + 1)?.monitoringSeconds)
    }

    @Test
    fun `days from the future are kept`() {
        // Переведённые назад часы не должны стирать уже записанное.
        val h = StatsHistory.EMPTY.plus(day(today + 5, monitoring = 60), today)
        assertEquals(60L, h.forDay(today + 5)?.monitoringSeconds)
    }

    @Test
    fun `last days pads the gaps and keeps the order`() {
        val h = StatsHistory.EMPTY
            .plus(day(today, monitoring = 60), today)
            .plus(day(today - 3, monitoring = 30), today)

        val window = h.lastDays(today, 7)
        assertEquals(7, window.size)
        assertEquals((today - 6..today).toList(), window.map { it.epochDay })
        assertEquals(30L, window[3].monitoringSeconds)
        assertEquals(60L, window[6].monitoringSeconds)
        assertFalse(window[0].hasData) // пропуск, а не ноль
    }

    @Test
    fun `last days of a non-positive count is empty`() {
        assertTrue(StatsHistory.EMPTY.lastDays(today, 0).isEmpty())
    }

    @Test
    fun `totals sum only the window`() {
        val h = StatsHistory.EMPTY
            .plus(day(today, monitoring = 100, tooClose = 10, events = 1), today)
            .plus(day(today - 6, monitoring = 200, tooClose = 20, events = 2), today)
            .plus(day(today - 7, monitoring = 999, tooClose = 99, events = 9), today) // вне окна

        val totals = h.totalsForLastDays(today, StatsHistory.WEEK_DAYS)
        assertEquals(300L, totals.monitoringSeconds)
        assertEquals(30L, totals.tooCloseSeconds)
        assertEquals(3, totals.tooCloseEvents)
    }

    @Test
    fun `serialize and parse round trip`() {
        val h = StatsHistory.EMPTY
            .plus(day(today - 1, monitoring = 120, tooClose = 12, events = 2, distanceSum = 600, samples = 20), today)
            .plus(day(today, monitoring = 60, tooClose = 6, events = 1, distanceSum = 300, samples = 10), today)

        assertEquals(h.days, StatsHistory.parse(h.serialize()).days)
    }

    @Test
    fun `parse tolerates empty and broken input`() {
        assertTrue(StatsHistory.parse(null).days.isEmpty())
        assertTrue(StatsHistory.parse("").days.isEmpty())
        assertTrue(StatsHistory.parse("garbage").days.isEmpty())
        assertTrue(StatsHistory.parse("1:2:3").days.isEmpty()) // не хватает полей

        // Испорченная запись выбрасывается, соседняя целая — остаётся.
        val mixed = StatsHistory.parse("100:60:6:1:300:10;oops;101:x:0:0:0:0")
        assertEquals(1, mixed.days.size)
        assertEquals(100L, mixed.days.first().epochDay)
    }

    @Test
    fun `parse merges duplicate days instead of losing them`() {
        val merged = StatsHistory.parse("100:60:0:0:0:0;100:30:0:0:0:0")
        assertEquals(1, merged.days.size)
        assertEquals(90L, merged.forDay(100)?.monitoringSeconds)
    }

    @Test
    fun `streak counts consecutive good days`() {
        var h = StatsHistory.EMPTY
        for (i in 0 until 3) h = h.plus(day(today - i, monitoring = 1000, tooClose = 10), today)
        assertEquals(3, h.goodDayStreak(today, StatsHistory.GOOD_DAY_MAX_TOO_CLOSE_SHARE))
    }

    @Test
    fun `streak breaks on a bad day`() {
        val h = StatsHistory.EMPTY
            .plus(day(today, monitoring = 1000, tooClose = 10), today)      // хороший
            .plus(day(today - 1, monitoring = 1000, tooClose = 500), today) // плохой
            .plus(day(today - 2, monitoring = 1000, tooClose = 10), today)  // до него не доходим
        assertEquals(1, h.goodDayStreak(today, StatsHistory.GOOD_DAY_MAX_TOO_CLOSE_SHARE))
    }

    @Test
    fun `empty today does not break the streak`() {
        // Утром сегодняшних данных ещё нет — вчерашняя серия обязана сохраниться.
        val h = StatsHistory.EMPTY
            .plus(day(today - 1, monitoring = 1000, tooClose = 10), today)
            .plus(day(today - 2, monitoring = 1000, tooClose = 10), today)
        assertEquals(2, h.goodDayStreak(today, StatsHistory.GOOD_DAY_MAX_TOO_CLOSE_SHARE))
    }

    @Test
    fun `a gap in the middle ends the streak`() {
        val h = StatsHistory.EMPTY
            .plus(day(today, monitoring = 1000, tooClose = 10), today)
            .plus(day(today - 2, monitoring = 1000, tooClose = 10), today) // вчера пропущено
        assertEquals(1, h.goodDayStreak(today, StatsHistory.GOOD_DAY_MAX_TOO_CLOSE_SHARE))
    }

    @Test
    fun `streak is zero without any data`() {
        assertEquals(0, StatsHistory.EMPTY.goodDayStreak(today, StatsHistory.GOOD_DAY_MAX_TOO_CLOSE_SHARE))
    }
}
