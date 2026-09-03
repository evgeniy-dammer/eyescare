package com.eyescare

/**
 * Статистика за один день.
 *
 * Средняя дистанция хранится не как готовое среднее, а как сумма замеров и их количество: только так
 * можно дописывать новые замеры в уже сохранённый день, не храня всю выборку. Пересчёт среднего из
 * готового среднего потерял бы вес прошлых замеров.
 *
 * [epochDay] — день по `LocalDate.toEpochDay()`; часовой пояс и календарь — забота вызывающего.
 */
data class DailyStats(
    val epochDay: Long,
    val monitoringSeconds: Long = 0,
    val tooCloseSeconds: Long = 0,
    val tooCloseEvents: Int = 0,
    val distanceSumCm: Long = 0,
    val distanceSamples: Long = 0,
) {

    /** Средняя дистанция за день в см; `null` — в этот день не было ни одного замера. */
    val averageDistanceCm: Float?
        get() = if (distanceSamples > 0) distanceSumCm.toFloat() / distanceSamples else null

    /** Доля времени «слишком близко» от времени мониторинга (0..1); `null` — не мониторили. */
    val tooCloseShare: Float?
        get() = if (monitoringSeconds > 0) {
            (tooCloseSeconds.toFloat() / monitoringSeconds).coerceIn(0f, 1f)
        } else {
            null
        }

    /** Был ли день вообще: пустые дни рисуются в графике как пропуски, а не как нули. */
    val hasData: Boolean get() = monitoringSeconds > 0 || distanceSamples > 0

    internal operator fun plus(other: DailyStats) = DailyStats(
        epochDay = epochDay,
        monitoringSeconds = monitoringSeconds + other.monitoringSeconds,
        tooCloseSeconds = tooCloseSeconds + other.tooCloseSeconds,
        tooCloseEvents = tooCloseEvents + other.tooCloseEvents,
        distanceSumCm = distanceSumCm + other.distanceSumCm,
        distanceSamples = distanceSamples + other.distanceSamples,
    )
}

/**
 * История использования по дням: источник правды для графика и для сводки за неделю.
 *
 * Зачем ряд по дням, а не счётчики за неделю: недельные счётчики отвечают только на вопрос «сколько
 * всего», и сбрасываются в понедельник, стирая любую динамику. По ряду видно, стало лучше или хуже,
 * и на нём же строятся серии «хороших» дней и отчёт для родителя.
 *
 * Хранится компактной строкой (десятки байт на день), а не в Room: объём — 30 записей, а лишняя
 * зависимость с кодогенерацией стоила бы дороже, чем сама задача. Формат разбирается терпимо к
 * мусору: испорченная запись пропускается, а не роняет старт.
 *
 * Класс без Android и без часов (сегодняшний день подаёт вызывающий) — тестируется.
 */
