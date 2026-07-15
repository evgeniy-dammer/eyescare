package com.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * После перезагрузки устройства напоминает возобновить мониторинг, если он был включён.
 *
 * Почему уведомление, а не прямой перезапуск сервиса: начиная с Android 14 (API 34) foreground-сервисы
 * типа `camera` НЕЛЬЗЯ запускать из фона (в том числе по BOOT_COMPLETED). Поэтому показываем
 * уведомление; тап открывает [MainActivity], и мониторинг возобновляется уже из foreground
 * (см. авто-возобновление в MainActivity.onResume).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> Unit
            else -> return
        }

        // Возобновлять нечего, если пользователь мониторинг не включал.
        if (!SettingsRepository.getInstance(context).isMonitoringEnabled()) return

        // showResume() сам проверяет разрешение на уведомления.
        MonitoringNotifications(context).apply {
            createChannels()
            showResume()
        }
    }
}
