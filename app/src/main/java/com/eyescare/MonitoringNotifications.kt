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
 * «слишком близко», низкий заряд, напоминания о перерыве, тёмной комнате и осанке.
 * Вынесено из [ForegroundMonitoringService], чтобы отделить транспорт (уведомления) от логики сервиса.
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
        // Отдельные каналы (а не общий с перерывами), чтобы мягкие напоминания можно было приглушить
        // в системных настройках поштучно, не трогая предупреждения о дистанции.
        nm.createNotificationChannel(
            NotificationChannel(DARK_ROOM_CHANNEL_ID, context.getString(R.string.channel_dark_room_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(POSTURE_CHANNEL_ID, context.getString(R.string.channel_posture_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /**
     * Постоянное уведомление foreground-сервиса. Кроме «Остановить» несёт кнопку паузы —
     * пользователь по определению находится в другом приложении, когда ему нужно ненадолго
     * отключить контроль дистанции (посмотреть фото, почитать вблизи).
     */
    fun buildForeground(contentText: String, snoozed: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_running_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_stop),
                serviceAction(ForegroundMonitoringService.ACTION_STOP_SERVICE, REQ_STOP),
            )
        if (snoozed) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.action_resume_monitoring),
                serviceAction(ForegroundMonitoringService.ACTION_CANCEL_SNOOZE, REQ_CANCEL_SNOOZE),
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(
                    R.string.action_snooze_minutes,
                    ForegroundMonitoringService.DEFAULT_SNOOZE_MINUTES,
                ),
                serviceAction(
                    ForegroundMonitoringService.ACTION_SNOOZE,
                    REQ_SNOOZE,
                    ForegroundMonitoringService.DEFAULT_SNOOZE_MINUTES,
                ),
            )
        }
        return builder.build()
    }

    /**
     * PendingIntent к сервису. Разные requestCode обязательны: с одинаковым система переиспользует
     * один и тот же PendingIntent и кнопки начали бы делать одно и то же.
     */
    private fun serviceAction(action: String, requestCode: Int, snoozeMinutes: Int? = null): PendingIntent {
        val intent = Intent(context, ForegroundMonitoringService::class.java).apply {
            this.action = action
            snoozeMinutes?.let { putExtra(ForegroundMonitoringService.EXTRA_SNOOZE_MINUTES, it) }
        }
        return PendingIntent.getService(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Обновляет текст постоянного уведомления (без разрешения — no-op). */
    fun updateForeground(contentText: String, snoozed: Boolean = false) {
        if (hasPermission()) manager.notify(FOREGROUND_ID, buildForeground(contentText, snoozed))
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

    /** Мягкое напоминание «в комнате слишком темно» (датчик освещённости, см. [AmbientLightMonitor]). */
    fun showDarkRoom() {
        if (!hasPermission()) return
        val n = NotificationCompat.Builder(context, DARK_ROOM_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.dark_room_title))
            .setContentText(context.getString(R.string.dark_room_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(DARK_ROOM_ID, n)
    }

    /** Мягкое напоминание об осанке: голова долго наклонена вниз (см. [PostureMonitor]). */
    fun showPosture() {
        if (!hasPermission()) return
        val n = NotificationCompat.Builder(context, POSTURE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.posture_title))
            .setContentText(context.getString(R.string.posture_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(POSTURE_ID, n)
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
        private const val DARK_ROOM_ID = 6
        private const val POSTURE_ID = 7

        // requestCode для PendingIntent'ов кнопок — должны различаться, см. serviceAction().
        private const val REQ_STOP = 0
        private const val REQ_SNOOZE = 1
        private const val REQ_CANCEL_SNOOZE = 2

        private const val CHANNEL_ID = "monitoring_service"
        private const val WARNING_CHANNEL_ID = "warning_service"
        private const val BREAK_CHANNEL_ID = "break_reminder"
        private const val RESUME_CHANNEL_ID = "resume_prompt"
        private const val DARK_ROOM_CHANNEL_ID = "dark_room_warning"
        private const val POSTURE_CHANNEL_ID = "posture_reminder"

        private val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500)
    }
}
