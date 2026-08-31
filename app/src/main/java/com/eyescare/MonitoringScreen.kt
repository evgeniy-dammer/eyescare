package com.eyescare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import kotlinx.coroutines.delay

@Composable
fun MonitoringScreen(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    weeklyStats: WeeklyStats = WeeklyStats(),
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
            LiveDistanceCard(status)
            SnoozeCard(
                snoozeUntilElapsedMs = status.snoozeUntilElapsedMs,
                optionsMinutes = snoozeOptionsMinutes,
                onSnooze = onSnooze,
                onCancel = onCancelSnooze,
            )
        }

        WeeklyStatsCard(weeklyStats)
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
                Text(
                    text = stringResource(if (status.tooClose) R.string.too_close else R.string.status_active),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (status.tooClose) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
