package com.eyescare

/** Почему мониторинг сейчас не смотрит в камеру. */
enum class PauseReason {
    /** Экран погас — смотреть не на что (экономия батареи). */
    SCREEN_OFF,

    /** Пользователь сам поставил мониторинг на паузу на время (снуз). */
    SNOOZE,
}

/**
 * Причины паузы мониторинга. Камера должна работать ровно тогда, когда причин нет.
 *
 * Отдельный класс, а не пара булевых флагов в сервисе, потому что причины **накладываются**:
 * экран может погаснуть посреди снуза, снуз может истечь при погашенном экране, пользователь может
 * снять снуз, пока экран выключен. Каждый такой переход должен поднимать камеру ровно один раз и
 * только когда не осталось ни одной причины. Логика без Android и без часов — тестируется.
 *
 * Сервис при этом остаётся foreground-сервисом в любом случае: на Android 14+ camera-сервис нельзя
 * поднять из фона, поэтому пауза — это освобождение камеры, а не остановка сервиса.
 */
class MonitoringPause {

    private val reasons = LinkedHashSet<PauseReason>()
    private var snoozeUntilMs: Long? = null

    /** Стоит ли мониторинг на паузе хоть по какой-то причине. */
    val isPaused: Boolean get() = reasons.isNotEmpty()

    /** Активен ли пользовательский снуз (в том числе когда экран заодно погашен). */
    val isSnoozed: Boolean get() = PauseReason.SNOOZE in reasons

    /**
     * Ставит на паузу по указанной причине.
     * @return `true`, если мониторинг только что встал на паузу — камеру нужно отпустить.
     *         `false`, если он уже стоял по другой причине.
     */
    fun pause(reason: PauseReason): Boolean {
        val wasRunning = reasons.isEmpty()
        reasons.add(reason)
        return wasRunning
    }

    /**
     * Снимает указанную причину.
     * @return `true`, если причин не осталось — камеру нужно поднять.
     */
    fun resume(reason: PauseReason): Boolean {
        if (!reasons.remove(reason)) return false
        if (reason == PauseReason.SNOOZE) snoozeUntilMs = null
        return reasons.isEmpty()
    }

    /**
     * Ставит снуз на [durationMs]. Повторный вызов во время снуза продлевает его от [nowMs].
     * @return `true`, если мониторинг только что встал на паузу.
     */
    fun snooze(nowMs: Long, durationMs: Long): Boolean {
        snoozeUntilMs = nowMs + durationMs
        return pause(PauseReason.SNOOZE)
    }

    /** Момент окончания снуза в той же шкале, что и [nowMs] (`null` — снуза нет). */
    fun snoozeUntilMs(): Long? = snoozeUntilMs

    /** Сколько снуза осталось в миллисекундах (`null` — снуза нет; не бывает меньше нуля). */
    fun snoozeRemainingMs(nowMs: Long): Long? = snoozeUntilMs?.let { (it - nowMs).coerceAtLeast(0L) }

    /**
     * Снимает снуз, если его срок вышел. Вызывается по таймеру.
     * @return `true`, если после этого причин не осталось — камеру нужно поднять. Если экран всё
     *         ещё выключен, вернёт `false`: снуз снят, но камеру поднимать рано.
     */
    fun expireSnoozeIfDue(nowMs: Long): Boolean {
        val until = snoozeUntilMs ?: return false
        if (nowMs < until) return false
        return resume(PauseReason.SNOOZE)
    }
}
