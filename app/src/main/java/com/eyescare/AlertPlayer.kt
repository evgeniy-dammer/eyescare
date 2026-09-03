package com.eyescare

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Звук и вибрация предупреждения «слишком близко».
 *
 * Отдельный класс, потому что [OverlayManager] занят окном: у сигнала своя ошибочная область
 * (недоступный рингтон, отсутствующий вибромотор) и своё время жизни — звук нужно уметь оборвать,
 * когда баннер убрали.
 */
class AlertPlayer(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Разрешение рингтона — обращение к MediaStore, а сигнал срабатывает на каждом сближении.
    // Кэшируем по URI, чтобы не платить за это каждый раз; ключ храним, чтобы поймать смену звука.
    private var ringtone: Ringtone? = null
    private var ringtoneUri: Uri? = null

    /**
     * Подаёт сигнал [signal]. [soundUri] — выбранный пользователем звук; `null` означает системный
     * звук уведомления по умолчанию.
     */
    fun play(signal: AlertSignal, soundUri: Uri?) {
        if (signal.vibrates) vibrate()
        if (signal.plays) playSound(soundUri)
    }

    /** Обрывает звук: баннер убрали — сигналить больше не о чем. */
    fun stop() {
        try {
            ringtone?.takeIf { it.isPlaying }?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop the alert sound", e)
        }
    }

    private fun vibrate() {
        try {
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    /**
     * Звук проигрывается как **уведомление**, а не как музыка: под этим usage система приглушает
     * чужое воспроизведение, а не останавливает его — предупреждение не должно обрывать фильм,
     * ради которого человек и придвинулся к экрану.
     *
     * Выбранный когда-то звук может стать недоступен (файл удалён, карта вынута, у приложения нет
     * доступа к чужой медиатеке). Тогда откатываемся на системный звук уведомления: молчание в
     * ответ на выбранный пользователем сигнал выглядело бы как сломанная фича.
     */
    private fun playSound(soundUri: Uri?) {
        val uri = soundUri ?: defaultUri() ?: return
        try {
            if (!obtainRingtone(uri)) {
                if (soundUri == null) return // системный звук и так не разрешился — пробовать нечего
                val fallback = defaultUri() ?: return
                if (!obtainRingtone(fallback)) return
            }
            ringtone?.let {
                if (it.isPlaying) it.stop() // повторное сближение начинает сигнал заново
                it.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play the alert sound", e)
        }
    }

    /** Готовит (и кэширует) рингтон по [uri]. `false` — звук недоступен. */
    private fun obtainRingtone(uri: Uri): Boolean {
        if (ringtone != null && ringtoneUri == uri) return true
        stop() // звук сменили на ходу — прежний не должен доигрывать
        val resolved = RingtoneManager.getRingtone(context, uri) ?: return false
        resolved.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone = resolved
        ringtoneUri = uri
        return true
    }

    private fun defaultUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private companion object {
        const val TAG = "AlertPlayer"
        const val VIBRATION_MS = 150L
    }
}
