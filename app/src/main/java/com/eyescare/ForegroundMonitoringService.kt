package com.eyescare

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LifecycleService

class ForegroundMonitoringService : LifecycleService() {

    private lateinit var cameraAnalyzer: CameraAnalyzer
    private lateinit var overlayManager: OverlayManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notifications: MonitoringNotifications

    private var lastKnownDistance: Float? = null
    private var isThresholdExceeded = false
    private var lastNotificationUpdateMs = 0L
    private var pausedForScreenOff = false

    private val handler = Handler(Looper.getMainLooper())
    private val breakReminder = BreakReminder()
    private val stats = StatsAccumulator()
    private val statsFlushRunnable = object : Runnable {
        override fun run() {
            flushStats(keepCounting = true)
            handler.postDelayed(this, STATS_FLUSH_INTERVAL_MS)
        }
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.eyescare.ACTION_STOP_SERVICE"
        // Троттлинг перестроения уведомления: дистанция приходит часто, обновлять уведомление
        // так же часто нет смысла (нагрузка/батарея).
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L
        // Порог авто-выключения при низком заряде (если не заряжается) — экономия батареи.
        private const val LOW_BATTERY_PERCENT = 15
        // Как часто сбрасывать накопленную статистику в prefs (страховка на случай убийства процесса).
        private const val STATS_FLUSH_INTERVAL_MS = 120_000L
    }

