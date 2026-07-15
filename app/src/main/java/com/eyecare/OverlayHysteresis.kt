package com.eyecare

/**
 * Гистерезис (debounce) для показа/скрытия оверлея, ТЗ п. 6.5.
 *
 * Оверлей включается только после [framesToEngage] кадров подряд ниже порога и
 * выключается только после [framesToRelease] кадров подряд выше порога. Это
 * предотвращает мигание баннера, когда сглаженное расстояние колеблется вокруг порога.
 *
 * Класс не зависит от Android/ML Kit и принимает только примитивы — легко тестируется.
 */
class OverlayHysteresis(
    private val framesToEngage: Int = 3,
    private val framesToRelease: Int = 5
) {
    var isEngaged = false
        private set

    private var framesBelow = 0
    private var framesAbove = 0
    private var framesLost = 0

    /**
     * Обновляет состояние по текущему (уже сглаженному) расстоянию.
     *
     * @return `true` — если оверлей только что должен появиться, `false` — если только что
     *         должен исчезнуть, `null` — если состояние не изменилось.
     */
    fun update(distanceCm: Float, thresholdCm: Int): Boolean? {
        framesLost = 0 // лицо снова в кадре
        if (distanceCm < thresholdCm) {
            framesBelow++
            framesAbove = 0
        } else {
            framesAbove++
            framesBelow = 0
        }

        if (!isEngaged && framesBelow >= framesToEngage) {
            isEngaged = true
            return true
        }
        if (isEngaged && framesAbove >= framesToRelease) {
            isEngaged = false
            return false
        }
        return null
    }

    /**
     * Обрабатывает кадр без лица. Прогресс включения/выключения по дистанции сбрасывается,
     * а если оверлей был показан — снимаем его после [framesToRelease] кадров подряд без лица,
     * чтобы баннер не «висел» поверх других приложений, когда пользователь ушёл из кадра.
     *
     * @return `false` — если оверлей только что должен исчезнуть; иначе `null`.
     */
    fun onFaceLost(): Boolean? {
        framesBelow = 0
        framesAbove = 0
        if (!isEngaged) return null
        framesLost++
        if (framesLost >= framesToRelease) {
            isEngaged = false
            framesLost = 0
            return false
        }
        return null
    }

    /** Сбрасывает состояние (например, при перезапуске мониторинга). */
    fun reset() {
        isEngaged = false
        framesBelow = 0
        framesAbove = 0
        framesLost = 0
    }
}
