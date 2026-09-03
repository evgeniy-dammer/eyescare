// EncryptedSharedPreferences/MasterKey помечены @Deprecated в security-crypto 1.1.0: AndroidX закрыл
// Jetpack Security и рекомендует обычные SharedPreferences (данные и так защищены file-based
// encryption устройства). API остаётся рабочим и стабильным; отказ от прикладного шифрования —
// продуктовое решение, затрагивающее README и privacy policy, и пока не принято.
@file:Suppress("DEPRECATION")

package com.eyescare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

    /** Защищает read-modify-write истории использования (см. [mutateHistory]). */
    private val statsLock = Any()

    /**
     * Создаёт зашифрованное хранилище. Если файл или мастер-ключ повреждены
     * (например, после restore из бэкапа на другом устройстве или ротации ключей),
     * сбрасывает хранилище и пересоздаёт его, чтобы приложение не падало при старте.
     */
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            buildEncryptedPrefs(context)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Encrypted prefs corrupted, recreating", e)
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            buildEncryptedPrefs(context)
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Monitoring State & Threshold ---
    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MONITORING_ENABLED, enabled) }
    }

    fun isMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    fun setDistanceThreshold(cm: Int) {
        prefs.edit { putInt(KEY_DISTANCE_THRESHOLD, cm) }
    }

    fun getDistanceThreshold(): Int {
        return prefs.getInt(KEY_DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_ADULT_DEFAULT)
    }

    // --- Permissions ---
    fun setOverlayPermissionGranted(granted: Boolean) {
        prefs.edit { putBoolean(KEY_OVERLAY_PERMISSION, granted) }
    }

    fun isOverlayPermissionGranted(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_PERMISSION, false)
    }

    // --- Theme ---
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    // --- Privacy Consent ---
    fun setPrivacyConsentGiven(given: Boolean) {
        prefs.edit { putBoolean(KEY_PRIVACY_CONSENT, given) }
    }

    fun isPrivacyConsentGiven(): Boolean {
        return prefs.getBoolean(KEY_PRIVACY_CONSENT, false)
    }

    // --- Child Mode & IPD ---
    fun setChildMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CHILD_MODE, enabled) }
    }

    fun isChildMode(): Boolean {
        return prefs.getBoolean(KEY_CHILD_MODE, false)
    }

    fun setCalibratedIpd(ipd: Float) {
        prefs.edit { putFloat(KEY_CALIBRATED_IPD, ipd) }
    }

    fun getIpdMm(): Float {
        val calibrated = prefs.getFloat(KEY_CALIBRATED_IPD, 0.0f)
        if (calibrated > 0.0f) {
            return calibrated
        }
        return if (isChildMode()) IPD_CHILD_DEFAULT else IPD_ADULT_DEFAULT
    }

    fun clearCalibratedIpd() {
        prefs.edit { remove(KEY_CALIBRATED_IPD) }
    }

    // --- Camera Hardware Data ---
    fun saveCameraHardwareProperties(focalLength: Float, sensorWidth: Float) {
        prefs.edit {
            putFloat(KEY_HW_FOCAL_LENGTH, focalLength)
            putFloat(KEY_HW_SENSOR_WIDTH, sensorWidth)
        }
    }

    fun getHardwareFocalLength(): Float = prefs.getFloat(KEY_HW_FOCAL_LENGTH, 0.0f)
    fun getHardwareSensorWidth(): Float = prefs.getFloat(KEY_HW_SENSOR_WIDTH, 0.0f)

    // --- Onboarding ---
    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_DONE, done) }
    }

    // --- Break reminders (20-20-20) ---
    fun setBreakRemindersEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BREAK_REMINDERS, enabled) }
    }

    fun isBreakRemindersEnabled(): Boolean = prefs.getBoolean(KEY_BREAK_REMINDERS, true)

    // --- Предупреждение о тёмной комнате (датчик освещённости) ---
    fun setDarkRoomWarningEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_ROOM_WARNING, enabled) }
    }

    fun isDarkRoomWarningEnabled(): Boolean = prefs.getBoolean(KEY_DARK_ROOM_WARNING, true)

    // --- Напоминание об осанке (наклон головы) ---
    fun setPostureWarningEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_POSTURE_WARNING, enabled) }
    }

    /**
     * Включено по умолчанию с 2026-08-31: знак `headEulerAngleX` подтверждён замером на устройстве
     * (Redmi 2201117TG) — опущенный подбородок даёт −65°, спокойная посадка −6…−15°, то есть «плюс —
     * лицо смотрит вверх». До проверки фича стояла выключенной именно из-за этого допущения.
     */
    fun isPostureWarningEnabled(): Boolean = prefs.getBoolean(KEY_POSTURE_WARNING, true)

    // --- Расписание мониторинга ---
    fun setSchedule(schedule: MonitoringSchedule) {
        prefs.edit {
            putBoolean(KEY_SCHEDULE_ENABLED, schedule.enabled)
            // Дни храним строкой «1,2,3» — читаемо в отладке и не зависит от порядка множества.
            putString(KEY_SCHEDULE_DAYS, schedule.days.sorted().joinToString(","))
            putInt(KEY_SCHEDULE_START, schedule.startMinuteOfDay)
            putInt(KEY_SCHEDULE_END, schedule.endMinuteOfDay)
        }
    }

    fun getSchedule(): MonitoringSchedule {
        val raw = prefs.getString(KEY_SCHEDULE_DAYS, null)
        val days = if (raw == null) {
            MonitoringSchedule.DEFAULT_DAYS
        } else {
            // Повреждённое значение не должно ронять старт: непарсящиеся элементы просто отбрасываем.
            raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
        }
        return MonitoringSchedule(
            enabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
            days = days,
            startMinuteOfDay = prefs.getInt(KEY_SCHEDULE_START, MonitoringSchedule.DEFAULT_START),
            endMinuteOfDay = prefs.getInt(KEY_SCHEDULE_END, MonitoringSchedule.DEFAULT_END),
        )
    }

    // --- Сигнал предупреждения «слишком близко» (см. AlertSignal) ---

    fun setAlertSignal(signal: AlertSignal) {
        prefs.edit { putString(KEY_ALERT_SIGNAL, signal.name) }
    }

    fun getAlertSignal(): AlertSignal = AlertSignal.fromName(prefs.getString(KEY_ALERT_SIGNAL, null))

    /** Выбранный звук предупреждения; `null` — системный звук уведомления по умолчанию. */
    fun setAlertSoundUri(uri: android.net.Uri?) {
        prefs.edit { putString(KEY_ALERT_SOUND, uri?.toString()) }
    }

    fun getAlertSoundUri(): android.net.Uri? =
        prefs.getString(KEY_ALERT_SOUND, null)?.takeIf { it.isNotBlank() }?.toUri()

    // --- Usage stats: ряд по дням (см. StatsHistory) ---

    /**
     * Читает историю, применяет [mutate] и пишет обратно под замком.
     *
     * Замок нужен потому, что вся история лежит в одном значении: параллельные read-modify-write из
     * потока камеры и из тика статистики иначе потеряли бы одно из обновлений целиком, а не одно
     * поле. Записи редкие (периодический сброс и переходы «слишком близко»), борьбы за замок нет.
     */
    private fun mutateHistory(mutate: (StatsHistory, Long) -> StatsHistory) = synchronized(statsLock) {
        val today = java.time.LocalDate.now().toEpochDay()
        val updated = mutate(StatsHistory.parse(prefs.getString(KEY_STATS_HISTORY, null)), today)
        prefs.edit { putString(KEY_STATS_HISTORY, updated.serialize()) }
    }

    /**
     * Дописывает накопленное за отрезок к сегодняшнему дню.
     *
     * Отрезок, начавшийся вчера вечером, целиком попадёт в сегодня — сброс идёт раз в несколько
     * минут, так что «протечь» через полночь может лишь этот интервал; делить отрезок по дням ради
     * такой точности не стоит усложнения.
     */
    fun addStats(
        monitoringSeconds: Long = 0,
        tooCloseSeconds: Long = 0,
        tooCloseEvents: Int = 0,
        distanceSumCm: Long = 0,
        distanceSamples: Long = 0,
    ) {
        if (monitoringSeconds <= 0 && tooCloseSeconds <= 0 && tooCloseEvents <= 0 && distanceSamples <= 0) return
        mutateHistory { history, today ->
            history.plus(
                DailyStats(
                    epochDay = today,
                    monitoringSeconds = monitoringSeconds.coerceAtLeast(0),
                    tooCloseSeconds = tooCloseSeconds.coerceAtLeast(0),
                    tooCloseEvents = tooCloseEvents.coerceAtLeast(0),
                    distanceSumCm = distanceSumCm.coerceAtLeast(0),
                    distanceSamples = distanceSamples.coerceAtLeast(0),
                ),
                todayEpochDay = today,
            )
        }
    }

    /** Сводка за последние [StatsHistory.WEEK_DAYS] дней. */
    fun getWeeklyStats(): WeeklyStats =
        statsHistory().totalsForLastDays(java.time.LocalDate.now().toEpochDay(), StatsHistory.WEEK_DAYS)

    /** Последние [count] дней по возрастанию, с пустыми днями на местах пропусков — для графика. */
    fun getDailyHistory(count: Int = StatsHistory.WEEK_DAYS): List<DailyStats> =
        statsHistory().lastDays(java.time.LocalDate.now().toEpochDay(), count)

    /** Серия подряд идущих дней без заметного «слишком близко» (см. [StatsHistory.goodDayStreak]). */
    fun getGoodDayStreak(): Int = statsHistory().goodDayStreak(
        todayEpochDay = java.time.LocalDate.now().toEpochDay(),
        maxTooCloseShare = StatsHistory.GOOD_DAY_MAX_TOO_CLOSE_SHARE,
    )

    private fun statsHistory(): StatsHistory =
        synchronized(statsLock) { StatsHistory.parse(prefs.getString(KEY_STATS_HISTORY, null)) }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        /** Единый экземпляр на процесс: избегаем повторной инициализации EncryptedSharedPreferences. */
        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }

        // Разумный диапазон межзрачкового расстояния (мм) для валидации ручного ввода IPD.
        const val MIN_IPD_MM = 40
        const val MAX_IPD_MM = 90

        // Доступные пороги дистанции (см) для выбора на главном экране.
        val DISTANCE_THRESHOLD_OPTIONS = listOf(25, 30, 35, 40, 45, 50)

        private const val PREFS_FILE_NAME = "eyescare_secure_prefs"

        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_DISTANCE_THRESHOLD = "distance_threshold"
        private const val KEY_CHILD_MODE = "child_mode"
        private const val KEY_OVERLAY_PERMISSION = "overlay_permission"
        private const val KEY_PRIVACY_CONSENT = "privacy_consent"
        private const val KEY_THEME_MODE = "theme_mode"

        private const val KEY_CALIBRATED_IPD = "calibrated_ipd"
        private const val KEY_HW_FOCAL_LENGTH = "hw_focal_length"
        private const val KEY_HW_SENSOR_WIDTH = "hw_sensor_width"

        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_BREAK_REMINDERS = "break_reminders"
        private const val KEY_DARK_ROOM_WARNING = "dark_room_warning"
        private const val KEY_POSTURE_WARNING = "posture_warning"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_DAYS = "schedule_days"
        private const val KEY_SCHEDULE_START = "schedule_start"
        private const val KEY_SCHEDULE_END = "schedule_end"
        // Ряд по дням (StatsHistory). Пришёл на смену недельным счётчикам stats_week_id/
        // stats_monitor_sec/stats_tooclose_sec/stats_tooclose_events: перенести их было некуда —
        // недельная сумма не раскладывается обратно по дням, поэтому история начинается с нуля.
        private const val KEY_STATS_HISTORY = "stats_history"
        private const val KEY_ALERT_SIGNAL = "alert_signal"
        private const val KEY_ALERT_SOUND = "alert_sound"

        private const val IPD_ADULT_DEFAULT = 63.0f
        private const val IPD_CHILD_DEFAULT = 54.0f
        const val DISTANCE_THRESHOLD_ADULT_DEFAULT = 30
        const val DISTANCE_THRESHOLD_CHILD_DEFAULT = 35 // Более реалистичный порог для детей
    }
}