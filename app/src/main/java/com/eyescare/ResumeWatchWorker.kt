package com.eyescare

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Периодический «сторож» живучести: если мониторинг включён пользователем, но сервис не работает
 * (агрессивный OEM выгрузил процесс без перезагрузки, а приложение не открывали) — показывает
 * уведомление «возобновить». Camera-сервис из фона на Android 14+ не поднять, поэтому именно
 * уведомление: тап открывает приложение → срабатывает авто-возобновление.
 *
 * Когда мониторинг выключен — сторож сам себя отменяет.
 */
class ResumeWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val settings = SettingsRepository.getInstance(applicationContext)
        if (!settings.isMonitoringEnabled()) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME)
            return Result.success()
        }
        // running == true и в фоновой паузе по экрану (сервис жив) — тогда не беспокоим.
        if (!MonitoringStateHolder.state.value.running) {
            MonitoringNotifications(applicationContext).apply {
                createChannels()
                showResume()
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "resume_watch"
    }
}
