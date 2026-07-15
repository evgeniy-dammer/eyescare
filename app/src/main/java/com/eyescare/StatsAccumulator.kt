package com.eyescare

/**
 * Накопление статистики использования по временным меткам (без Android и без часов — время подаёт
 * вызывающий). Держит открытые «отрезки» мониторинга и «слишком близко»; на [flush] отдаёт накопленные
 * секунды, которые вызывающий сохраняет в хранилище. Чистый класс — легко тестируется.
 */
class StatsAccumulator {

    // null = отрезок не открыт (не смешиваем с валидной меткой 0).
    private var monitorStartMs: Long? = null
    private var tooCloseStartMs: Long? = null

    /** Секунды, накопленные по закрытым/сброшенным отрезкам (для сохранения в хранилище). */
    data class Flushed(val monitoringSeconds: Long, val tooCloseSeconds: Long)

    /** Начать (или перезапустить) отсчёт активного мониторинга. */
    fun startMonitoring(nowMs: Long) {
        monitorStartMs = nowMs
    }

    /** Открыть отрезок «слишком близко». */
    fun tooCloseEngaged(nowMs: Long) {
        tooCloseStartMs = nowMs
    }

    /** Закрыть отрезок «слишком близко»; вернуть его длительность в секундах (0, если не был открыт). */
    fun tooCloseReleased(nowMs: Long): Long {
        val start = tooCloseStartMs ?: return 0L
        tooCloseStartMs = null
        return (nowMs - start) / 1000
    }

    /**
     * Отдаёт накопленное по открытым отрезкам. При [keepCounting] отрезки продолжаются с [nowMs]
     * (периодический сброс во время работы), иначе закрываются (пауза/остановка).
     */
    fun flush(nowMs: Long, keepCounting: Boolean): Flushed {
        var monitoring = 0L
        var tooClose = 0L
        monitorStartMs?.let {
            monitoring = (nowMs - it) / 1000
            monitorStartMs = if (keepCounting) nowMs else null
        }
        tooCloseStartMs?.let {
            tooClose = (nowMs - it) / 1000
            tooCloseStartMs = if (keepCounting) nowMs else null
        }
        return Flushed(monitoring, tooClose)
    }
}
