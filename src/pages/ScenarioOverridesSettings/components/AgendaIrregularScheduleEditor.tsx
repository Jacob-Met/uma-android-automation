import { useCallback, useContext, useEffect, useMemo, useRef } from "react"
import { DeviceEventEmitter, ScrollView, Text, TouchableOpacity, View, StyleSheet } from "react-native"
import { useFocusEffect } from "@react-navigation/native"
import racesData from "../../../data/races.json"
import { GRADE_COLORS, RaceEntry, turnDateLabel, YEAR_LABELS } from "../../../lib/solver/constants"
import {
    AgendaScheduleEntry,
    countRecordableAgendaTurns,
    ensureScheduleForAgendaSlot,
    getAgendaScheduleKey,
    getLegacyAgendaScheduleKey,
    isAgendaAutofillRecordableTurn,
    parseAgendaSchedules,
} from "../../../lib/agendaIrregularSchedule"
import { ScenarioOverridesContext, RacingContext, defaultSettings } from "../../../context/BotStateContext"
import { useSettings } from "../../../context/SettingsContext"
import { useTheme } from "../../../context/ThemeContext"
import CustomCheckbox from "../../../components/CustomCheckbox"
import CustomButton from "../../../components/CustomButton"
import InfoContainer from "../../../components/InfoContainer"
import WarningContainer from "../../../components/WarningContainer"

type RaceWithKey = RaceEntry & { key: string }

interface Props {
    updateOverrideSetting: (key: string, value: unknown) => void
}

/**
 * Manual editor and autofill controls for the agenda race calendar (turn → race map per agenda slot).
 * Used by irregular training, megaphone race-forecast, and G1 hammer conservation.
 */
