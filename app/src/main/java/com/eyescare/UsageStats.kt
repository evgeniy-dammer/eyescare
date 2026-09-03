package com.eyescare

/**
 * Сводка использования за скользящее окно (последние [StatsHistory.WEEK_DAYS] дней).
 *
 * Раньше это были счётчики календарной недели, обнулявшиеся в понедельник. Теперь сводка считается
 * из [StatsHistory] — от «сколько всего» ничего не изменилось, но данные перестали исчезать: тот же
 * ряд по дням питает график и серии «хороших» дней.
 */
data class WeeklyStats(
    val monitoringSeconds: Long = 0,
    val tooCloseSeconds: Long = 0,
    val tooCloseEvents: Int = 0,
)
