package com.steve1316.uma_android_automation.utils

import android.content.Context
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.automation_library.utils.SQLiteSettingsManager
import com.steve1316.uma_android_automation.MainActivity
import org.json.JSONObject

/**
 * Applies per-Uma Musume setting prefabs when OCR detects the trainee name.
 * Training event overrides and system timing settings are never overwritten.
 */
object UmaPresetApplier {
    private val TAG: String = "[${MainActivity.loggerTag}]UmaPresetApplier"

    private val PRESET_CATEGORIES =
        listOf(
            "training",
            "trainingStatTarget",
            "racing",
            "skills",
            "scenarioOverrides",
        )

    /** Kotlin-owned racing blobs that must not be copied from preset JSON. */
    private val RACING_DB_OWNED_KEYS =
        setOf(
            "racesData",
            "epithetsData",
            "characterPresetsData",
            "racingPlanData",
        )

    private var lastAppliedNormalizedName: String? = null

    fun isAutoLoadEnabled(): Boolean = SettingsHelper.getBooleanSetting("misc", "enableAutoLoadUmaPreset", false)

    fun normalizeUmaName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * Attempts to load and apply a preset for [detectedName].
     * @return The matched preset's display name, or null when skipped or not found.
     */
    fun tryApplyForDetectedUma(context: Context, detectedName: String): String? {
        if (!isAutoLoadEnabled()) {
            return null
        }

        val normalized = normalizeUmaName(detectedName)
        if (normalized.isEmpty()) {
            return null
        }

        if (normalized == lastAppliedNormalizedName) {
            return null
        }

        val settingsManager = SQLiteSettingsManager(context)
        if (!settingsManager.isAvailable()) {
            MessageLog.w(TAG, "[WARN] tryApplyForDetectedUma:: SQLite settings database unavailable.")
            settingsManager.close()
            return null
        }

        return try {
            val database = settingsManager.readableDatabase
            if (database == null) {
                MessageLog.w(TAG, "[WARN] tryApplyForDetectedUma:: Readable database unavailable.")
                null
            } else {
                val cursor =
                    database.rawQuery(
                        "SELECT uma_name, settings FROM uma_presets WHERE normalized_name = ? LIMIT 1",
                        arrayOf(normalized),
                    )

                cursor.use {
                    if (!it.moveToFirst()) {
                        MessageLog.i(TAG, "[UMA PRESET] No preset linked to detected Uma \"$detectedName\".")
                        return null
                    }

                    val presetName = it.getString(0)
                    val settingsJson = it.getString(1)
                    val appliedCount = applyPresetJson(settingsManager, settingsJson)
                    lastAppliedNormalizedName = normalized
                    MessageLog.i(
                        TAG,
                        "[UMA PRESET] Applied preset \"$presetName\" for detected Uma \"$detectedName\" ($appliedCount setting(s)). " +
                            "Training event overrides unchanged. Restart the bot for all training fields to refresh if this run already started.",
                    )
                    presetName
                }
            }
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] tryApplyForDetectedUma:: ${e.message}")
            null
        } finally {
            settingsManager.close()
        }
    }

    fun resetSession() {
        lastAppliedNormalizedName = null
    }

    private fun applyPresetJson(settingsManager: SQLiteSettingsManager, settingsJson: String): Int {
        val root = JSONObject(settingsJson)
        val writable = settingsManager.writableDatabase ?: return 0
        var count = 0

        writable.beginTransaction()
        try {
            val insert =
                writable.compileStatement(
                    "INSERT OR REPLACE INTO settings (category, key, value, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                )
            for (category in PRESET_CATEGORIES) {
                if (!root.has(category)) continue
                val categoryObject = root.getJSONObject(category)
                val keys = categoryObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (category == "racing" && key in RACING_DB_OWNED_KEYS) continue
                    val serialized = serializePresetValue(categoryObject.get(key))
                    insert.clearBindings()
                    insert.bindString(1, category)
                    insert.bindString(2, key)
                    insert.bindString(3, serialized)
                    insert.executeInsert()
                    count++
                }
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }

        return count
    }

    private fun serializePresetValue(value: Any): String =
        when (value) {
            is Boolean -> if (value) "true" else "false"
            is Int, is Long, is Double -> value.toString()
            is JSONObject, is org.json.JSONArray -> value.toString()
            else -> value.toString()
        }
}
