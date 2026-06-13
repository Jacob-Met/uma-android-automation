package com.steve1316.uma_android_automation.utils

import android.content.Context
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.automation_library.utils.SQLiteSettingsManager
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.types.RaceGrade
import org.json.JSONObject

/**
 * Persists turn → race-key mappings per in-game agenda name for irregular training with user agenda.
 *
 * Autofill mode collects scheduled race keys during a learning run (status = learning) without enabling
 * agenda irregular training until the schedule is marked ready (career complete or manual).
 */
object AgendaIrregularSchedule {
    private val TAG: String = "[${MainActivity.loggerTag}]AgendaIrregularSchedule"

    private const val SETTINGS_CATEGORY = "scenarioOverrides"
    private const val SCHEDULES_KEY = "trackblazerAgendaIrregularSchedules"
    private const val AUTOFILL_KEY = "trackblazerAgendaIrregularAutofill"

    private const val STATUS_LEARNING = "learning"
    private const val STATUS_READY = "ready"

    private const val TABLE_RACES = "races"
    private const val RACES_COLUMN_KEY = "key"
    private const val RACES_COLUMN_GRADE = "grade"
    private const val RACES_COLUMN_TURN_NUMBER = "turn_number"
    private const val RACES_COLUMN_NAME = "name"

    data class AgendaSchedule(
        val status: String,
        val turns: Map<Int, String>,
        val locked: Boolean = false,
    )

    fun isAutofillEnabled(): Boolean =
        SettingsHelper.getBooleanSetting(SETTINGS_CATEGORY, AUTOFILL_KEY, true)

    fun getEffectiveAgendaName(): String {
        val customTitle = SettingsHelper.getStringSetting("racing", "customAgendaTitle")
        val selectedAgenda = SettingsHelper.getStringSetting("racing", "selectedUserAgenda")
        return customTitle.takeIf { it.isNotBlank() } ?: selectedAgenda
    }

    fun loadSchedule(agendaName: String): AgendaSchedule? {
        if (agendaName.isBlank()) return null
        val root = loadAllSchedulesRoot()
        if (!root.has(agendaName)) return null
        return parseScheduleEntry(root.getJSONObject(agendaName))
    }

    fun isScheduleReady(agendaName: String): Boolean {
        if (agendaName.isBlank()) return false
        val schedule = loadSchedule(agendaName) ?: return false
        if (isAutofillEnabled()) {
            return schedule.status == STATUS_READY && schedule.turns.isNotEmpty()
        }
        return schedule.turns.isNotEmpty()
    }

    fun getRaceKeyForTurn(agendaName: String, turnNumber: Int): String? =
        loadSchedule(agendaName)?.turns?.get(turnNumber)

    fun isAgendaMappedRaceTurn(agendaName: String, turnNumber: Int): Boolean =
        getRaceKeyForTurn(agendaName, turnNumber) != null

    /**
     * Whether autofill should record a completed agenda race for this turn.
     * Excludes climax/finale (73+) and pre-debut (1–11) unless the pre-debut setting is enabled.
     */
    fun isAgendaAutofillRecordableTurn(turnNumber: Int): Boolean {
        if (turnNumber > 72) {
            MessageLog.i(TAG, "[AGENDA IRREGULAR] Skipping autofill for climax/finale turn $turnNumber (training not available).")
            return false
        }
        val allowPreDebut =
            SettingsHelper.getBooleanSetting(SETTINGS_CATEGORY, "trackblazerEnableIrregularTrainingAgendaPreDebut", false)
        if (turnNumber < 12 && !allowPreDebut) {
            MessageLog.i(TAG, "[AGENDA IRREGULAR] Skipping autofill for pre-debut turn $turnNumber.")
            return false
        }
        return true
    }

