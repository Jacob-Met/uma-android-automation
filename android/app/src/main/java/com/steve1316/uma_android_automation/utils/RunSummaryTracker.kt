package com.steve1316.uma_android_automation.utils

import android.content.Context
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.types.StatName
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/** Accumulates per-run metadata, training counts, and stat gains for optional CSV export at career end. */
object RunSummaryTracker {
    private val TAG: String = "[${MainActivity.loggerTag}]RunSummaryTracker"

    data class RaceGainEntry(
        val turn: Int,
        val label: String,
        val grade: String?,
        val won: Boolean?,
        val gains: Map<StatName, Int>,
        val note: String,
    )

    private data class PendingDiffAttribution(
        val turn: Int,
        val label: String,
        val grade: String?,
        var won: Boolean?,
        val statsBefore: Map<StatName, Int>,
    )

    private var scenario: String = ""
    private var umaName: String = ""
    private val supportNames: MutableSet<String> = linkedSetOf()
    private val trainingClickCounts: MutableMap<StatName, Int> = StatName.entries.associateWith { 0 }.toMutableMap()
    private val trainingGainTotals: MutableMap<StatName, Int> = StatName.entries.associateWith { 0 }.toMutableMap()
    private val raceGainEntries: CopyOnWriteArrayList<RaceGainEntry> = CopyOnWriteArrayList()
    private var pendingDiffAttribution: PendingDiffAttribution? = null

    fun isExportEnabled(): Boolean = SettingsHelper.getBooleanSetting("debug", "enableRunSummaryExport", false)

    fun reset(scenarioName: String) {
        scenario = scenarioName
        umaName = ""
        supportNames.clear()
        StatName.entries.forEach { stat ->
            trainingClickCounts[stat] = 0
            trainingGainTotals[stat] = 0
        }
        raceGainEntries.clear()
        pendingDiffAttribution = null
    }

    fun setUmaName(name: String) {
        if (name.isNotBlank()) {
            umaName = name.trim()
        }
    }

