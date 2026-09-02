package com.eyescare

/**
 * Расписание мониторинга: в какие дни и часы контроль дистанции вообще должен работать.
 *
 * Зачем: постоянно включённый мониторинг мешает там, где он не нужен, — вечерний фильм, выходные,
 * нерабочие часы. Без расписания единственный выход — выключать приложение вручную, а выключенное
 * вручную приложение часто уже не включают обратно.
 *
 * Дни недели заданы как в `java.time.DayOfWeek.value`: 1 — понедельник, 7 — воскресенье.
 * Время — минуты от полуночи (0..1439).
 *
 * Окно может переходить через полночь (например, 22:00–02:00). В этом случае оно **принадлежит
 * дню, в который началось**: ночь с понедельника на вторник попадает в расписание, если отмечен
 * понедельник, а не вторник. Иначе «ночное» окно вело бы себя контринтуитивно на границах недели.
 *
 * Класс без Android и без часов (день и время подаёт вызывающий) — тестируется.
 */
data class MonitoringSchedule(
    val enabled: Boolean = false,
    val days: Set<Int> = DEFAULT_DAYS,
    val startMinuteOfDay: Int = DEFAULT_START,
    val endMinuteOfDay: Int = DEFAULT_END,
) {

    /**
     * Разрешён ли мониторинг в указанный момент.
     *
     * Выключенное расписание не ограничивает ничего — возвращает `true` всегда. Это важно для
     * вызывающего: он спрашивает «можно ли сейчас», а не «попадаем ли в окно».
     */
    fun isMonitoringAllowedAt(dayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return true
        if (days.isEmpty()) return false
        // Совпадающие границы читаем как «круглые сутки в отмеченные дни», а не как пустое окно:
        // пустое окно означало бы «расписание включено, но не работает никогда» — бесполезное
        // состояние, в которое легко попасть случайно.
        if (startMinuteOfDay == endMinuteOfDay) return dayOfWeek in days

        return if (startMinuteOfDay < endMinuteOfDay) {
            dayOfWeek in days && minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
        } else {
            // Окно через полночь: либо вечерняя часть сегодняшнего дня, либо утренняя часть,
            // унаследованная от вчерашнего.
            (dayOfWeek in days && minuteOfDay >= startMinuteOfDay) ||
                (previousDay(dayOfWeek) in days && minuteOfDay < endMinuteOfDay)
        }
    }

    companion object {
        /** Понедельник–пятница: самый частый случай (учёба и работа). */
        val DEFAULT_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5)

        /** 09:00 */
        const val DEFAULT_START = 9 * 60

        /** 18:00 */
        const val DEFAULT_END = 18 * 60

        const val MINUTES_PER_DAY = 24 * 60

        /** Предыдущий день недели с переходом через границу: перед понедельником — воскресенье. */
        fun previousDay(dayOfWeek: Int): Int = if (dayOfWeek == 1) 7 else dayOfWeek - 1
    }
}
