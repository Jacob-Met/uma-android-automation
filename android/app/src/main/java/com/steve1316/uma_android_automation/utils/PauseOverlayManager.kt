package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.steve1316.automation_library.events.JSEvent
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import org.greenrobot.eventbus.EventBus

/**
 * Manages the floating pause/resume overlay while the bot is running.
 */
object PauseOverlayManager {
    private val TAG = "[${MainActivity.loggerTag}]PauseOverlayManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayButton: PauseOverlayButton? = null

    fun show(context: Context) {
        if (!BotPauseController.isFeatureEnabled()) {
            return
        }

        mainHandler.post {
            if (overlayButton != null) {
                overlayButton?.setPausedState(false)
                return@post
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayButton =
                PauseOverlayButton(context, windowManager).apply {
                    setOnClickListener { handleOverlayClick() }
                }
            MessageLog.i(TAG, "[PAUSE] Pause/resume overlay shown.")
        }
    }

    fun hide() {
        mainHandler.post {
            overlayButton?.cleanup()
            overlayButton = null
        }
    }

    fun setPausedState(isPaused: Boolean) {
        mainHandler.post {
            overlayButton?.setPausedState(isPaused)
        }
    }

    private fun handleOverlayClick() {
        val pausedNow =
            if (BotPauseController.isPaused()) {
                BotPauseController.resume()
            } else {
                BotPauseController.pause()
            }

        if (!pausedNow) {
            return
        }

        val message = if (BotPauseController.isPaused()) "Paused" else "Running"
        overlayButton?.setPausedState(BotPauseController.isPaused())
        EventBus.getDefault().post(JSEvent("BotPause", message, false))
    }
}
