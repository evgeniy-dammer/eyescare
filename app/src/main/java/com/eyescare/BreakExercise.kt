package com.eyescare

/** Шаг гимнастики на перерыве. */
enum class BreakStep {
    /** Ядро правила 20-20-20: смотреть вдаль, чтобы расслабить аккомодацию. */
    LOOK_FAR,

    /** Осознанное моргание: за экраном моргают реже, из-за чего слёзная плёнка пересыхает. */
    BLINK,

    /** Перерыв закончен. */
    DONE,
}

/**
 * Сценарий перерыва: во что превращается напоминание 20-20-20, когда по нему нажимают.
 *
 * До этого напоминание было текстом в шторке без действия — его легко смахнуть, не сделав перерыв.
 * Здесь тот же перерыв становится отсчётом, за которым можно следить, не глядя в экран
 * (в этом и смысл: смотреть надо вдаль, а не на телефон).
 *
 * Длительности не выдуманы: 20 секунд взгляда вдаль — это само правило 20-20-20, ради которого
 * фича и существует; короткий блок моргания добавлен против сухости глаз.
 *
 * Класс без Android и без часов (время подаёт вызывающий) — тестируется.
 */
object BreakExercise {

    /** Смотреть вдаль — 20 секунд по правилу 20-20-20. */
    const val LOOK_FAR_MS = 20_000L

    /** Короткий блок осознанного моргания. */
    const val BLINK_MS = 5_000L

    const val TOTAL_MS = LOOK_FAR_MS + BLINK_MS

    /**
     * @param step текущий шаг.
     * @param remainingInStepMs сколько осталось до конца шага (0 на [BreakStep.DONE]).
     * @param stepFraction доля пройденного внутри шага, `0f..1f` — для анимации.
     */
    data class Progress(
        val step: BreakStep,
        val remainingInStepMs: Long,
        val stepFraction: Float,
    )

    /** Состояние перерыва через [elapsedMs] после его начала. */
    fun progressAt(elapsedMs: Long): Progress {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        return when {
            elapsed < LOOK_FAR_MS -> Progress(
                step = BreakStep.LOOK_FAR,
                remainingInStepMs = LOOK_FAR_MS - elapsed,
                stepFraction = elapsed.toFloat() / LOOK_FAR_MS,
            )
            elapsed < TOTAL_MS -> Progress(
                step = BreakStep.BLINK,
                remainingInStepMs = TOTAL_MS - elapsed,
                stepFraction = (elapsed - LOOK_FAR_MS).toFloat() / BLINK_MS,
            )
            else -> Progress(BreakStep.DONE, 0L, 1f)
        }
    }

    /**
     * Секунды для показа на экране — округление ВВЕРХ: пока на часах «1», секунда ещё идёт, и
     * ноль появляется ровно в момент конца шага, а не за секунду до него.
     */
    fun displaySeconds(remainingMs: Long): Int = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()
}
