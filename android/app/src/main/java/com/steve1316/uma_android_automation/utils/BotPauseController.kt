package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity

/**
 * Cooperative pause/resume for manual mid-run intervention.
 *
 * When enabled via settings, pause keeps the bot thread alive while blocking in [waitIfPaused].
 * Resume skips agenda loading and the skill point check for exactly one main-screen pass.
 */
object BotPauseController {
    private val TAG: String = "[${MainActivity.loggerTag}]BotPauseController"

    @Volatile
    private var isPaused: Boolean = false

    /** When true, the next [handleMainScreen] pass skips agenda load and skill point check. */
    @Volatile
    private var skipPostResumeChecksOnce: Boolean = false

    fun isFeatureEnabled(): Boolean = SettingsHelper.getBooleanSetting("debug", "enablePauseResume", false)

    fun isPaused(): Boolean = isPaused

    fun pause(): Boolean {
        if (!isFeatureEnabled()) {
            MessageLog.w(TAG, "[WARN] pause:: enablePauseResume is disabled in settings.")
            return false
        }
        if (!BotService.isRunning) {
            MessageLog.w(TAG, "[WARN] pause:: Bot is not running.")
            return false
        }
        if (isPaused) {
            return true
        }
        isPaused = true
        PauseOverlayManager.setPausedState(true)
        MessageLog.i(TAG, "[PAUSE] Bot paused. Manual intervention allowed. Tap Resume to continue (agenda load and skill point check skipped once).")
        return true
    }

    fun resume(): Boolean {
        if (!isFeatureEnabled()) {
            MessageLog.w(TAG, "[WARN] resume:: enablePauseResume is disabled in settings.")
            return false
        }
        if (!BotService.isRunning) {
            MessageLog.w(TAG, "[WARN] resume:: Bot is not running.")
            return false
        }
        if (!isPaused) {
            return false
        }
        isPaused = false
        skipPostResumeChecksOnce = true
        PauseOverlayManager.setPausedState(false)
        MessageLog.i(TAG, "[PAUSE] Bot resumed. Skipping agenda load and skill point check for this turn.")
        return true
    }

    /** Returns true once, then clears the flag. Only active when pause/resume feature is enabled. */
    fun consumeSkipPostResumeChecksOnce(): Boolean {
        if (!isFeatureEnabled() || !skipPostResumeChecksOnce) {
            return false
        }
        skipPostResumeChecksOnce = false
        return true
    }

    fun reset() {
        isPaused = false
        skipPostResumeChecksOnce = false
        PauseOverlayManager.hide()
    }

    /** Blocks while paused. Throws if the bot is stopped. */
    fun waitIfPaused() {
        while (isPaused) {
            if (!BotService.isRunning) {
                throw InterruptedException()
            }
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            }
        }
    }
}
