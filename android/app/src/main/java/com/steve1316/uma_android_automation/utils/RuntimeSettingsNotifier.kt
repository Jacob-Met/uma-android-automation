package com.steve1316.uma_android_automation.utils

import android.util.Log
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game

/**
 * Applies persisted SQLite settings to a live bot session without requiring Home stop/start.
 * No-op when the bot thread is not running ([Game.activeInstance] is null).
 */
object RuntimeSettingsNotifier {
    private val TAG: String = "[${MainActivity.loggerTag}]RuntimeSettingsNotifier"

    fun notifySettingsUpdated() {
        val game = Game.activeInstance
        if (game == null) {
            Log.d(TAG, "Settings saved but no active bot session; next start will read fresh values.")
            return
        }
        try {
            game.reloadRuntimeSettings()
            MessageLog.i(TAG, "[SETTINGS] Applied updated settings to the running bot session.")
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] notifySettingsUpdated:: Failed to reload runtime settings: ${e.message}")
        }
    }
}