    fun lookupGradeForRaceKey(context: Context, turnNumber: Int, raceKey: String): RaceGrade? {
        val settingsManager = SQLiteSettingsManager(context)
        if (!settingsManager.isAvailable()) {
            settingsManager.close()
            return null
        }

        return try {
            val database = settingsManager.readableDatabase ?: return null
            val cursor =
                database.query(
                    TABLE_RACES,
                    arrayOf(RACES_COLUMN_GRADE),
                    "$RACES_COLUMN_KEY = ? AND $RACES_COLUMN_TURN_NUMBER = ?",
                    arrayOf(raceKey, turnNumber.toString()),
                    null,
                    null,
                    null,
                )
            cursor.use {
                if (!it.moveToFirst()) {
                    null
                } else {
                    RaceGrade.fromName(it.getString(0))
                }
            }
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] lookupGradeForRaceKey:: ${e.message}")
            null
        } finally {
            settingsManager.close()
        }
    }

    fun lookupRaceKeyForCompletedRace(context: Context, turnNumber: Int, raceName: String?): String? {
        if (raceName.isNullOrBlank()) return null
        val settingsManager = SQLiteSettingsManager(context)
        if (!settingsManager.isAvailable()) {
            settingsManager.close()
            return null
        }

        return try {
            val database = settingsManager.readableDatabase ?: return null
            val cursor =
                database.query(
                    TABLE_RACES,
                    arrayOf(RACES_COLUMN_KEY),
                    "$RACES_COLUMN_TURN_NUMBER = ? AND $RACES_COLUMN_NAME = ?",
                    arrayOf(turnNumber.toString(), raceName),
                    null,
                    null,
                    null,
                )
            cursor.use {
                if (!it.moveToFirst()) {
                    null
                } else {
                    it.getString(0)
                }
            }
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] lookupRaceKeyForCompletedRace:: ${e.message}")
            null
        } finally {
            settingsManager.close()
        }
    }

    /**
     * Records a scheduled agenda race after completion while autofill is enabled.
     * Does not mark the schedule ready — that happens at career completion or manually in settings.
     */
    fun recordScheduledRace(context: Context, agendaName: String, turnNumber: Int, raceKey: String) {
        if (agendaName.isBlank() || raceKey.isBlank()) return
        if (!isAgendaAutofillRecordableTurn(turnNumber)) {
            return
        }

        val root = loadAllSchedulesRoot()
        val existing = if (root.has(agendaName)) parseScheduleEntry(root.getJSONObject(agendaName)) else null
        val turns = existing?.turns?.toMutableMap() ?: mutableMapOf()
        val previous = turns[turnNumber]
        if (previous == raceKey) {
            return
        }
        if (existing?.locked == true && turns.containsKey(turnNumber)) {
            MessageLog.i(
                TAG,
                "[AGENDA IRREGULAR] Schedule for \"$agendaName\" is locked; keeping existing turn $turnNumber mapping.",
            )
            return
        }

        turns[turnNumber] = raceKey
        val status =
            when {
                !isAutofillEnabled() -> STATUS_READY
                existing?.status == STATUS_READY -> STATUS_READY
                else -> STATUS_LEARNING
            }

        root.put(
            agendaName,
            scheduleToJson(
                AgendaSchedule(
                    status = status,
                    turns = turns,
                    locked = existing?.locked ?: false,
                ),
            ),
        )
        persistSchedules(context, root, agendaName)

        MessageLog.i(
            TAG,
            "[AGENDA IRREGULAR] Recorded turn $turnNumber → \"$raceKey\" for agenda \"$agendaName\" (status=$status).",
        )
    }

    /** Marks the agenda schedule ready so agenda irregular training may run on future careers. */
    fun markScheduleReady(context: Context, agendaName: String) {
        if (agendaName.isBlank()) return
        val root = loadAllSchedulesRoot()
        val existing = if (root.has(agendaName)) parseScheduleEntry(root.getJSONObject(agendaName)) else null
        if (existing == null || existing.turns.isEmpty()) {
            MessageLog.w(TAG, "[WARN] markScheduleReady:: No turns recorded for agenda \"$agendaName\". Skipping.")
            return
        }
        if (existing.status == STATUS_READY) {
            return
        }

        root.put(agendaName, scheduleToJson(existing.copy(status = STATUS_READY)))
        persistSchedules(context, root, agendaName)
        MessageLog.i(TAG, "[AGENDA IRREGULAR] Agenda \"$agendaName\" schedule marked ready (${existing.turns.size} mapped turn(s), recordable after pre-debut/climax filter).")
    }

    fun shouldEvaluateIrregularForAgendaTurn(
        context: Context,
        agendaName: String,
        turnNumber: Int,
        allowedGrades: Set<String>,
        allowPreOpOp: Boolean = false,
    ): Boolean {
        if (agendaName.isBlank() || allowedGrades.isEmpty()) return false
        if (!isScheduleReady(agendaName)) {
            return false
        }
        val raceKey = getRaceKeyForTurn(agendaName, turnNumber) ?: return false
        val grade = lookupGradeForRaceKey(context, turnNumber, raceKey) ?: return false
        val allowed = isAgendaIrregularGradeAllowed(grade, allowedGrades, allowPreOpOp)
        if (allowed) {
            MessageLog.i(
                TAG,
                "[AGENDA IRREGULAR] Turn $turnNumber agenda race \"$raceKey\" (${grade.name}) allows irregular training evaluation.",
            )
        } else {
            MessageLog.i(
                TAG,
                "[AGENDA IRREGULAR] Turn $turnNumber agenda race \"$raceKey\" (${grade.name}) is outside selected irregular grades.",
            )
        }
        return allowed
    }

    /** Whether [grade] qualifies for agenda irregular under selected G1/G2/G3 toggles and optional Pre-Op/OP support. */
    fun isAgendaIrregularGradeAllowed(
        grade: RaceGrade,
        allowedGrades: Set<String>,
        allowPreOpOp: Boolean,
    ): Boolean {
        if (grade.name in allowedGrades) {
            return true
        }
        return allowPreOpOp && (grade == RaceGrade.PRE_OP || grade == RaceGrade.OP)
    }

    private fun loadAllSchedulesRoot(): JSONObject {
        val raw = SettingsHelper.getStringSetting(SETTINGS_CATEGORY, SCHEDULES_KEY, "{}")
        return try {
            JSONObject(raw.ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun parseScheduleEntry(json: JSONObject): AgendaSchedule {
        val status = json.optString("status", STATUS_LEARNING)
        val turnsJson = json.optJSONObject("turns") ?: JSONObject()
        val turns = mutableMapOf<Int, String>()
        val keys = turnsJson.keys()
        while (keys.hasNext()) {
            val turnKey = keys.next()
            val turnNumber = turnKey.toIntOrNull() ?: continue
            val raceKey = turnsJson.optString(turnKey, "")
            if (raceKey.isNotBlank()) {
                turns[turnNumber] = raceKey
            }
        }
        val locked = json.optBoolean("locked", false)
        return AgendaSchedule(status = status, turns = turns, locked = locked)
    }

    private fun scheduleToJson(schedule: AgendaSchedule): JSONObject {
        val turnsJson = JSONObject()
        schedule.turns.toSortedMap().forEach { (turn, raceKey) ->
            turnsJson.put(turn.toString(), raceKey)
        }
        return JSONObject()
            .put("status", schedule.status)
            .put("turns", turnsJson)
            .put("locked", schedule.locked)
    }

    private fun persistSchedules(context: Context, root: JSONObject, agendaName: String? = null) {
        val settingsManager = SQLiteSettingsManager(context)
        if (!settingsManager.isAvailable()) {
            MessageLog.e(TAG, "[ERROR] persistSchedules:: SQLite settings database unavailable.")
            settingsManager.close()
            return
        }

        try {
            val writable = settingsManager.writableDatabase ?: return
            writable.beginTransaction()
            try {
                val insert =
                    writable.compileStatement(
                        "INSERT OR REPLACE INTO settings (category, key, value, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    )
                insert.clearBindings()
                insert.bindString(1, SETTINGS_CATEGORY)
                insert.bindString(2, SCHEDULES_KEY)
                insert.bindString(3, root.toString())
                insert.executeInsert()
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] persistSchedules:: ${e.message}")
        } finally {
            settingsManager.close()
        }

        agendaName?.let { StartModule.sendAgendaIrregularScheduleUpdatedEvent(it) }
    }
}