class StatsHistory private constructor(
    /** Дни по возрастанию [DailyStats.epochDay], без дубликатов. */
    val days: List<DailyStats>,
) {

    /** Данные за конкретный день (`null` — записи нет). */
    fun forDay(epochDay: Long): DailyStats? = days.firstOrNull { it.epochDay == epochDay }

    /**
     * Прибавляет [delta] к дню [DailyStats.epochDay] (создавая запись, если её не было) и
     * отбрасывает всё старше [keepDays] относительно [todayEpochDay].
     *
     * Обрезка именно здесь, а не отдельным вызовом: единственная точка записи — единственное место,
     * где история может вырасти, значит и единственное, где она обязана быть ограничена.
     */
    fun plus(delta: DailyStats, todayEpochDay: Long, keepDays: Int = KEEP_DAYS): StatsHistory {
        val merged = LinkedHashMap<Long, DailyStats>(days.size + 1)
        days.forEach { merged[it.epochDay] = it }
        merged[delta.epochDay] = merged[delta.epochDay]?.plus(delta) ?: delta
        val oldest = todayEpochDay - keepDays + 1
        // Дни из будущего (переведённые назад часы) не выбрасываем: они уже записаны, и потеря
        // данных хуже, чем лишняя точка справа на графике.
        return StatsHistory(merged.values.filter { it.epochDay >= oldest }.sortedBy { it.epochDay })
    }

    /**
     * Последние [count] дней, включая [todayEpochDay], по возрастанию. Дни без записей заполняются
     * пустыми [DailyStats]: графику нужен ряд равной длины с дырками, а не сжатый список.
     */
    fun lastDays(todayEpochDay: Long, count: Int): List<DailyStats> {
        if (count <= 0) return emptyList()
        val byDay = days.associateBy { it.epochDay }
        return (todayEpochDay - count + 1..todayEpochDay).map { byDay[it] ?: DailyStats(it) }
    }

    /** Суммарная сводка за последние [count] дней (для карточки «за 7 дней»). */
    fun totalsForLastDays(todayEpochDay: Long, count: Int): WeeklyStats {
        val window = lastDays(todayEpochDay, count)
        return WeeklyStats(
            monitoringSeconds = window.sumOf { it.monitoringSeconds },
            tooCloseSeconds = window.sumOf { it.tooCloseSeconds },
            tooCloseEvents = window.sumOf { it.tooCloseEvents },
        )
    }

    /**
     * Серия подряд идущих «хороших» дней, заканчивающаяся сегодня или вчера.
     *
     * Хороший день — тот, где мониторинг работал и доля времени «слишком близко» не больше
     * [maxTooCloseShare]. Сегодняшний день ещё не закончился, поэтому пустое «сегодня» серию не
     * обрывает — иначе каждое утро обнуляло бы её до первого замера.
     */
    fun goodDayStreak(todayEpochDay: Long, maxTooCloseShare: Float): Int {
        var streak = 0
        var day = todayEpochDay
        if (forDay(todayEpochDay)?.hasData != true) day-- // сегодня ещё впереди
        while (true) {
            val share = forDay(day)?.tooCloseShare ?: return streak
            if (share > maxTooCloseShare) return streak
            streak++
            day--
        }
    }

    /** Компактная строка для хранения: записи через `;`, поля внутри записи через `:`. */
    fun serialize(): String = days.joinToString(RECORD_SEPARATOR) {
        listOf(
            it.epochDay,
            it.monitoringSeconds,
            it.tooCloseSeconds,
            it.tooCloseEvents,
            it.distanceSumCm,
            it.distanceSamples,
        ).joinToString(FIELD_SEPARATOR)
    }

    companion object {
        /** Глубина истории. Месяц — столько, сколько человек готов считать «недавним». */
        const val KEEP_DAYS = 30

        /** Окно недельной сводки: скользящие 7 дней, а не календарная неделя. */
        const val WEEK_DAYS = 7

        /**
         * Порог «хорошего дня»: не больше 10% времени мониторинга слишком близко.
         *
         * Ноль был бы недостижим — нагнуться к экрану на минуту за рабочий день нормально, и серия,
         * которая рвётся каждый день, не мотивирует. Значение подобрано как заведомо достижимое;
         * данных о реальном распределении пока нет, уточнить после сбора статистики.
         */
        const val GOOD_DAY_MAX_TOO_CLOSE_SHARE = 0.1f

        private const val RECORD_SEPARATOR = ";"
        private const val FIELD_SEPARATOR = ":"
        private const val FIELD_COUNT = 6

        val EMPTY = StatsHistory(emptyList())

        /**
         * Разбирает строку [serialize]. Испорченные записи молча пропускаются: единственная
         * альтернатива — падать при старте на данных, которые пользователь всё равно не починит.
         */
        fun parse(raw: String?): StatsHistory {
            if (raw.isNullOrBlank()) return EMPTY
            val parsed = raw.split(RECORD_SEPARATOR).mapNotNull { record ->
                val f = record.split(FIELD_SEPARATOR)
                if (f.size != FIELD_COUNT) return@mapNotNull null
                val epochDay = f[0].toLongOrNull() ?: return@mapNotNull null
                DailyStats(
                    epochDay = epochDay,
                    monitoringSeconds = f[1].toLongOrNull() ?: return@mapNotNull null,
                    tooCloseSeconds = f[2].toLongOrNull() ?: return@mapNotNull null,
                    tooCloseEvents = f[3].toIntOrNull() ?: return@mapNotNull null,
                    distanceSumCm = f[4].toLongOrNull() ?: return@mapNotNull null,
                    distanceSamples = f[5].toLongOrNull() ?: return@mapNotNull null,
                )
            }
            // Дубликаты дней (теоретически возможны при повреждении) складываем, а не теряем.
            val merged = LinkedHashMap<Long, DailyStats>(parsed.size)
            parsed.forEach { d -> merged[d.epochDay] = merged[d.epochDay]?.plus(d) ?: d }
            return StatsHistory(merged.values.sortedBy { it.epochDay })
        }
    }
}