    fun recordSupportName(name: String?) {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            supportNames.add(trimmed)
        }
    }

    /** Records a training execution and accumulates OCR stat gains (no per-turn rows). */
    fun recordTrainingExecution(training: StatName, statGains: Map<StatName, Int>) {
        if (!isExportEnabled()) {
            return
        }

        trainingClickCounts[training] = (trainingClickCounts[training] ?: 0) + 1
        statGains.filterValues { it > 0 }.forEach { (stat, gain) ->
            trainingGainTotals[stat] = (trainingGainTotals[stat] ?: 0) + gain
        }
    }

    /**
     * Starts hub-stat diff tracking for a race. Call before the race; call [applyStatSnapshot] on the next hub stat read.
     */
    fun beginRaceAttribution(
        turn: Int,
        label: String,
        statsBefore: Map<StatName, Int>,
        grade: String? = null,
    ) {
        if (!isExportEnabled()) {
            return
        }
        // Climax/finale races (73+) are mandatory with no training alternative — exclude from career race totals.
        if (turn > 72) {
            return
        }

        pendingDiffAttribution =
            PendingDiffAttribution(
                turn = turn,
                label = label.ifBlank { "unknown" },
                grade = grade,
                won = null,
                statsBefore = snapshotStats(statsBefore),
            )
    }

    /** Sets win/loss for a pending race attribution (stats are applied on the next hub snapshot). */
    fun completeRaceAttribution(won: Boolean) {
        pendingDiffAttribution?.won = won
    }

    /** Applies a hub stat snapshot and, if a race attribution is pending, records measured stat deltas. */
    fun applyStatSnapshot(statsAfter: Map<StatName, Int>) {
        val pending = pendingDiffAttribution ?: return
        pendingDiffAttribution = null

        val after = snapshotStats(statsAfter)
        val delta = computeDelta(pending.statsBefore, after)

        val note =
            if (pending.won == false) {
                "Measured hub delta after loss (may be 0 or include unrelated changes)"
            } else {
                "Measured hub delta after race"
            }

        raceGainEntries.add(
            RaceGainEntry(
                turn = pending.turn,
                label = pending.label,
                grade = pending.grade,
                won = pending.won,
                gains = delta.filterValues { it != 0 },
                note = note,
            ),
        )
    }

    fun writeExport(context: Context): String? {
        if (!isExportEnabled()) {
            return null
        }

        return try {
            val exportDir = File(context.getExternalFilesDir(null), "run_summaries")
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                MessageLog.w(TAG, "[WARN] writeExport:: Could not create run_summaries directory.")
                return null
            }

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val safeUma = umaName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "unknown_uma" }
            val summaryFile = File(exportDir, "run_${safeUma}_$timestamp.csv")

            val raceTotals = totalsForRaceGains()

            summaryFile.bufferedWriter().use { writer ->
                writer.appendLine("section,field,value")
                writer.appendLine("meta,scenario,\"${csvEscape(scenario)}\"")
                writer.appendLine("meta,uma_musume,\"${csvEscape(umaName.ifBlank { "unknown" })}\"")
                writer.appendLine(
                    "meta,supports_note,\"${csvEscape("Support names detected on training relationship bars during the run (not guaranteed to match full deck loadout).")}\"",
                )
                if (supportNames.isEmpty()) {
                    writer.appendLine("meta,supports_detected,")
                } else {
                    writer.appendLine("meta,supports_detected,\"${csvEscape(supportNames.joinToString("; "))}\"")
                }
                writer.appendLine("meta,exported_at,\"${csvEscape(LocalDateTime.now().toString())}\"")
                writer.appendLine("meta,training_clicks_speed,${trainingClickCounts[StatName.SPEED] ?: 0}")
                writer.appendLine("meta,training_clicks_stamina,${trainingClickCounts[StatName.STAMINA] ?: 0}")
                writer.appendLine("meta,training_clicks_power,${trainingClickCounts[StatName.POWER] ?: 0}")
                writer.appendLine("meta,training_clicks_guts,${trainingClickCounts[StatName.GUTS] ?: 0}")
                writer.appendLine("meta,training_clicks_wit,${trainingClickCounts[StatName.WIT] ?: 0}")
                writer.appendLine("meta,total_training_speed,${trainingGainTotals[StatName.SPEED] ?: 0}")
                writer.appendLine("meta,total_training_stamina,${trainingGainTotals[StatName.STAMINA] ?: 0}")
                writer.appendLine("meta,total_training_power,${trainingGainTotals[StatName.POWER] ?: 0}")
                writer.appendLine("meta,total_training_guts,${trainingGainTotals[StatName.GUTS] ?: 0}")
                writer.appendLine("meta,total_training_wit,${trainingGainTotals[StatName.WIT] ?: 0}")
                writer.appendLine("meta,total_race_speed,${raceTotals[StatName.SPEED] ?: 0}")
                writer.appendLine("meta,total_race_stamina,${raceTotals[StatName.STAMINA] ?: 0}")
                writer.appendLine("meta,total_race_power,${raceTotals[StatName.POWER] ?: 0}")
                writer.appendLine("meta,total_race_guts,${raceTotals[StatName.GUTS] ?: 0}")
                writer.appendLine("meta,total_race_wit,${raceTotals[StatName.WIT] ?: 0}")
                writer.appendLine("meta,race_gain_entries,${raceGainEntries.size}")
                writer.appendLine("")
                writer.appendLine("turn,race_name,grade,won,speed_gain,stamina_gain,power_gain,guts_gain,wit_gain,note")
                for (entry in raceGainEntries) {
                    writer.appendLine(formatRaceGainRow(entry))
                }
            }

            MessageLog.i(TAG, "[RUN_SUMMARY] Exported run summary CSV to ${summaryFile.absolutePath}")
            summaryFile.absolutePath
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] writeExport:: Failed to write run summary CSV: ${e.message}")
            null
        }
    }

    private fun snapshotStats(stats: Map<StatName, Int>): Map<StatName, Int> = StatName.entries.associateWith { stats[it] ?: 0 }

    private fun computeDelta(before: Map<StatName, Int>, after: Map<StatName, Int>): Map<StatName, Int> =
        StatName.entries.associateWith { stat -> (after[stat] ?: 0) - (before[stat] ?: 0) }

    private fun totalsForRaceGains(): Map<StatName, Int> {
        val totals = StatName.entries.associateWith { 0 }.toMutableMap()
        raceGainEntries.forEach { entry ->
            entry.gains.forEach { (stat, gain) ->
                totals[stat] = (totals[stat] ?: 0) + gain
            }
        }
        return totals
    }

    private fun formatRaceGainRow(entry: RaceGainEntry): String {
        val wonText = entry.won?.let { if (it) "true" else "false" } ?: ""
        return listOf(
            entry.turn.toString(),
            "\"${csvEscape(entry.label)}\"",
            entry.grade?.let { "\"${csvEscape(it)}\"" } ?: "",
            wonText,
            (entry.gains[StatName.SPEED] ?: 0).toString(),
            (entry.gains[StatName.STAMINA] ?: 0).toString(),
            (entry.gains[StatName.POWER] ?: 0).toString(),
            (entry.gains[StatName.GUTS] ?: 0).toString(),
            (entry.gains[StatName.WIT] ?: 0).toString(),
            "\"${csvEscape(entry.note)}\"",
        ).joinToString(",")
    }

    private fun csvEscape(value: String): String = value.replace("\"", "\"\"")
}
