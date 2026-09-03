package com.eyescare

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import androidx.core.content.ContextCompat

class OverlayManager(private val context: Context) {

    private var overlayView: View? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val alertPlayer = AlertPlayer(context)
    private val settingsRepository = SettingsRepository.getInstance(context)

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay() {
        ContextCompat.getMainExecutor(context).execute {
            if (overlayView == null) {
                // Создаем контекст с темой приложения
                val themedContext = ContextThemeWrapper(context, R.style.Theme_EyesCare)
                val layoutInflater = LayoutInflater.from(themedContext)

                overlayView = layoutInflater.inflate(R.layout.overlay_layout, null)

                // Сигнал берём в момент показа, а не при создании менеджера: настройку могли
                // поменять, пока сервис работает.
                alertPlayer.play(settingsRepository.getAlertSignal(), settingsRepository.getAlertSoundUri())

                overlayView?.findViewById<Button>(R.id.home_button)?.setOnClickListener {
                    val stopIntent = Intent(context, ForegroundMonitoringService::class.java).apply {
                        action = ForegroundMonitoringService.ACTION_STOP_SERVICE
                    }
                    context.startService(stopIntent)
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.CENTER

                try {
                    windowManager.addView(overlayView, params)
                    animateIn(overlayView)
                } catch (e: Exception) {
                    Log.e("OverlayManager", "Failed to add overlay view", e)
                }
            }
        }
    }

    /** Плавное появление в стиле iOS: фон — fade, контент — лёгкий scale-«поп». */
    private fun animateIn(root: View?) {
        root ?: return
        val content = root.findViewById<View>(R.id.overlay_content)
        root.alpha = 0f
        content?.scaleX = 0.9f
        content?.scaleY = 0.9f
        root.animate().alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        content?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(260)?.setInterpolator(OvershootInterpolator(1.4f))?.start()
    }

    fun hideOverlay() {
        alertPlayer.stop()
        ContextCompat.getMainExecutor(context).execute {
            overlayView?.let { view ->
                // Плавное исчезновение: fade + лёгкий scale-down, затем удаляем.
                val content = view.findViewById<View>(R.id.overlay_content)
                content?.animate()?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(150)?.start()
                view.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        try {
                            if (view.isAttachedToWindow) {
                                windowManager.removeView(view)
                            }
                        } catch (e: Exception) {
                            Log.e("OverlayManager", "Failed to remove overlay view", e)
                        }
                    }
                    .start()
                overlayView = null
            }
        }
    }
}