package com.eyescare

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Отвечает за уведомления мониторинга: каналы, постоянное foreground-уведомление, предупреждение
 * «слишком близко», низкий заряд и напоминание о перерыве. Вынесено из [ForegroundMonitoringService],
 * чтобы отделить транспорт (уведомления) от логики сервиса.
 */
// Каждый notify() вызывается только после проверки hasPermission(); lint не отслеживает наш гейт.
@SuppressLint("MissingPermission")
class MonitoringNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** На Android 13+ уведомления требуют рантайм-разрешения; ниже — всегда разрешены. */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannels() {
        // minSdk 26 (O) → каналы доступны всегда.
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_monitoring_name), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(WARNING_CHANNEL_ID, context.getString(R.string.channel_warning_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_warning_desc)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(BREAK_CHANNEL_ID, context.getString(R.string.channel_break_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(RESUME_CHANNEL_ID, context.getString(R.string.channel_resume_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Постоянное уведомление foreground-сервиса (с кнопкой «Остановить»). */
    fun buildForeground(contentText: String): Notification {
        val stopIntent = Intent(context, ForegroundMonitoringService::class.java).apply {
            action = ForegroundMonitoringService.ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            context, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_running_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_stop), stopPending)
            .build()
    }

    /** Обновляет текст постоянного уведомления (без разрешения — no-op). */
    fun updateForeground(contentText: String) {
        if (hasPermission()) manager.notify(FOREGROUND_ID, buildForeground(contentText))
    }

    fun showWarning() {
        if (!hasPermission()) return
        val n = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.too_close))
            .setContentText(context.getString(R.string.notif_warning_text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(VIBRATION_PATTERN)
            .build()
        manager.notify(WARNING_ID, n)
    }

    fun cancelWarning() = manager.cancel(WARNING_ID)

    fun showLowBattery() {
        if (!hasPermission()) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_low_battery_title))
            .setContentText(context.getString(R.string.notif_low_battery_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        manager.notify(LOW_BATTERY_ID, n)
    }

    fun showBreak() {
        if (!hasPermission()) return
        val n = NotificationCompat.Builder(context, BREAK_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.break_title))
            .setContentText(context.getString(R.string.break_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(BREAK_ID, n)
    }

    /** Напоминание «возобновить мониторинг» — тап открывает приложение (после перезагрузки/выгрузки OEM). */
    fun showResume() {
        if (!hasPermission()) return
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.resume_title))
            .setContentText(context.getString(R.string.resume_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(RESUME_ID, n)
    }

    companion object {
        const val FOREGROUND_ID = 1
        private const val WARNING_ID = 2
        private const val RESUME_ID = 3
        private const val LOW_BATTERY_ID = 4
        private const val BREAK_ID = 5

        private const val CHANNEL_ID = "monitoring_service"
        private const val WARNING_CHANNEL_ID = "warning_service"
        private const val BREAK_CHANNEL_ID = "break_reminder"
        private const val RESUME_CHANNEL_ID = "resume_prompt"

        private val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500)
    }
}
