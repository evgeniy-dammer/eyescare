package com.eyescare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MonitoringScreen(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    weeklyStats: WeeklyStats = WeeklyStats(),
    dailyHistory: List<DailyStats> = emptyList(),
    goodDayStreak: Int = 0,
    thresholdCm: Int = 30,
    snoozeOptionsMinutes: List<Int> = emptyList(),
    onSnooze: (minutes: Int) -> Unit = {},
    onCancelSnooze: () -> Unit = {},
    contentBottomPadding: Dp = 0.dp,
) {
    val status by MonitoringStateHolder.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.nav_monitoring))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.title_monitoring),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IosSwitch(checked = enabled, onCheckedChange = onToggle)
            }
        }

        if (enabled) {
            // Пока камера отпущена намеренно — по снузу или по расписанию — «лицо не найдено»
            // вводило бы в заблуждение: показываем причину, а не отсутствие данных.
            when {
                status.pausedBySchedule -> OutsideScheduleCard()
                status.snoozeUntilElapsedMs != null -> Unit // объяснит карточка паузы ниже
                else -> LiveDistanceCard(status)
            }
            // Ставить паузу поверх паузы по расписанию бессмысленно — прячем управление.
            if (!status.pausedBySchedule) {
                SnoozeCard(
                    snoozeUntilElapsedMs = status.snoozeUntilElapsedMs,
                    optionsMinutes = snoozeOptionsMinutes,
                    onSnooze = onSnooze,
                    onCancel = onCancelSnooze,
                )
            }
        }

        WeeklyStatsCard(weeklyStats)
        DistanceHistoryCard(days = dailyHistory, thresholdCm = thresholdCm, goodDayStreak = goodDayStreak)
    }
}

/**
 * История дистанции: средняя за каждый из последних дней и порог, к которому её сравнивают.
 *
 * Зачем график, если рядом уже есть сводка: сумма за неделю отвечает только на «сколько всего» и
 * ничего не говорит о том, стало лучше или хуже. Динамику видно только рядом по дням.
 */
