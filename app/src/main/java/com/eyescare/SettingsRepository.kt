// EncryptedSharedPreferences/MasterKey помечены @Deprecated в security-crypto 1.1.0: AndroidX закрыл
// Jetpack Security и рекомендует обычные SharedPreferences (данные и так защищены file-based
// encryption устройства). API остаётся рабочим и стабильным; отказ от прикладного шифрования —
// продуктовое решение, затрагивающее README и privacy policy, и пока не принято.
@file:Suppress("DEPRECATION")

package com.eyescare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

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
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    fun isMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    fun setDistanceThreshold(cm: Int) {
        prefs.edit().putInt(KEY_DISTANCE_THRESHOLD, cm).apply()
    }

    fun getDistanceThreshold(): Int {
        return prefs.getInt(KEY_DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_ADULT_DEFAULT)
    }

    // --- Permissions ---
    fun setOverlayPermissionGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION, granted).apply()
    }

    fun isOverlayPermissionGranted(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_PERMISSION, false)
    }

    // --- Theme ---
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    // --- Privacy Consent ---
    fun setPrivacyConsentGiven(given: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_CONSENT, given).apply()
    }

    fun isPrivacyConsentGiven(): Boolean {
        return prefs.getBoolean(KEY_PRIVACY_CONSENT, false)
    }

    // --- Child Mode & IPD ---
    fun setChildMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHILD_MODE, enabled).apply()
    }

    fun isChildMode(): Boolean {
        return prefs.getBoolean(KEY_CHILD_MODE, false)
    }

    fun setCalibratedIpd(ipd: Float) {
        prefs.edit().putFloat(KEY_CALIBRATED_IPD, ipd).apply()
    }

    fun getIpdMm(): Float {
        val calibrated = prefs.getFloat(KEY_CALIBRATED_IPD, 0.0f)
        if (calibrated > 0.0f) {
            return calibrated
        }
        return if (isChildMode()) IPD_CHILD_DEFAULT else IPD_ADULT_DEFAULT
    }

    fun clearCalibratedIpd() {
        prefs.edit().remove(KEY_CALIBRATED_IPD).apply()
    }

    // --- Camera Hardware Data ---
    fun saveCameraHardwareProperties(focalLength: Float, sensorWidth: Float) {
        prefs.edit().apply {
            putFloat(KEY_HW_FOCAL_LENGTH, focalLength)
            putFloat(KEY_HW_SENSOR_WIDTH, sensorWidth)
            apply()
        }
    }

    fun getHardwareFocalLength(): Float = prefs.getFloat(KEY_HW_FOCAL_LENGTH, 0.0f)
    fun getHardwareSensorWidth(): Float = prefs.getFloat(KEY_HW_SENSOR_WIDTH, 0.0f)

    // --- Onboarding ---
    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    // --- Break reminders (20-20-20) ---
    fun setBreakRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BREAK_REMINDERS, enabled).apply()
    }

    fun isBreakRemindersEnabled(): Boolean = prefs.getBoolean(KEY_BREAK_REMINDERS, true)

    // --- Weekly usage stats (сбрасываются в начале новой недели) ---
    private fun rolloverStatsIfNeeded() {
        val current = weekIdForEpochDay(java.time.LocalDate.now().toEpochDay())
        if (prefs.getLong(KEY_STATS_WEEK_ID, Long.MIN_VALUE) != current) {
            prefs.edit()
                .putLong(KEY_STATS_WEEK_ID, current)
                .putLong(KEY_STATS_MONITOR_SEC, 0)
                .putLong(KEY_STATS_TOOCLOSE_SEC, 0)
                .putInt(KEY_STATS_TOOCLOSE_EVENTS, 0)
                .apply()
        }
    }

    fun addMonitoringSeconds(seconds: Long) {
        if (seconds <= 0) return
        rolloverStatsIfNeeded()
        prefs.edit().putLong(KEY_STATS_MONITOR_SEC, prefs.getLong(KEY_STATS_MONITOR_SEC, 0) + seconds).apply()
    }

    fun addTooCloseSeconds(seconds: Long) {
        if (seconds <= 0) return
        rolloverStatsIfNeeded()
        prefs.edit().putLong(KEY_STATS_TOOCLOSE_SEC, prefs.getLong(KEY_STATS_TOOCLOSE_SEC, 0) + seconds).apply()
    }

    fun incrementTooCloseEvents() {
        rolloverStatsIfNeeded()
        prefs.edit().putInt(KEY_STATS_TOOCLOSE_EVENTS, prefs.getInt(KEY_STATS_TOOCLOSE_EVENTS, 0) + 1).apply()
    }

    fun getWeeklyStats(): WeeklyStats {
        rolloverStatsIfNeeded()
        return WeeklyStats(
            monitoringSeconds = prefs.getLong(KEY_STATS_MONITOR_SEC, 0),
            tooCloseSeconds = prefs.getLong(KEY_STATS_TOOCLOSE_SEC, 0),
            tooCloseEvents = prefs.getInt(KEY_STATS_TOOCLOSE_EVENTS, 0),
        )
    }

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
        private const val KEY_STATS_WEEK_ID = "stats_week_id"
        private const val KEY_STATS_MONITOR_SEC = "stats_monitor_sec"
        private const val KEY_STATS_TOOCLOSE_SEC = "stats_tooclose_sec"
        private const val KEY_STATS_TOOCLOSE_EVENTS = "stats_tooclose_events"

        private const val IPD_ADULT_DEFAULT = 63.0f
        private const val IPD_CHILD_DEFAULT = 54.0f
        const val DISTANCE_THRESHOLD_ADULT_DEFAULT = 30
        const val DISTANCE_THRESHOLD_CHILD_DEFAULT = 35 // Более реалистичный порог для детей
    }
}