export default function AgendaIrregularScheduleEditor({ updateOverrideSetting }: Props) {
    const { colors } = useTheme()
    const { scenarioOverrides } = useContext(ScenarioOverridesContext)
    const { racing } = useContext(RacingContext)
    const { loadSettings } = useSettings()

    const agendaSlot = getAgendaScheduleKey(racing.selectedUserAgenda)
    const legacyScheduleKey = getLegacyAgendaScheduleKey(racing.selectedUserAgenda, racing.customAgendaTitle)
    const autofill = scenarioOverrides.trackblazerAgendaIrregularAutofill ?? defaultSettings.scenarioOverrides.trackblazerAgendaIrregularAutofill
    const allowPreDebut =
        scenarioOverrides.trackblazerEnableIrregularTrainingAgendaPreDebut ??
        defaultSettings.scenarioOverrides.trackblazerEnableIrregularTrainingAgendaPreDebut
    const parsedSchedules = useMemo(
        () => parseAgendaSchedules(scenarioOverrides.trackblazerAgendaIrregularSchedules),
        [scenarioOverrides.trackblazerAgendaIrregularSchedules]
    )

    const schedules = useMemo(() => {
        const { schedules: migrated } = ensureScheduleForAgendaSlot(parsedSchedules, agendaSlot, legacyScheduleKey)
        return migrated
    }, [agendaSlot, legacyScheduleKey, parsedSchedules])

    const migrationPersistedRef = useRef<string | null>(null)
    useEffect(() => {
        const migrationKey = `${agendaSlot}:${legacyScheduleKey}`
        if (migrationPersistedRef.current === migrationKey) {
            return
        }
        const { schedules: migrated, migrated: didMigrate } = ensureScheduleForAgendaSlot(parsedSchedules, agendaSlot, legacyScheduleKey)
        if (!didMigrate) {
            migrationPersistedRef.current = migrationKey
            return
        }
        migrationPersistedRef.current = migrationKey
        updateOverrideSetting("trackblazerAgendaIrregularSchedules", JSON.stringify(migrated))
    }, [agendaSlot, legacyScheduleKey, parsedSchedules, updateOverrideSetting])

    const current = schedules[agendaSlot] ?? { status: autofill ? "learning" : "ready", turns: {}, locked: false }
    const locked = current.locked === true
    const turnEntries = Object.entries(current.turns).sort(([a], [b]) => Number(a) - Number(b))
    const recordableTurnCount = countRecordableAgendaTurns(current.turns, allowPreDebut)
    const excludedTurnCount = turnEntries.length - recordableTurnCount

    const reloadScheduleFromDatabase = useCallback(() => {
        loadSettings(true)
    }, [loadSettings])

    useFocusEffect(
        useCallback(() => {
            reloadScheduleFromDatabase()
        }, [reloadScheduleFromDatabase])
    )

    useEffect(() => {
        const subscription = DeviceEventEmitter.addListener("AgendaIrregularScheduleUpdated", (data: { agendaName?: string }) => {
            if (!data?.agendaName || data.agendaName === agendaSlot) {
                reloadScheduleFromDatabase()
            }
        })
        return () => subscription.remove()
    }, [agendaSlot, reloadScheduleFromDatabase])

    const racesByTurn = useMemo(() => {
        const map = new Map<number, RaceWithKey[]>()
        for (const [key, race] of Object.entries(racesData as Record<string, RaceEntry>)) {
            const turn = race.turnNumber
            const list = map.get(turn) ?? []
            list.push({ ...race, key })
            map.set(turn, list)
        }
        for (const list of map.values()) {
            list.sort((a, b) => a.name.localeCompare(b.name))
        }
        return map
    }, [])

    const persistSchedule = useCallback(
        (nextEntry: AgendaScheduleEntry) => {
            const nextSchedules = { ...schedules, [agendaSlot]: nextEntry }
            updateOverrideSetting("trackblazerAgendaIrregularSchedules", JSON.stringify(nextSchedules))
        },
        [agendaSlot, schedules, updateOverrideSetting]
    )

    const setAutofill = (checked: boolean) => {
        if (locked) return
        updateOverrideSetting("trackblazerAgendaIrregularAutofill", checked)
        if (checked && turnEntries.length === 0) {
            persistSchedule({ status: "learning", turns: {}, locked: false })
        } else if (!checked && turnEntries.length > 0) {
            persistSchedule({ ...current, status: "ready" })
        }
    }

    const setLocked = (checked: boolean) => {
        persistSchedule({ ...current, locked: checked })
    }

    const markReady = () => {
        if (turnEntries.length === 0) return
        persistSchedule({ ...current, status: "ready" })
    }

    const clearSchedule = () => {
        if (locked) return
        persistSchedule({ status: autofill ? "learning" : "ready", turns: {}, locked: false })
    }

    const setTurnRace = (turn: number, raceKey: string) => {
        if (locked) return
        const nextTurns = { ...current.turns, [String(turn)]: raceKey }
        persistSchedule({ status: autofill ? current.status : "ready", turns: nextTurns, locked: current.locked })
    }

    const removeTurn = (turn: string) => {
        if (locked) return
        const nextTurns = { ...current.turns }
        delete nextTurns[turn]
        persistSchedule({
            status: Object.keys(nextTurns).length === 0 && autofill ? "learning" : current.status,
            turns: nextTurns,
            locked: current.locked,
        })
    }

    const customTitleHint =
        racing.customAgendaTitle?.trim() && racing.customAgendaTitle.trim() !== agendaSlot
            ? ` · in-game title "${racing.customAgendaTitle.trim()}"`
            : ""

    const statusLabel =
        current.status === "ready"
            ? "Ready — agenda irregular training may run on mapped turns"
            : autofill
              ? "Learning — autofill is recording races into this schedule"
              : "Manual — fill turns below to enable agenda irregular training"

    const styles = StyleSheet.create({
        section: { marginTop: 12 },
        label: { fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 6 },
        hint: { fontSize: 13, color: colors.mutedForeground, marginBottom: 8 },
        row: {
            flexDirection: "row",
            alignItems: "center",
            paddingVertical: 8,
            borderBottomWidth: StyleSheet.hairlineWidth,
            borderBottomColor: colors.border,
        },
        badge: {
            minWidth: 36,
            paddingHorizontal: 6,
            paddingVertical: 4,
            borderRadius: 6,
            marginRight: 8,
            alignItems: "center",
        },
        badgeText: { color: "#fff", fontSize: 11, fontWeight: "700" },
        turnRow: { flex: 1 },
        turnTitle: { fontSize: 14, fontWeight: "600", color: colors.foreground },
        turnMeta: { fontSize: 12, color: colors.mutedForeground, marginTop: 2 },
        altList: { maxHeight: 120, marginTop: 4 },
        altRow: { paddingVertical: 6 },
        altText: { fontSize: 13, color: colors.primary },
        altTextDisabled: { fontSize: 13, color: colors.mutedForeground },
        buttonRow: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 },
        lockedOverlay: { opacity: 0.55 },
    })

    return (
        <View style={styles.section}>
            <CustomCheckbox
                searchId="trackblazer-agenda-irregular-autofill"
                checked={autofill}
                onCheckedChange={setAutofill}
                label="Autofill Agenda Schedule From Runs"
                description="When enabled, scheduled agenda races recorded during a run appear in the schedule below. Irregular training stays off until the schedule is marked ready."
                disabled={locked}
            />

            {autofill && (
                <WarningContainer>
                    Try not to pause or stop the bot during an autofill learning run. Interrupting mid-career can leave the schedule incomplete or out of sync with your agenda.
                </WarningContainer>
            )}

            <CustomCheckbox
                searchId="trackblazer-agenda-irregular-lock-schedule"
                checked={locked}
                onCheckedChange={setLocked}
                label="Lock Schedule"
                description="When locked, manual edits are disabled. Autofill can still add new turns but will not overwrite existing mappings."
            />

            <InfoContainer>
                Agenda slot: <Text style={{ fontWeight: "700" }}>{agendaSlot}</Text>
                {customTitleHint} · {recordableTurnCount} recordable mapped turn(s)
                {excludedTurnCount > 0 ? ` (${excludedTurnCount} excluded: pre-debut/climax)` : ""}
                {locked ? " · Locked" : ""} · {statusLabel}
            </InfoContainer>

            {autofill && current.status === "learning" && (
                <WarningContainer>
                    Autofill learning is active. Recorded races appear in Mapped Turns below as you play. Irregular training on agenda days stays off until you mark the schedule ready or finish a career.
                </WarningContainer>
            )}

            {!autofill && turnEntries.length === 0 && !locked && (
                <WarningContainer>
                    Autofill is off and no turns are configured. Add races below to enable agenda irregular training.
                </WarningContainer>
            )}

            <View style={styles.buttonRow}>
                <CustomButton searchId="trackblazer-agenda-irregular-mark-ready" onPress={markReady} disabled={turnEntries.length === 0 || current.status === "ready"}>
                    Mark Schedule Ready
                </CustomButton>
                <CustomButton searchId="trackblazer-agenda-irregular-clear" onPress={clearSchedule} disabled={turnEntries.length === 0 || locked}>
                    Clear Schedule
                </CustomButton>
            </View>

            {turnEntries.length > 0 && (
                <View style={styles.section}>
                    <Text style={styles.label}>Mapped Turns</Text>
                    {turnEntries.map(([turn, raceKey]) => {
                        const turnNum = Number(turn)
                        const recordable = isAgendaAutofillRecordableTurn(turnNum, allowPreDebut)
                        const race = (racesData as Record<string, RaceEntry>)[raceKey]
                        const grade = race?.grade ?? "?"
                        const gradeColor = GRADE_COLORS[grade] ?? colors.primary
                        const yearOffset = (turnNum - 1) % 24
                        const yearName = turnNum <= 24 ? "Junior" : turnNum <= 48 ? "Classic" : "Senior"
                        return (
                            <View key={turn} style={[styles.row, !recordable && { opacity: 0.55 }]}>
                                <View style={[styles.badge, { backgroundColor: gradeColor }]}>
                                    <Text style={styles.badgeText}>{grade.replace("PRE_OP", "Pre")}</Text>
                                </View>
                                <View style={styles.turnRow}>
                                    <Text style={styles.turnTitle}>
                                        T{turn} · {yearName} {turnDateLabel(yearOffset)}
                                    </Text>
                                    <Text style={styles.turnMeta}>
                                        {race?.name ?? raceKey}
                                        {!recordable ? " · excluded from autofill count" : ""}
                                    </Text>
                                </View>
                                {!locked && (
                                    <TouchableOpacity onPress={() => removeTurn(turn)}>
                                        <Text style={{ color: colors.destructive, fontWeight: "600" }}>Remove</Text>
                                    </TouchableOpacity>
                                )}
                            </View>
                        )
                    })}
                </View>
            )}

            <View style={[styles.section, locked && styles.lockedOverlay]}>
                <Text style={styles.label}>{locked ? "Schedule (locked — view only)" : "Add / Edit Turn"}</Text>
                <Text style={styles.hint}>
                    {locked
                        ? "Unlock the schedule to change turn assignments manually."
                        : "Tap a turn, then pick the race for your agenda on that day. Autofill entries use the same list."}
                </Text>
                {YEAR_LABELS.map(({ name, startTurn }) => {
                    const endTurn = name === "Junior" ? 24 : name === "Classic" ? 48 : 72
                    const turns = Array.from({ length: endTurn - startTurn + 1 }, (_, i) => startTurn + i).filter((t) => (racesByTurn.get(t)?.length ?? 0) > 0)
                    if (turns.length === 0) return null
                    return (
                        <View key={name} style={{ marginBottom: 12 }}>
                            <Text style={[styles.label, { fontSize: 14 }]}>{name}</Text>
                            {turns.map((turn) => {
                                const options = racesByTurn.get(turn) ?? []
                                const selectedKey = current.turns[String(turn)]
                                return (
                                    <View key={turn} style={{ marginBottom: 8 }}>
                                        <Text style={styles.turnMeta}>
                                            T{turn} · {turnDateLabel((turn - 1) % 24)}
                                            {selectedKey ? ` · ${(racesData as Record<string, RaceEntry>)[selectedKey]?.name ?? "Set"}` : ""}
                                        </Text>
                                        <ScrollView horizontal nestedScrollEnabled showsHorizontalScrollIndicator={false} style={styles.altList}>
                                            {options.map((race) => (
                                                <TouchableOpacity
                                                    key={race.key}
                                                    style={styles.altRow}
                                                    disabled={locked}
                                                    onPress={() => setTurnRace(turn, race.key)}
                                                >
                                                    <Text
                                                        style={[
                                                            locked ? styles.altTextDisabled : styles.altText,
                                                            selectedKey === race.key && !locked && { fontWeight: "700" },
                                                        ]}
                                                    >
                                                        {race.grade.replace("PRE_OP", "Pre")} {race.name}
                                                    </Text>
                                                </TouchableOpacity>
                                            ))}
                                        </ScrollView>
                                    </View>
                                )
                            })}
                        </View>
                    )
                })}
            </View>
        </View>
    )
}
