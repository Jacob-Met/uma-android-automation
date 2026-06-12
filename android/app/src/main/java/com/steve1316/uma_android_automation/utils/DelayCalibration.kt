package com.steve1316.uma_android_automation.utils

import android.os.SystemClock
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads per-action delay overrides from advanced settings, falling back to caller-provided defaults.
 */
object ActionDelays {
    private val TAG = "[${MainActivity.loggerTag}]ActionDelays"

    fun get(key: String, fallback: Double): Double {
        return try {
            val jsonStr = SettingsHelper.getStringSetting("advanced", "perActionDelayOverrides")
            if (jsonStr.isBlank()) {
                return fallback
            }
            val obj = JSONObject(jsonStr)
            if (!obj.has(key)) {
                return fallback
            }
            obj.getDouble(key).coerceAtLeast(0.0)
        } catch (e: Exception) {
            fallback
        }
    }
}

/**
 * Emits structured timing lines when delay calibration is enabled in advanced settings.
 * React parses `[DELAY_CAL]` entries after a home-button stop.
 */
object DelayCalibration {
    private val TAG = "[${MainActivity.loggerTag}]DelayCalibration"
    private val detectionTimes = ConcurrentHashMap<String, Long>()

    fun enabled(): Boolean = SettingsHelper.getBooleanSetting("advanced", "enableDelayCalibration", false)

    fun markDetected(action: String) {
        if (!enabled()) return
        detectionTimes[action] = SystemClock.elapsedRealtime()
    }

    fun clearDetected(action: String) {
        detectionTimes.remove(action)
    }

    fun logExecution(
        action: String,
        plannedSec: Double,
        success: Boolean,
        retry: Boolean = false,
        failKind: String = "none",
    ) {
        if (!enabled()) return
        val detectMs =
            detectionTimes.remove(action)?.let { start ->
                (SystemClock.elapsedRealtime() - start).coerceAtLeast(0)
            } ?: -1L
        MessageLog.i(
            TAG,
            "[DELAY_CAL] action=$action success=$success plannedSec=$plannedSec detectToActionMs=$detectMs retry=$retry failKind=$failKind",
        )
    }

    fun logFailureFromPattern(action: String, plannedSec: Double, failKind: String = "too_fast") {
        logExecution(action, plannedSec, success = false, retry = false, failKind = failKind)
    }
}
