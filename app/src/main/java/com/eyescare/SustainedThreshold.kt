package com.eyescare

/**
 * «Условие держится достаточно долго, чтобы о нём стоило сказать» — общий механизм мягких
 * напоминаний (тёмная комната, наклон головы). Три слоя защиты от назойливости:
 *
 * 1. **Гистерезис.** Условие включается при пересечении [enterAt] и выключается только при
 *    пересечении [exitAt]; между порогами состояние сохраняется прежним, поэтому колебания
 *    величины на границе не дёргают напоминание.
 * 2. **Выдержка** [dwellMs] — срабатываем, только если условие держится непрерывно.
 * 3. **Пауза** [cooldownMs] между срабатываниями — если человек решил ничего не менять, мы не
 *    повторяем напоминание. Пауза переживает и выход из условия, и [reset].
 *
 * Направление сравнения выводится из порядка порогов и потому читается прямо в месте создания:
 * `SustainedThreshold(enterAt = 10f, exitAt = 30f, …)` — «включиться, когда упало ниже 10, выйти,
 * когда поднялось выше 30»; `SustainedThreshold(enterAt = 30f, exitAt = 20f, …)` — наоборот.
 *
 * Класс без Android-зависимостей и без часов (время подаёт вызывающий через [update]) — тестируется.
 */
class SustainedThreshold(
    private val enterAt: Float,
    private val exitAt: Float,
    private val dwellMs: Long,
    private val cooldownMs: Long,
) {
    /** true — условие «сверху» (наклон больше порога), false — «снизу» (света меньше порога). */
    private val higherIsActive = enterAt > exitAt

    private var active = false
    private var activeSinceMs = 0L
    private var lastFiredMs: Long? = null

    /** Держится ли условие прямо сейчас (без учёта выдержки и паузы). */
    val isActive: Boolean get() = active

    /**
     * Очередной замер. Возвращает `true` РОВНО в тот момент, когда пора показать напоминание;
     * в остальных случаях — `false`.
     */
    fun update(nowMs: Long, value: Float): Boolean {
        val nowActive = if (higherIsActive) {
            when {
                value > enterAt -> true
                value < exitAt -> false
                else -> active // зона гистерезиса — держим прежнее состояние
            }
        } else {
            when {
                value < enterAt -> true
                value > exitAt -> false
                else -> active
            }
        }
        if (nowActive && !active) activeSinceMs = nowMs
        active = nowActive

        if (!active) return false
        if (nowMs - activeSinceMs < dwellMs) return false

        val last = lastFiredMs
        if (last != null && nowMs - last < cooldownMs) return false

        lastFiredMs = nowMs
        activeSinceMs = nowMs // выдержку отсчитываем заново
        return true
    }

    /**
     * Сбрасывает отсчёт выдержки — когда данные перестали поступать (экран погас, лицо ушло из
     * кадра). Пауза между срабатываниями СОХРАНЯЕТСЯ: она живёт столько же, сколько сервис, и не
     * должна обнуляться каждым перерывом в данных.
     */
    fun reset() {
        active = false
        activeSinceMs = 0L
    }
}
