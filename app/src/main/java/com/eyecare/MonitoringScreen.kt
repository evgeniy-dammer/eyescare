package com.eyecare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun MonitoringScreen(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    weeklyStats: WeeklyStats = WeeklyStats(),
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
        }

        WeeklyStatsCard(weeklyStats)
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

@Composable
private fun LiveDistanceCard(status: MonitoringStatus) {
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
                        text = String.format(Locale.getDefault(), "%.1f", distance),
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
