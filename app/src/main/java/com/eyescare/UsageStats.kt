package com.eyescare

/** Сводка использования за текущую неделю (сбрасывается при переходе на новую неделю). */
data class WeeklyStats(
    val monitoringSeconds: Long = 0,
    val tooCloseSeconds: Long = 0,
    val tooCloseEvents: Int = 0,
)

/**
 * Идентификатор недели (с выравниванием на понедельник) для дня по epoch-day.
 * 1970-01-01 = день 0 = четверг, поэтому сдвигаем на +3, чтобы понедельник открывал неделю.
 * Чистая функция — легко тестируется.
 */
fun weekIdForEpochDay(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7)
