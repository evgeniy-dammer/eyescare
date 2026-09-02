package com.eyescare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        /** Тап по напоминанию 20-20-20 — открыть экран перерыва. */
        const val ACTION_SHOW_BREAK = "com.eyescare.ACTION_SHOW_BREAK"
    }

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var calibrationController: CalibrationController

    // Состояние главного/настроек для Compose; обновляется из настроек в refreshState().
    private var uiState by mutableStateOf(MainUiState())

    // Запрос согласия на камеру: если не null — показывается iOS-алерт.
    private var consentRequest by mutableStateOf<ConsentRequest?>(null)

    // Онбординг первого запуска (показывается один раз вместо основного UI).
    private var onboardingDone by mutableStateOf(true)

    // Экран перерыва 20-20-20: открывается тапом по уведомлению, показывается вместо основного UI.
    private var showBreak by mutableStateOf(false)

    private class ConsentRequest(val onGranted: () -> Unit, val onDenied: () -> Unit)

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allPermissionsGranted = permissions.entries.all { it.value }
        if (allPermissionsGranted) {
            checkOverlayPermission()
        } else {
            resetMonitoring()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val hasPermission = Settings.canDrawOverlays(this)
        settingsRepository.setOverlayPermissionGranted(hasPermission)
        startMonitoringService()
    }

    // После возврата из диалога исключения из оптимизации батареи обновляем статус в UI.
    private val batteryExemptionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // контент рисуется под системными барами и стеклянной панелью табов

        settingsRepository = SettingsRepository.getInstance(this)
        calibrationController = CalibrationController(this, settingsRepository)
        onboardingDone = settingsRepository.isOnboardingDone()
        showBreak = consumeBreakIntent()
        refreshState()

        setContent {
            EyesCareTheme(themeMode = uiState.themeMode) {
                if (!onboardingDone) {
                    OnboardingScreen(onFinish = {
                        settingsRepository.setOnboardingDone(true)
                        onboardingDone = true
                    })
                    return@EyesCareTheme
                }

                if (showBreak) {
                    BreakScreen(onFinish = { showBreak = false })
                    return@EyesCareTheme
                }

                // Синхронизируем UI с фактическим состоянием сервиса: он может остановиться сам
                // (кнопка «Остановить» в уведомлении/оверлее) — тогда running → false, и тумблер
                // должен погаснуть, даже если Activity не проходила через onResume.
                val monitoringStatus by MonitoringStateHolder.state.collectAsState()
                LaunchedEffect(monitoringStatus.running) { refreshState() }

                AppScaffold(
                    mainState = uiState,
                    thresholdOptions = SettingsRepository.DISTANCE_THRESHOLD_OPTIONS,
                    calibrationController = calibrationController,
                    onMonitoringToggle = ::onMonitoringToggle,
                    onChildModeToggle = ::onChildModeToggle,
                    onThresholdSelect = ::onThresholdSelect,
                    onSelectLanguage = ::onSelectLanguage,
                    onSelectTheme = ::onSelectTheme,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onOpenAutostartSettings = ::openAutostartSettings,
                    onBreakRemindersToggle = ::onBreakRemindersToggle,
                    onDarkRoomWarningToggle = ::onDarkRoomWarningToggle,
                    onPostureWarningToggle = ::onPostureWarningToggle,
                    onScheduleChange = ::onScheduleChange,
                    onSnooze = ::snoozeMonitoring,
                    onCancelSnooze = ::cancelSnooze,
                    ensureConsent = ::ensureConsent,
                )

                consentRequest?.let { request ->
                    IosAlertDialog(
                        title = stringResource(R.string.consent_title),
                        message = stringResource(R.string.consent_message),
                        confirmText = stringResource(R.string.consent_agree),
                        cancelText = stringResource(R.string.action_cancel),
                        onConfirm = {
                            settingsRepository.setPrivacyConsentGiven(true)
                            consentRequest = null
                            request.onGranted()
                        },
                        onCancel = {
                            consentRequest = null
                            request.onDenied()
                        },
                    )
                }
            }
        }
    }

    /**
     * Приложение уже было открыто, когда нажали на уведомление о перерыве (activity объявлена
     * `singleTop`). Без этого экран перерыва не показался бы: onCreate во второй раз не вызывается.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (consumeBreakIntent()) showBreak = true
    }

    /**
     * Проверяет, что активность открыли ради перерыва, и СБРАСЫВАЕТ действие. Сброс обязателен:
     * иначе после выхода с экрана перерыва система при возврате из «недавних» подсунула бы тот же
     * интент, и перерыв открывался бы снова и снова.
     */
    private fun consumeBreakIntent(): Boolean {
        val current = intent ?: return false
        if (current.action != ACTION_SHOW_BREAK) return false
        current.action = null
        setIntent(current)
        return true
    }

    override fun onResume() {
        super.onResume()
        // Обновляем UI при возвращении на экран, например, после смены настроек.
        refreshState()
        autoResumeMonitoringIfNeeded()
    }

    /**
     * Возобновляет мониторинг, если он был включён, но сервис не работает (убит агрессивным
     * OEM-менеджером батареи или после перезагрузки). Вызывается из foreground, поэтому запуск
     * camera-сервиса легален и на Android 14+ (в отличие от старта из фона/по BOOT_COMPLETED).
     */
    private fun autoResumeMonitoringIfNeeded() {
        if (!settingsRepository.isMonitoringEnabled()) return
        if (MonitoringStateHolder.state.value.running) return
        // Разрешения могли отозвать, пока приложение было закрыто, — тогда не навязываем запрос.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            resetMonitoring()
            return
        }
        startMonitoringService()
    }

    override fun onDestroy() {
        super.onDestroy()
        calibrationController.shutdown()
    }

    private fun refreshState() {
        uiState = MainUiState(
            monitoringEnabled = settingsRepository.isMonitoringEnabled(),
            childMode = settingsRepository.isChildMode(),
            threshold = settingsRepository.getDistanceThreshold(),
            languageLabel = currentLanguageLabel(),
            languageTag = currentLanguageTag(),
            themeMode = settingsRepository.getThemeMode(),
            ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
            breakRemindersEnabled = settingsRepository.isBreakRemindersEnabled(),
            darkRoomWarningEnabled = settingsRepository.isDarkRoomWarningEnabled(),
            postureWarningEnabled = settingsRepository.isPostureWarningEnabled(),
            schedule = settingsRepository.getSchedule(),
            weeklyStats = settingsRepository.getWeeklyStats(),
        )
    }

    /** true, если приложение исключено из оптимизации батареи (ОС не будет усыплять фон). */
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun onMonitoringToggle(enabled: Boolean) {
        if (enabled) {
            ensureConsent(
                onGranted = { handleStartMonitoring() },
                onDenied = { resetMonitoring() },
            )
        } else {
            stopMonitoringService()
        }
    }

    private fun onChildModeToggle(enabled: Boolean) {
        settingsRepository.setChildMode(enabled)
        settingsRepository.clearCalibratedIpd()

        val newThreshold = if (enabled) {
            SettingsRepository.DISTANCE_THRESHOLD_CHILD_DEFAULT
        } else {
            SettingsRepository.DISTANCE_THRESHOLD_ADULT_DEFAULT
        }
        settingsRepository.setDistanceThreshold(newThreshold)
        refreshState()
    }

    private fun onThresholdSelect(threshold: Int) {
        settingsRepository.setDistanceThreshold(threshold)
        // При ручном выборе порога выключаем детский режим, если он был включён.
        if (settingsRepository.isChildMode()) {
            settingsRepository.setChildMode(false)
        }
        refreshState()
    }

    /** Тег текущего языка (`en`/`ru`/…) или `null`, если приложение следует системному. */
    private fun currentLanguageTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.language
    }

    /**
     * Возвращает подпись текущего языка: «Как в системе», если пользователь не выбирал язык,
     * иначе — эндоним выбранного языка.
     */
    private fun currentLanguageLabel(): String {
        val tag = currentLanguageTag() ?: return getString(R.string.language_system_default)
        val index = SUPPORTED_LANGUAGE_TAGS.indexOf(tag)
        return if (index >= 0) {
            resources.getStringArray(R.array.language_names)[index]
        } else {
            getString(R.string.language_system_default)
        }
    }

    /** Применяет выбранный язык (null = как в системе). Смена локали пересоздаёт активность. */
    private fun onSelectLanguage(tag: String?) {
        val locales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Применяет выбранную тему (recompose без пересоздания активности). */
    private fun onSelectTheme(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        refreshState()
    }

    private fun onBreakRemindersToggle(enabled: Boolean) {
        settingsRepository.setBreakRemindersEnabled(enabled)
        refreshState()
    }

    private fun onDarkRoomWarningToggle(enabled: Boolean) {
        settingsRepository.setDarkRoomWarningEnabled(enabled)
        refreshState()
    }

    private fun onPostureWarningToggle(enabled: Boolean) {
        settingsRepository.setPostureWarningEnabled(enabled)
        refreshState()
    }

    private fun onScheduleChange(schedule: MonitoringSchedule) {
        settingsRepository.setSchedule(schedule)
        refreshState()
    }

    /**
     * Пауза мониторинга на [minutes] минут. Сервис остаётся foreground и лишь отпускает камеру —
     * останавливать его нельзя: на Android 14+ camera-сервис не поднять обратно из фона.
     */
    private fun snoozeMonitoring(minutes: Int) {
        val intent = Intent(this, ForegroundMonitoringService::class.java).apply {
            action = ForegroundMonitoringService.ACTION_SNOOZE
            putExtra(ForegroundMonitoringService.EXTRA_SNOOZE_MINUTES, minutes)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun cancelSnooze() {
        val intent = Intent(this, ForegroundMonitoringService::class.java).apply {
            action = ForegroundMonitoringService.ACTION_CANCEL_SNOOZE
        }
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * Просит исключить приложение из оптимизации батареи, чтобы система не усыпляла фоновый сервис.
     * Показывает системный диалог; при недоступности открывает общий список настроек оптимизации.
     */
    private fun requestBatteryExemption() {
        if (isIgnoringBatteryOptimizations()) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        try {
            batteryExemptionLauncher.launch(direct)
        } catch (e: Exception) {
            try {
                batteryExemptionLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                // Настройки оптимизации недоступны на этом устройстве — ничего не делаем.
            }
        }
    }

    /**
     * Открывает настройки автозапуска (актуально для агрессивных OEM вроде MIUI, где без автозапуска
     * система выгружает сервис). Пытается перейти в известные OEM-экраны, иначе — в карточку приложения.
     */
    private fun openAutostartSettings() {
        val oemIntents = listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent().setClassName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        )
        for (intent in oemIntents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // пробуем следующий
            }
        }
        // Фолбэк: карточка приложения в системных настройках.
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            // недоступно — молча выходим
        }
    }

    /**
     * Показывает экран согласия на локальную обработку данных с камеры перед первым
     * использованием камеры. Согласие сохраняется и запрашивается только один раз.
     * По ТЗ (п. 6.4) должно предшествовать запросу разрешений.
     */
    private fun ensureConsent(onGranted: () -> Unit, onDenied: () -> Unit = {}) {
        if (settingsRepository.isPrivacyConsentGiven()) {
            onGranted()
            return
        }
        // Показываем iOS-алерт (Compose) — рендерится в setContent при consentRequest != null.
        consentRequest = ConsentRequest(onGranted, onDenied)
    }

    private fun handleStartMonitoring() {
        val requiredPermissions = mutableListOf(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            checkOverlayPermission()
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest)
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        } else {
            settingsRepository.setOverlayPermissionGranted(true)
            startMonitoringService()
        }
    }

    private fun startMonitoringService() {
        settingsRepository.setMonitoringEnabled(true)
        val intent = Intent(this, ForegroundMonitoringService::class.java)
        // Сервис становится foreground — корректный и безопасный API (в т.ч. при старте не из UI).
        ContextCompat.startForegroundService(this, intent)
        scheduleResumeWatch()
        refreshState()
    }

    private fun stopMonitoringService() {
        settingsRepository.setMonitoringEnabled(false)
        cancelResumeWatch()
        val intent = Intent(this, ForegroundMonitoringService::class.java)
        stopService(intent)
        refreshState()
    }

    /** Периодический «сторож» (WorkManager): напомнит возобновить, если OEM выгрузит сервис без перезагрузки. */
    private fun scheduleResumeWatch() {
        val request = PeriodicWorkRequestBuilder<ResumeWatchWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ResumeWatchWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun cancelResumeWatch() {
        WorkManager.getInstance(this).cancelUniqueWork(ResumeWatchWorker.WORK_NAME)
    }

    /** Сбрасывает мониторинг (например, при отказе в разрешениях) и обновляет UI. */
    private fun resetMonitoring() {
        settingsRepository.setMonitoringEnabled(false)
        cancelResumeWatch()
        refreshState()
    }
}
