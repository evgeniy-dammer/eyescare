package com.eyecare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    childMode: Boolean,
    threshold: Int,
    thresholdOptions: List<Int>,
    languageLabel: String,
    themeMode: ThemeMode,
    ignoringBatteryOptimizations: Boolean,
    breakRemindersEnabled: Boolean,
    onChildModeToggle: (Boolean) -> Unit,
    onThresholdSelect: (Int) -> Unit,
    onBreakRemindersToggle: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onThemeClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    contentBottomPadding: Dp = 0.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Детский режим
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.title_child_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IosSwitch(checked = childMode, onCheckedChange = onChildModeToggle)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Напоминания о перерывах (20-20-20)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_break_reminders),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IosSwitch(checked = breakRemindersEnabled, onCheckedChange = onBreakRemindersToggle)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Порог срабатывания — сегментированный контрол
                Text(
                    text = stringResource(R.string.label_threshold),
                    style = MaterialTheme.typography.bodyLarge,
                )
                IosSegmentedControl(
                    options = thresholdOptions,
                    selected = threshold,
                    onSelect = onThresholdSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )

                HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

                // Язык (открывает детальный экран)
                NavigationRow(
                    label = stringResource(R.string.label_language),
                    value = languageLabel,
                    onClick = onLanguageClick,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Тема (открывает детальный экран)
                val themeLabel = when (themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.language_system_default)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                }
                NavigationRow(
                    label = stringResource(R.string.label_theme),
                    value = themeLabel,
                    onClick = onThemeClick,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Работа в фоне (открывает детальный экран)
                val backgroundStatus = if (ignoringBatteryOptimizations) {
                    stringResource(R.string.battery_opt_unrestricted)
                } else {
                    stringResource(R.string.battery_opt_restricted)
                }
                NavigationRow(
                    label = stringResource(R.string.label_background),
                    value = backgroundStatus,
                    onClick = onBackgroundClick,
                )
            }
        }
    }
}

/** Строка-переход в стиле iOS: слева заголовок, справа серое значение и шеврон «›». */
@Composable
private fun NavigationRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
