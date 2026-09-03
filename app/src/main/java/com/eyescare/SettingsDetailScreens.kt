package com.eyescare

import android.app.TimePickerDialog
import android.text.format.DateFormat
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar

/** Порядок должен совпадать с массивом R.array.language_names. */
val SUPPORTED_LANGUAGE_TAGS = listOf("en", "de", "fr", "es", "ru", "it", "tr")

/** Экран выбора языка (push-детализация, iOS-стиль: список с галочкой у выбранного). */
@Composable
fun LanguageDetailScreen(
    currentTag: String?,
    contentBottomPadding: Dp,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val names = stringArrayResource(R.array.language_names)
    DetailScreen(
        title = stringResource(R.string.label_language),
        contentBottomPadding = contentBottomPadding,
        onBack = onBack,
    ) {
        CheckableRow(stringResource(R.string.language_system_default), currentTag == null) { onSelect(null) }
        SUPPORTED_LANGUAGE_TAGS.forEachIndexed { index, tag ->
            CheckableRow(names[index], currentTag == tag) { onSelect(tag) }
        }
    }
}

/** Экран выбора темы (push-детализация). */
@Composable
fun ThemeDetailScreen(
    current: ThemeMode,
    contentBottomPadding: Dp,
    onSelect: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    DetailScreen(
        title = stringResource(R.string.label_theme),
        contentBottomPadding = contentBottomPadding,
        onBack = onBack,
    ) {
        CheckableRow(stringResource(R.string.language_system_default), current == ThemeMode.SYSTEM) { onSelect(ThemeMode.SYSTEM) }
        CheckableRow(stringResource(R.string.theme_light), current == ThemeMode.LIGHT) { onSelect(ThemeMode.LIGHT) }
        CheckableRow(stringResource(R.string.theme_dark), current == ThemeMode.DARK) { onSelect(ThemeMode.DARK) }
    }
}

/**
 * Экран «Работа в фоне»: объясняет, зачем нужны исключения, и даёт быстрый доступ к отключению
 * оптимизации батареи и к настройкам автозапуска (актуально для агрессивных OEM вроде MIUI).
 */
@Composable
fun BackgroundDetailScreen(
    ignoringBatteryOptimizations: Boolean,
    contentBottomPadding: Dp,
    onBatteryClick: () -> Unit,
    onAutostartClick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IosBackButton(label = stringResource(R.string.nav_settings), onClick = onBack)
        ScreenTitle(stringResource(R.string.label_background))
        Text(
            text = stringResource(R.string.background_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ActionRow(
                    title = stringResource(R.string.battery_opt_title),
                    subtitle = if (ignoringBatteryOptimizations) {
                        stringResource(R.string.battery_opt_unrestricted)
                    } else {
                        stringResource(R.string.battery_opt_restricted)
                    },
                    done = ignoringBatteryOptimizations,
                    onClick = onBatteryClick,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ActionRow(
                    title = stringResource(R.string.autostart_title),
                    subtitle = stringResource(R.string.autostart_desc),
                    done = false,
                    onClick = onAutostartClick,
                )
            }
        }
    }
}

/** Строка-действие: заголовок + пояснение, справа — галочка (готово) или шеврон (открыть). */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (done) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Экран расписания: в какие дни и часы мониторинг вообще работает.
 *
 * Названия дней берём из [DayOfWeek.getDisplayName] по текущей локали, а не из строковых ресурсов:
 * это семь строк на семь языков, которые система уже знает и склоняет правильно.
 */
@Composable
fun ScheduleDetailScreen(
    schedule: MonitoringSchedule,
    contentBottomPadding: Dp,
    onChange: (MonitoringSchedule) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    DetailScreen(
        title = stringResource(R.string.label_schedule),
        contentBottomPadding = contentBottomPadding,
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_enable),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IosSwitch(
                checked = schedule.enabled,
                onCheckedChange = { onChange(schedule.copy(enabled = it)) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.schedule_days),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 1..7 = понедельник..воскресенье, как в java.time.DayOfWeek.
            (1..7).forEach { day ->
                val selected = day in schedule.days
                val label = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            },
                        )
                        .clickable {
                            val days = if (selected) schedule.days - day else schedule.days + day
                            // Последний день снять нельзя: пустой набор дал бы «расписание
                            // включено, но не работает никогда» — то же бесполезное состояние,
                            // от которого уже защищены совпадающие границы времени. Выключить
                            // расписание целиком можно тумблером выше.
                            if (days.isNotEmpty()) onChange(schedule.copy(days = days))
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        TimeRow(
            label = stringResource(R.string.schedule_start),
            minuteOfDay = schedule.startMinuteOfDay,
            onPick = { onChange(schedule.copy(startMinuteOfDay = it)) },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        TimeRow(
            label = stringResource(R.string.schedule_end),
            minuteOfDay = schedule.endMinuteOfDay,
            onPick = { onChange(schedule.copy(endMinuteOfDay = it)) },
        )

        Text(
            text = if (schedule.startMinuteOfDay > schedule.endMinuteOfDay) {
                stringResource(R.string.schedule_hint_overnight)
            } else {
                stringResource(R.string.schedule_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Строка «Начало / Конец» с системным диалогом выбора времени (он уважает 12/24-часовой формат). */
@Composable
private fun TimeRow(label: String, minuteOfDay: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onPick(hour * 60 + minute) },
                    minuteOfDay / 60,
                    minuteOfDay % 60,
                    DateFormat.is24HourFormat(context),
                ).show()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = formatMinuteOfDay(minuteOfDay),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Время в том виде, в каком его показывает система (12/24 часа, локаль). */
@Composable
private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

@Composable
private fun DetailScreen(
    title: String,
    contentBottomPadding: Dp,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // iOS-навбар: тонкий шеврон + название предыдущего экрана, акцентным цветом.
        IosBackButton(label = stringResource(R.string.nav_settings), onClick = onBack)
        ScreenTitle(title)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CheckableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