    // Авто-выключение при низком заряде: система шлёт ACTION_BATTERY_LOW при достижении порога.
    private val batteryLowReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_LOW && !isCharging()) {
                stopDueToLowBattery()
            }
        }
    }

    // Экономия батареи: при выключенном экране смотреть не на что — освобождаем камеру и
    // поднимаем её обратно при включении. Foreground-сервис при этом остаётся жив (иначе на
    // Android 14+ нельзя было бы снова стартовать camera-сервис из фонового broadcast).
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseForScreenOff()
                Intent.ACTION_SCREEN_ON -> resumeAfterScreenOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = MonitoringNotifications(this)
        overlayManager = OverlayManager(this)
        settingsRepository = SettingsRepository.getInstance(this)
        cameraAnalyzer = CameraAnalyzer(
            context = this,
            lifecycleOwner = this,
            settingsRepository = settingsRepository,
            onDistanceUpdate = { distance ->
                lastKnownDistance = distance
                MonitoringStateHolder.setDistance(distance) // «живой» показ в UI
                updateNotification(null) // Обновляем с последним расстоянием
                maybeRemindBreak(facePresent = distance != null) // правило 20-20-20
            },
            onStatusUpdate = { status ->
                updateNotification(status)
            },
            // Прототип (только debug): дистанция по радужке для сравнения с IPD-методом.
            onIrisDistanceUpdate = { irisDistance ->
                MonitoringStateHolder.setIrisDistance(irisDistance)
            },
            onThresholdExceeded = { isExceeded ->
                MonitoringStateHolder.setTooClose(isExceeded)
                handleThresholdExceeded(isExceeded)
            }
        )
        registerReceiver(batteryLowReceiver, IntentFilter(Intent.ACTION_BATTERY_LOW))
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()

        // Если заряд уже низкий и устройство не заряжается — не запускаем мониторинг.
        if (batteryPercent() <= LOW_BATTERY_PERCENT && !isCharging()) {
            stopDueToLowBattery()
            return START_NOT_STICKY
        }

        cameraAnalyzer.start()
        MonitoringStateHolder.setRunning(true)
        stats.startMonitoring(SystemClock.elapsedRealtime())
        handler.postDelayed(statsFlushRunnable, STATS_FLUSH_INTERVAL_MS)
        // НЕ sticky: авто-перезапуск системой camera-FGS на Android 14+ ненадёжен (нельзя вывести
        // while-in-use сервис в foreground из фона). Восстановление — через авто-возобновление при
        // открытии приложения, BootReceiver и WorkManager-сторож (ResumeWatchWorker).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        flushStats(keepCounting = false)
        handler.removeCallbacks(statsFlushRunnable)
        cameraAnalyzer.shutdown()
        overlayManager.hideOverlay()
        notifications.cancelWarning()
        try {
            unregisterReceiver(batteryLowReceiver)
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // не был зарегистрирован — игнорируем
        }
        settingsRepository.setMonitoringEnabled(false)
        MonitoringStateHolder.reset()
    }

    /** Экран погас: освобождаем камеру (мониторить нечего) и убираем оверлей/«живое» состояние. */
    private fun pauseForScreenOff() {
        if (pausedForScreenOff) return
        pausedForScreenOff = true
        flushStats(keepCounting = false) // закрываем текущие отрезки статистики
        breakReminder.reset() // выключенный экран = перерыв
        cameraAnalyzer.stop()
        isThresholdExceeded = false
        overlayManager.hideOverlay()
        MonitoringStateHolder.setTooClose(false)
        MonitoringStateHolder.setDistance(null)
    }

    /** Экран включился: снова поднимаем камеру. Сервис уже foreground, так что это легально. */
    private fun resumeAfterScreenOn() {
        if (!pausedForScreenOff) return
        pausedForScreenOff = false
        cameraAnalyzer.start()
        stats.startMonitoring(SystemClock.elapsedRealtime())
    }

    /** Останавливает мониторинг из-за низкого заряда и уведомляет пользователя. */
    private fun stopDueToLowBattery() {
        flushStats(keepCounting = false)
        notifications.showLowBattery()
        // Сбрасываем флаг, чтобы авто-возобновление не подняло сервис снова, пока заряд низкий.
        settingsRepository.setMonitoringEnabled(false)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Текущий заряд в процентах (0..100); 100 при недоступности данных. */
    private fun batteryPercent(): Int {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else 100
    }

    private fun isCharging(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun handleThresholdExceeded(isExceeded: Boolean) {
        if (this.isThresholdExceeded == isExceeded) return // Избегаем дублирования
        this.isThresholdExceeded = isExceeded

        // Статистика: число сближений и суммарное время «слишком близко».
        val now = SystemClock.elapsedRealtime()
        if (isExceeded) {
            stats.tooCloseEngaged(now)
            settingsRepository.incrementTooCloseEvents()
        } else {
            settingsRepository.addTooCloseSeconds(stats.tooCloseReleased(now))
        }

        if (settingsRepository.isOverlayPermissionGranted()) {
            if (isExceeded) overlayManager.showOverlay() else overlayManager.hideOverlay()
        } else {
            if (isExceeded) notifications.showWarning() else notifications.cancelWarning()
        }
    }

    /**
     * Копит статистику: закрывает текущие отрезки (мониторинг и «слишком близко») в prefs.
     * При [keepCounting] отрезки продолжаются с текущего момента (периодический сброс), иначе — стоп.
     */
    private fun flushStats(keepCounting: Boolean) {
        val flushed = stats.flush(SystemClock.elapsedRealtime(), keepCounting)
        settingsRepository.addMonitoringSeconds(flushed.monitoringSeconds)
        settingsRepository.addTooCloseSeconds(flushed.tooCloseSeconds)
    }

    /** Правило 20-20-20: если включено и пора — показываем мягкое напоминание о перерыве. */
    private fun maybeRemindBreak(facePresent: Boolean) {
        if (!settingsRepository.isBreakRemindersEnabled()) return
        if (breakReminder.update(SystemClock.elapsedRealtime(), facePresent)) {
            notifications.showBreak()
        }
    }

    private fun updateNotification(status: String?) {
        if (!notifications.hasPermission()) return

        // Обновления по дистанции (status == null) троттлим; статусные сообщения показываем сразу.
        if (status == null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotificationUpdateMs < NOTIFICATION_UPDATE_INTERVAL_MS) return
            lastNotificationUpdateMs = now
        }

        val contentText = status ?: run {
            val distance = lastKnownDistance
            if (distance != null) {
                getString(R.string.notif_distance, distance)
            } else {
                getString(R.string.notif_face_not_found)
            }
        }
        notifications.updateForeground(contentText)
    }

    private fun startForegroundService() {
        notifications.createChannels()
        if (!notifications.hasPermission()) {
            stopSelf()
            return
        }
        startForeground(
            MonitoringNotifications.FOREGROUND_ID,
            notifications.buildForeground(getString(R.string.notif_initializing)),
        )
    }
}
