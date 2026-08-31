package com.eyescare

/**
 * Логика напоминания об осанке («text neck»): голова долго наклонена вниз — нагрузка на шейный
 * отдел растёт кратно (при 30° голова «весит» для шеи примерно вдвое больше, чем при прямой
 * посадке).
 *
 * На вход подаётся уже посчитанный наклон головы от вертикали (см. [PostureMath.neckFlexionDeg]),
 * а не сырой угол от ML Kit. Защита от назойливости — в [SustainedThreshold]; здесь только пороги.
 */
class PostureMonitor(
    warnAtDeg: Float = WARN_DEG,
    releaseAtDeg: Float = RELEASE_DEG,
    dwellMs: Long = DWELL_MS,
    cooldownMs: Long = COOLDOWN_MS,
) {
    private val condition = SustainedThreshold(
        enterAt = warnAtDeg,     // плохо — когда наклон стал БОЛЬШЕ этого
        exitAt = releaseAtDeg,   // нормально — когда стал МЕНЬШЕ этого
        dwellMs = dwellMs,
        cooldownMs = cooldownMs,
    )

    /**
     * Очередной замер наклона. Возвращает `true` РОВНО в тот момент, когда пора напомнить об осанке.
     *
     * @param flexionDeg наклон головы вниз от вертикали в градусах.
     */
    fun update(nowMs: Long, flexionDeg: Float): Boolean = condition.update(nowMs, flexionDeg)

    /** Сбрасывает отсчёт при потере лица или паузе; пауза между напоминаниями сохраняется. */
    fun reset() = condition.reset()

    companion object {
        /** Выше этого наклона — напоминаем. 30° — общепринятая граница заметного роста нагрузки. */
        const val WARN_DEG = 30f

        /** Ниже этого — считаем посадку нормальной. Зазор с [WARN_DEG] — петля гистерезиса. */
        const val RELEASE_DEG = 20f

        /** Наклон должен держаться непрерывно: разовый взгляд вниз ничего не запускает. */
        const val DWELL_MS = 60_000L

        /** Минимальная пауза между двумя напоминаниями. */
        const val COOLDOWN_MS = 30 * 60_000L
    }
}
