package com.eyescare

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LifecycleService

class ForegroundMonitoringService : LifecycleService() {

    private lateinit var cameraAnalyzer: CameraAnalyzer
    private lateinit var overlayManager: OverlayManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notifications: MonitoringNotifications

    private var lastKnownDistance: Float? = null
    private var isThresholdExceeded = false
    private var lastNotificationUpdateMs = 0L

    // Причины паузы накладываются (экран + снуз), поэтому это отдельный объект, а не пара флагов.
    private val pause = MonitoringPause()

    private val handler = Handler(Looper.getMainLooper())
    private val breakReminder = BreakReminder()
    private val stats = StatsAccumulator()

    // Датчики, не связанные с камерой: освещённость (тёмная комната) и гравитация (наклон
    // устройства для осанки). На части устройств датчика может не быть — тогда getDefaultSensor
    // вернёт null и соответствующая фича просто молчит.
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as? SensorManager }
    private val lightSensor: Sensor? by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }

    /** TYPE_GRAVITY — фьюженный и уже отфильтрованный; где его нет, берём сырой акселерометр:
     *  у неподвижного в руке телефона он практически и есть вектор гравитации. */
    private val gravitySensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val ambientLight = AmbientLightMonitor()
    private val posture = PostureMonitor()

    /** Наклон устройства от вертикали; null — данных от датчика ещё не было. */
    private var deviceTiltDeg: Float? = null
    private var sensorsRegistered = false
    private var lastSensorLogMs = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_LIGHT -> onLuxSample(event.values.firstOrNull() ?: return)
                Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                    if (event.values.size < 3) return
                    deviceTiltDeg = PostureMath.deviceTiltFromVerticalDeg(event.values[1], event.values[2])
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Снуз закончился — поднимаем камеру (если экран к этому моменту не погас). */
    private val snoozeExpiryRunnable = Runnable { expireSnooze() }

    /**
     * Периодическая сверка с расписанием. Тик, а не будильник: сервис и так жив всё время (иначе
     * camera-FGS не поднять из фона на Android 14+), а точность до минуты здесь достаточна.
     * Проверка вызывается ещё и по включению экрана — в глубоком сне Handler не тикает.
     */
    private val scheduleRunnable = object : Runnable {
        override fun run() {
            applySchedule()
            handler.postDelayed(this, SCHEDULE_CHECK_INTERVAL_MS)
        }
    }
    private val statsFlushRunnable = object : Runnable {
        override fun run() {
            flushStats(keepCounting = true)
            handler.postDelayed(this, STATS_FLUSH_INTERVAL_MS)
        }
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.eyescare.ACTION_STOP_SERVICE"
        const val ACTION_SNOOZE = "com.eyescare.ACTION_SNOOZE"
        const val ACTION_CANCEL_SNOOZE = "com.eyescare.ACTION_CANCEL_SNOOZE"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        /** Варианты паузы в минутах, предлагаемые на экране мониторинга. */
        val SNOOZE_OPTIONS_MINUTES = listOf(15, 30, 60)

        /** Длительность паузы для кнопки в уведомлении (там выбирать не из чего). */
        const val DEFAULT_SNOOZE_MINUTES = 15
        // Троттлинг перестроения уведомления: дистанция приходит часто, обновлять уведомление
        // так же часто нет смысла (нагрузка/батарея).
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1000L
        // Порог авто-выключения при низком заряде (если не заряжается) — экономия батареи.
        private const val LOW_BATTERY_PERCENT = 15
        // Как часто сбрасывать накопленную статистику в prefs (страховка на случай убийства процесса).
        private const val STATS_FLUSH_INTERVAL_MS = 120_000L
        // Как часто сверяться с расписанием (границы окна заданы с точностью до минуты).
        private const val SCHEDULE_CHECK_INTERVAL_MS = 30_000L
        // Троттлинг debug-логов датчиков (подбор порогов на устройстве).
        private const val SENSOR_LOG_INTERVAL_MS = 700L
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
                Intent.ACTION_SCREEN_OFF -> pauseMonitoring(PauseReason.SCREEN_OFF)
                Intent.ACTION_SCREEN_ON -> {
                    // Снуз мог истечь, пока устройство спало: Handler.postDelayed отсчитывает
                    // uptime и в deep sleep стоит, а elapsedRealtime идёт. Поэтому сначала
                    // снимаем просроченный снуз, иначе он держал бы камеру до срабатывания
                    // таймера уже после пробуждения.
                    expireSnooze()
                    // Пока устройство спало, окно расписания могло закрыться или открыться,
                    // а тик Handler'а в глубоком сне не шёл.
                    applySchedule()
                    resumeMonitoring(PauseReason.SCREEN_OFF)
                }
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
            },
            // Осанка: угол лица относительно камеры; вместе с наклоном устройства даёт наклон
            // головы от вертикали (см. PostureMath).
            onHeadPitchUpdate = ::onHeadPitchSample,
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

        // Снуз приходит и из UI, и из кнопки в уведомлении. startForegroundService() здесь
        // обязателен и идемпотентен: если система создала сервис ради этого интента, мы всё равно
        // должны вывести его в foreground, иначе Android убьёт процесс за нарушение контракта.
        if (intent?.action == ACTION_SNOOZE) {
            startForegroundService()
            val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
            startSnooze(minutes * 60_000L)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL_SNOOZE) {
            startForegroundService()
            cancelSnooze()
            return START_NOT_STICKY
        }

        startForegroundService()

        // Если заряд уже низкий и устройство не заряжается — не запускаем мониторинг.
        if (batteryPercent() <= LOW_BATTERY_PERCENT && !isCharging()) {
            stopDueToLowBattery()
            return START_NOT_STICKY
        }

        // Явный запуск — это решение пользователя включить мониторинг: снимаем снуз, если он
        // почему-то остался от предыдущего цикла, чтобы камера и состояние паузы не разъехались.
        handler.removeCallbacks(snoozeExpiryRunnable)
        if (pause.isSnoozed) {
            pause.resume(PauseReason.SNOOZE)
            MonitoringStateHolder.setSnoozeUntil(null)
        }

        cameraAnalyzer.start()
        startSensors()
        MonitoringStateHolder.setRunning(true)
        stats.startMonitoring(SystemClock.elapsedRealtime())
        handler.postDelayed(statsFlushRunnable, STATS_FLUSH_INTERVAL_MS)
        // Сразу приводим состояние в соответствие расписанию: включить мониторинг могли и вне окна.
        applySchedule()
        handler.postDelayed(scheduleRunnable, SCHEDULE_CHECK_INTERVAL_MS)
        // НЕ sticky: авто-перезапуск системой camera-FGS на Android 14+ ненадёжен (нельзя вывести
        // while-in-use сервис в foreground из фона). Восстановление — через авто-возобновление при
        // открытии приложения, BootReceiver и WorkManager-сторож (ResumeWatchWorker).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        flushStats(keepCounting = false)
        handler.removeCallbacks(statsFlushRunnable)
        handler.removeCallbacks(snoozeExpiryRunnable)
        handler.removeCallbacks(scheduleRunnable)
        stopSensors()
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

    /**
     * Ставит мониторинг на паузу по указанной причине. Причины накладываются: если мониторинг уже
     * стоял (например, по снузу), второе основание камеру повторно не трогает — см. [MonitoringPause].
     */
    private fun pauseMonitoring(reason: PauseReason) {
        if (pause.pause(reason)) releaseCamera()
    }

    /** Снимает причину паузы; камера поднимается, только когда не осталось ни одной. */
    private fun resumeMonitoring(reason: PauseReason) {
        if (pause.resume(reason)) acquireCamera()
    }

    /** Собственно освобождение камеры и датчиков (и всего, что зависит от «живых» данных). */
    private fun releaseCamera() {
        flushStats(keepCounting = false) // закрываем текущие отрезки статистики
        breakReminder.reset() // пауза = перерыв
        posture.reset()
        stopSensors()
        cameraAnalyzer.stop()
        isThresholdExceeded = false
        overlayManager.hideOverlay()
        MonitoringStateHolder.setTooClose(false)
        MonitoringStateHolder.setDistance(null)
    }

    /** Возврат к работе. Сервис всё это время оставался foreground, так что поднять камеру легально. */
    private fun acquireCamera() {
        cameraAnalyzer.start()
        startSensors()
        stats.startMonitoring(SystemClock.elapsedRealtime())
    }

    /** Пауза мониторинга на заданное время; повторный вызов продлевает её. */
    private fun startSnooze(durationMs: Long) {
        if (pause.snooze(SystemClock.elapsedRealtime(), durationMs)) releaseCamera()
        handler.removeCallbacks(snoozeExpiryRunnable)
        handler.postDelayed(snoozeExpiryRunnable, durationMs)
        MonitoringStateHolder.setSnoozeUntil(pause.snoozeUntilMs())
        updateNotification(null, force = true)
    }

    /** Пользователь снял паузу вручную. */
    private fun cancelSnooze() {
        handler.removeCallbacks(snoozeExpiryRunnable)
        resumeMonitoring(PauseReason.SNOOZE)
        MonitoringStateHolder.setSnoozeUntil(null)
        updateNotification(null, force = true)
    }

    /** Сработал таймер снуза. Если экран к этому моменту погас, камера останется отпущенной. */
    private fun expireSnooze() {
        if (pause.expireSnoozeIfDue(SystemClock.elapsedRealtime())) acquireCamera()
        MonitoringStateHolder.setSnoozeUntil(pause.snoozeUntilMs())
        updateNotification(null, force = true)
    }

    /**
     * Сверяет текущее время с расписанием и ставит/снимает соответствующую причину паузы.
     * Вне окна камера отпускается, но сервис остаётся foreground — иначе обратно его не поднять.
     */
    private fun applySchedule() {
        val schedule = settingsRepository.getSchedule()
        val now = java.time.LocalDateTime.now()
        val allowed = schedule.isMonitoringAllowedAt(
            dayOfWeek = now.dayOfWeek.value,
            minuteOfDay = now.hour * 60 + now.minute,
        )
        val pausedBySchedule = pause.isPausedBySchedule
        if (allowed && pausedBySchedule) {
            resumeMonitoring(PauseReason.SCHEDULE)
            updateNotification(null, force = true)
        } else if (!allowed && !pausedBySchedule) {
            pauseMonitoring(PauseReason.SCHEDULE)
            updateNotification(null, force = true)
        }
    }

    /**
     * Подписывается на датчики освещённости и гравитации. `SENSOR_DELAY_NORMAL` (~200 мс) —
     * с запасом: решения принимаются по выдержке в десятки секунд, а эти датчики почти не
     * расходуют батарею.
     */
    private fun startSensors() {
        if (sensorsRegistered) return
        val sm = sensorManager ?: return
        ambientLight.reset()
        posture.reset()
        var any = false
        lightSensor?.let { any = sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) || any }
        gravitySensor?.let { any = sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) || any }
        sensorsRegistered = any
    }

    private fun stopSensors() {
        if (!sensorsRegistered) return
        sensorManager?.unregisterListener(sensorListener)
        sensorsRegistered = false
        deviceTiltDeg = null
    }

    /**
     * Диагностика порогов (только debug): пороги освещённости и наклона подбираются под реальные
     * показания датчиков конкретного устройства, а по одним лишь справочным значениям их не
     * выставить. Троттлится, чтобы не залить logcat.
     * Смотреть: `adb logcat -s EyesCareSensors`.
     */
    private fun logSensors(message: String) {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSensorLogMs < SENSOR_LOG_INTERVAL_MS) return
        lastSensorLogMs = now
        Log.d("EyesCareSensors", message)
    }

    /** Освещённость: предупреждение о тёмной комнате. */
    private fun onLuxSample(lux: Float) {
        logSensors("lux=%.1f tilt=%s".format(lux, deviceTiltDeg?.let { "%.1f".format(it) } ?: "—"))
        // Настройки проверяем в момент замера, а не при подписке: тумблер можно переключить, пока
        // сервис уже работает, и переподписываться на каждое изменение настроек не хочется.
        if (!settingsRepository.isDarkRoomWarningEnabled()) return
        if (ambientLight.update(SystemClock.elapsedRealtime(), lux)) {
            notifications.showDarkRoom()
        }
    }

    /**
     * Осанка: наклон лица относительно камеры приходит с кадрами ML Kit, наклон устройства — от
     * датчика гравитации. Пока нет одного из двух (лицо ушло из кадра, датчика нет), отсчёт
     * выдержки сбрасывается — предупреждать не по чему.
     */
    private fun onHeadPitchSample(headEulerXDeg: Float?) {
        if (!settingsRepository.isPostureWarningEnabled()) {
            posture.reset()
            return
        }
        val tilt = deviceTiltDeg
        if (headEulerXDeg == null || tilt == null) {
            posture.reset()
            return
        }
        val flexion = PostureMath.neckFlexionDeg(tilt, headEulerXDeg)
        logSensors("tilt=%.1f headEulerX=%.1f flexion=%.1f".format(tilt, headEulerXDeg, flexion))
        if (posture.update(SystemClock.elapsedRealtime(), flexion)) {
            notifications.showPosture()
        }
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

    /**
     * @param force показать немедленно, минуя троттлинг — для смены состояния (снуз включён/снят),
     *        которую нельзя проглотить только потому, что секунду назад обновлялась дистанция.
     */
    private fun updateNotification(status: String?, force: Boolean = false) {
        if (!notifications.hasPermission()) return

        // Обновления по дистанции (status == null) троттлим; статусные сообщения показываем сразу.
        if (status == null && !force) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotificationUpdateMs < NOTIFICATION_UPDATE_INTERVAL_MS) return
            lastNotificationUpdateMs = now
        }

        val contentText = status ?: when {
            // Во время паузы дистанции нет, и «лицо не найдено» ввело бы в заблуждение.
            pause.isSnoozed -> getString(R.string.notif_snoozed)
            pause.isPausedBySchedule -> getString(R.string.notif_outside_schedule)
            lastKnownDistance != null -> getString(R.string.notif_distance, lastKnownDistance)
            else -> getString(R.string.notif_face_not_found)
        }
        notifications.updateForeground(contentText, snoozed = pause.isSnoozed)
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
