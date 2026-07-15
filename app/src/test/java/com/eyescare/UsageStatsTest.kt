package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UsageStatsTest {

    // epoch day 0 = 1970-01-01 (четверг); неделя выровнена на понедельник.
    @Test
    fun `days within the same week share a week id`() {
        // Пн 1969-12-29 (-3) … Вс 1970-01-04 (3) — одна неделя.
        assertEquals(weekIdForEpochDay(-3), weekIdForEpochDay(0))
        assertEquals(weekIdForEpochDay(0), weekIdForEpochDay(3))
    }

    @Test
    fun `monday starts a new week`() {
        // Вс (3) и следующий Пн (4) должны быть в разных неделях.
        assertNotEquals(weekIdForEpochDay(3), weekIdForEpochDay(4))
        assertEquals(weekIdForEpochDay(3) + 1, weekIdForEpochDay(4))
    }

    @Test
    fun `consecutive mondays differ by one`() {
        assertEquals(weekIdForEpochDay(4) + 1, weekIdForEpochDay(11)) // 1970-01-05 → 1970-01-12
    }
}