@Composable
private fun DistanceHistoryCard(days: List<DailyStats>, thresholdCm: Int, goodDayStreak: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.stats_history_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (days.none { it.hasData }) {
                Text(
                    text = stringResource(R.string.stats_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            DistanceHistoryChart(days = days, thresholdCm = thresholdCm)
            Text(
                text = stringResource(R.string.stats_history_threshold, thresholdCm) + "\n" +
                    stringResource(R.string.stats_history_legend),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (goodDayStreak > 0) {
                StatRow(stringResource(R.string.stats_streak), goodDayStreak.toString())
            }
        }
    }
}

/**
 * Столбики средней дистанции по дням с пунктирной линией порога.
 *
 * Шкала начинается от нуля: обрезанная снизу ось растянула бы разницу в пару сантиметров до
 * драматической — на графике про здоровье это была бы ложь. Верх берём с запасом от большего из
 * (максимум, порог), чтобы линия порога всегда попадала в кадр.
 */
@Composable
private fun DistanceHistoryChart(days: List<DailyStats>, thresholdCm: Int) {
    val locale = LocalLocale.current.platformLocale
    val maxValue = days.mapNotNull { it.averageDistanceCm }.maxOrNull() ?: 0f
    val scaleMax = (maxOf(maxValue, thresholdCm.toFloat()) * 1.2f).coerceAtLeast(1f)
    val todayEpochDay = days.lastOrNull()?.epochDay // ряд заканчивается сегодняшним днём

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            // Линия порога лежит под столбиками: она ориентир, а не главный объект.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(thresholdCm / scaleMax)
                    .align(Alignment.BottomStart),
                contentAlignment = Alignment.TopStart,
            ) {
                DashedLine()
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val average = day.averageDistanceCm
                    // TalkBack не увидит столбик сам: озвучиваем день и значение словами.
                    val description = if (average == null) {
                        stringResource(R.string.stats_history_bar_empty, fullWeekdayLabel(day.epochDay, locale))
                    } else {
                        stringResource(
                            R.string.stats_history_bar,
                            fullWeekdayLabel(day.epochDay, locale),
                            average.roundToInt(),
                        )
                    }
                    DistanceBar(
                        modifier = Modifier.weight(1f),
                        averageCm = average,
                        scaleMax = scaleMax,
                        thresholdCm = thresholdCm,
                        description = description,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                val isToday = day.epochDay == todayEpochDay
                Text(
                    text = weekdayLabel(day.epochDay, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Один день: столбик высотой в среднюю дистанцию.
 *
 * День без замеров — не ноль, а пропуск: рисуем едва заметную подложку, иначе выключенный на день
 * мониторинг выглядел бы как «сидел вплотную к экрану».
 *
 * Столбик ниже порога отмечен **и цветом, и штриховкой**. Одного цвета мало: дальтонизм — это
 * примерно каждый двенадцатый мужчина, а «красный столбик» для него ничем не отличается от
 * соседнего. Штриховка читается и в оттенках серого.
 */
@Composable
private fun DistanceBar(
    modifier: Modifier,
    averageCm: Float?,
    scaleMax: Float,
    thresholdCm: Int,
    description: String,
) {
    val belowThreshold = averageCm != null && averageCm < thresholdCm
    val barColor = when {
        averageCm == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        belowThreshold -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val hatchColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.55f)
    // Пропуск показываем полной высотой полупрозрачной подложки, значение — долей от шкалы.
    val fraction = if (averageCm == null) 1f else (averageCm / scaleMax).coerceIn(MIN_BAR_FRACTION, 1f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(barColor),
        ) {
            if (belowThreshold) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawHatch(hatchColor) }
            }
        }
    }
}

/**
 * Диагональная штриховка по всей высоте столбика. Линии идут за пределы области — родитель их
 * обрезает по своей форме, поэтому у скруглённой шапки штрихи не выпирают.
 */
private fun DrawScope.drawHatch(color: Color) {
    val step = HATCH_STEP_DP.dp.toPx()
    val stroke = HATCH_STROKE_DP.dp.toPx()
    var x = -size.height
    while (x < size.width + size.height) {
        drawLine(
            color = color,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = stroke,
        )
        x += step
    }
}

/** Пунктир порога: сплошная линия читалась бы как ещё один столбик. */
@Composable
private fun DashedLine() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}

/**
 * Короткое имя дня недели в языке приложения.
 *
 * Локаль берём из LocalLocale (а не из Locale.getDefault): у приложения свой выбор языка, и
 * системная локаль не пересобирает UI при его смене.
 */
private fun weekdayLabel(epochDay: Long, locale: java.util.Locale): String =
    java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek
        .getDisplayName(java.time.format.TextStyle.SHORT, locale)

/** Полное имя дня недели — для TalkBack: сокращение вслух звучит как набор букв. */
private fun fullWeekdayLabel(epochDay: Long, locale: java.util.Locale): String =
    java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek
        .getDisplayName(java.time.format.TextStyle.FULL, locale)

private val CHART_HEIGHT = 120.dp

/** Шаг и толщина штриховки «ниже порога»: достаточно редко, чтобы не слиться в заливку. */
private const val HATCH_STEP_DP = 7
private const val HATCH_STROKE_DP = 2

/** Совсем короткий столбик выглядел бы как отсутствие данных — держим видимый минимум. */
private const val MIN_BAR_FRACTION = 0.03f

/** Мониторинг включён, но сейчас вне окна расписания — камера отпущена намеренно. */
@Composable
private fun OutsideScheduleCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notif_outside_schedule),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.schedule_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Пауза мониторинга на время: легитимный близкий просмотр (фото, книга) не должен вынуждать
 * выключать приложение совсем. Пока пауза идёт, показываем остаток и кнопку возврата.
 */
@Composable
private fun SnoozeCard(
    snoozeUntilElapsedMs: Long?,
    optionsMinutes: List<Int>,
    onSnooze: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    if (optionsMinutes.isEmpty()) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (snoozeUntilElapsedMs != null) {
                Text(
                    text = stringResource(R.string.snooze_remaining, remainingMinutes(snoozeUntilElapsedMs)),
                    style = MaterialTheme.typography.titleMedium,
                )
                PillButton(
                    text = stringResource(R.string.action_resume_monitoring),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.snooze_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    optionsMinutes.forEach { minutes ->
                        PillButton(
                            text = stringResource(R.string.snooze_option_minutes, minutes),
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Остаток паузы в минутах, округлённый вверх, — пересчитывается раз в секунду, чтобы «осталось
 * 5 мин» не застывало на экране. Шкала та же, что у сервиса: [SystemClock.elapsedRealtime].
 */
@Composable
private fun remainingMinutes(untilElapsedMs: Long): Int {
    val remaining by produceState(initialValue = untilElapsedMs - SystemClock.elapsedRealtime(), untilElapsedMs) {
        while (true) {
            value = untilElapsedMs - SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    return ((remaining.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
}

/** Кнопка-пилюля в стиле iOS: заливка акцентом низкой насыщенности, текст акцентным цветом. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun WeeklyStatsCard(stats: WeeklyStats) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium,
            )
            StatRow(stringResource(R.string.stats_monitoring_time), formatDuration(stats.monitoringSeconds))
            StatRow(stringResource(R.string.stats_too_close_time), formatDuration(stats.tooCloseSeconds))
            StatRow(stringResource(R.string.stats_too_close_events), stats.tooCloseEvents.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Формат длительности: «Xч Yмин» либо «Yмин» (для недельной сводки). */
@Composable
private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val h = stringResource(R.string.unit_hour)
    val m = stringResource(R.string.unit_min)
    return if (hours > 0) "$hours$h $minutes$m" else "$minutes$m"
}

/**
 * Прототип (только debug): показывает дистанцию по радужке (MediaPipe) рядом с IPD-дистанцией и их
 * разницу — чтобы сравнить точность на устройстве при известном расстоянии (30/50/70 см).
 */
@Composable
private fun IrisComparison(ipdCm: Float?, irisCm: Float?) {
    // Локаль берём из LocalLocale: java.util.Locale в composable не является observable-состоянием
    // и UI не пересобирается при смене языка (у приложения есть свой выбор языка, per-app locales).
    val locale = LocalLocale.current.platformLocale
    Column(
        modifier = Modifier.padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "— сравнение (debug) —",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "IPD (ML Kit): " + (ipdCm?.let { String.format(locale, "%.1f см", it) } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Радужка (MediaPipe): " + (irisCm?.let { String.format(locale, "%.1f см", it) } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (ipdCm != null && irisCm != null) {
            Text(
                text = String.format(locale, "Δ = %.1f см", irisCm - ipdCm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveDistanceCard(status: MonitoringStatus) {
    val locale = LocalLocale.current.platformLocale
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val distance = status.distanceCm
            if (distance != null) {
                val valueColor = if (status.tooClose) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(locale, "%.1f", distance),
                        style = MaterialTheme.typography.displayLarge,
                        color = valueColor,
                    )
                    Text(
                        text = " " + stringResource(R.string.unit_cm),
                        style = MaterialTheme.typography.headlineSmall,
                        color = valueColor,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                // «Слишком близко» помечено значком, а не только красным цветом: цвет как
                // единственный носитель смысла не работает при дальтонизме и на плохом экране.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (status.tooClose) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null, // смысл несёт соседний текст
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(if (status.tooClose) R.string.too_close else R.string.status_active),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (status.tooClose) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (BuildConfig.DEBUG) {
                    IrisComparison(ipdCm = distance, irisCm = status.irisDistanceCm)
                }
            } else {
                Text(
                    text = stringResource(R.string.notif_face_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
