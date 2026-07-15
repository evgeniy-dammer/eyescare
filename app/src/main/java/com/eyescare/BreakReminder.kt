package com.eyescare

/**
 * Таймер правила 20-20-20: после [workMs] непрерывного «смотрения» (лицо в кадре) пора сделать
 * перерыв. Если лицо пропало на [breakResetMs] и дольше — считаем, что пользователь отвёл взгляд
 * (перерыв сделан), и обнуляем накопленное время.
 *
 * Класс без Android-зависимостей и без часов (время передаёт вызывающий через [update]) — тестируется.
 */
class BreakReminder(
    private val workMs: Long = 20 * 60 * 1000L, // 20 минут работы
    private val breakResetMs: Long = 20 * 1000L, // 20 секунд без лица = перерыв
) {
    private var accumulatedMs = 0L
    private var lastSampleMs = 0L
    private var faceLostSinceMs = 0L
    private var hadFace = false

    /**
     * Очередной замер состояния. Возвращает `true` РОВНО в тот момент, когда перерыв стал нужен
     * (после чего счётчик обнуляется — следующее срабатывание не раньше, чем через [workMs]).
     */
    fun update(nowMs: Long, facePresent: Boolean): Boolean {
        if (facePresent) {
            if (hadFace) accumulatedMs += nowMs - lastSampleMs
            hadFace = true
            faceLostSinceMs = 0L
            lastSampleMs = nowMs
            if (accumulatedMs >= workMs) {
                accumulatedMs = 0L
                return true
            }
            return false
        }

        // Лицо не в кадре — возможно, перерыв.
        if (faceLostSinceMs == 0L) {
            faceLostSinceMs = nowMs
        } else if (nowMs - faceLostSinceMs >= breakResetMs) {
            accumulatedMs = 0L // отвёл взгляд достаточно долго — перерыв засчитан
        }
        hadFace = false
        lastSampleMs = nowMs
        return false
    }

    /** Сбрасывает состояние (например, при выключении экрана — это тоже перерыв). */
    fun reset() {
        accumulatedMs = 0L
        lastSampleMs = 0L
        faceLostSinceMs = 0L
        hadFace = false
    }
}
