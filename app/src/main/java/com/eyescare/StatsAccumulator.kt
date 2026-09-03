package com.eyescare

import kotlin.math.roundToLong

/**
 * Накопление статистики использования по временным меткам (без Android и без часов — время подаёт
 * вызывающий). Держит открытые «отрезки» мониторинга и «слишком близко»; на [flush] отдаёт накопленные
 * секунды, которые вызывающий сохраняет в хранилище. Чистый класс — легко тестируется.
 */
class StatsAccumulator {

    // null = отрезок не открыт (не смешиваем с валидной меткой 0).
    private var monitorStartMs: Long? = null
    private var tooCloseStartMs: Long? = null

    // Замеры дистанции приходят с частотой кадров, поэтому копятся в памяти суммой и счётчиком:
    // писать в хранилище на каждый кадр нельзя, а хранить всю выборку ради среднего незачем.
    private var distanceSumCm: Long = 0
    private var distanceSamples: Long = 0

    /** Накопленное по закрытым/сброшенным отрезкам и замерам (для сохранения в хранилище). */
    data class Flushed(
        val monitoringSeconds: Long,
        val tooCloseSeconds: Long,
        val distanceSumCm: Long = 0,
        val distanceSamples: Long = 0,
    )

    /** Начать (или перезапустить) отсчёт активного мониторинга. */
    fun startMonitoring(nowMs: Long) {
        monitorStartMs = nowMs
    }

    /**
     * Замер дистанции для средней за день. Отрицательные и нулевые значения отбрасываем: это не
     * дистанция, а сбой измерения, и он утянул бы среднее вниз.
     */
    fun addDistanceSample(distanceCm: Float) {
        if (distanceCm <= 0f || !distanceCm.isFinite()) return
        // Округляем, а не отбрасываем дробную часть: усечение каждого замера сместило бы среднее
        // примерно на полсантиметра вниз — как раз в сторону ложной тревоги.
        distanceSumCm += distanceCm.roundToLong()
        distanceSamples++
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
        val flushed = Flushed(monitoring, tooClose, distanceSumCm, distanceSamples)
        // Замеры отдаём всегда — независимо от keepCounting: они уже случились, и «продолжать»
        // тут нечего.
        distanceSumCm = 0
        distanceSamples = 0
        return flushed
    }
}
