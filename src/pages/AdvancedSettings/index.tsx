import { useCallback, useContext, useMemo, useRef } from "react"
import { View, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { Ionicons } from "@expo/vector-icons"
import { useTheme } from "../../context/ThemeContext"
import { AdvancedContext, BotMetaContext, GeneralMiscContext, RacingContext, Settings } from "../../context/BotStateContext"
import { SearchPageProvider } from "../../context/SearchPageContext"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSlider from "../../components/CustomSlider"
import CustomTitle from "../../components/CustomTitle"
import CustomButton from "../../components/CustomButton"
import PageHeader from "../../components/PageHeader"
import InfoContainer from "../../components/InfoContainer"
import SearchableItem from "../../components/SearchableItem"
import { Separator } from "../../components/ui/separator"
import { Text } from "../../components/ui/text"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import { DELAY_CALIBRATION_ACTIONS, getCurrentDelayForAction } from "../../lib/delayCalibration/registry"
import { applyDelayAdjustment } from "../../lib/delayCalibration/applyDelayAdjustment"
import { applyAllSuggestedDelays, applySuggestedDelayForAction } from "../../lib/delayCalibration/applyAllSuggestedDelays"
import { resetDelayCalibrationActionStats } from "../../lib/delayCalibration/resetDelayCalibrationActionStats"
import { DelayCalibrationActionStats } from "../../lib/delayCalibration/types"

const formatStatsSummary = (stats: DelayCalibrationActionStats | undefined): string => {
    if (!stats || stats.totalExecutions === 0) {
        if (stats?.suggestedDelaySec != null) {
            return "0 / 0 failed"
        }
        return "No data from last session"
    }
    const parts: string[] = [`${stats.failureCount} / ${stats.totalExecutions} failed`]
    if (stats.tooFastCount > 0) parts.push(`${stats.tooFastCount} too fast`)
    if (stats.tooSlowCount > 0) parts.push(`${stats.tooSlowCount} too slow`)
    return parts.join(" · ")
}

const formatSuggested = (stats: DelayCalibrationActionStats | undefined): string | null => {
    if (stats?.suggestedDelaySec == null) return null
    const suggested = Number(stats.suggestedDelaySec)
    if (!Number.isFinite(suggested)) return null
    const avgMs = stats.avgSuccessDelayMs != null ? Number(stats.avgSuccessDelayMs) : null
    const avg = avgMs != null && Number.isFinite(avgMs) ? ` (avg ${(avgMs / 1000).toFixed(2)}s)` : ""
    return `${suggested.toFixed(2)}s${avg}`
}

const mergeAdvancedSettings = (
    defaults: Settings["advanced"],
    slice: Settings["advanced"] | null | undefined
): Settings["advanced"] => ({
    ...defaults,
    ...(slice ?? {}),
    delayCalibrationStats: {
        ...defaults.delayCalibrationStats,
        ...(slice?.delayCalibrationStats ?? {}),
    },
})

/**
 * Advanced settings — per-action delay calibration and tuning.
 */
const AdvancedSettings = () => {
    usePerformanceLogging("AdvancedSettings")
    const { colors } = useTheme()
    const scrollViewRef = useRef<ScrollView>(null)
    const { advanced: advancedSlice, updateAdvanced } = useContext(AdvancedContext)
    const { defaultSettings, setSettings } = useContext(BotMetaContext)
    const { general } = useContext(GeneralMiscContext)
    const { racing } = useContext(RacingContext)
    const advanced = useMemo(() => mergeAdvancedSettings(defaultSettings.advanced, advancedSlice), [defaultSettings.advanced, advancedSlice])

    const settingsSnapshot = useMemo<Settings>(
        () => ({
            ...defaultSettings,
            general,
            racing,
            advanced,
        }),
        [general, racing, advanced]
    )

    const delayCalibrationStats = advanced.delayCalibrationStats ?? {}

    const hasSessionStats = useMemo(
        () => DELAY_CALIBRATION_ACTIONS.some((a) => (delayCalibrationStats[a.id]?.totalExecutions ?? 0) > 0),
        [delayCalibrationStats]
    )

    const hasAnySuggestion = useMemo(
        () => DELAY_CALIBRATION_ACTIONS.some((a) => delayCalibrationStats[a.id]?.suggestedDelaySec != null),
        [delayCalibrationStats]
    )

    const adjustDelay = useCallback(
        (actionId: string, sign: 1 | -1) => {
            const action = DELAY_CALIBRATION_ACTIONS.find((a) => a.id === actionId)
            if (!action) return
            const delta = sign * advanced.delayCalibrationIncrement
            setSettings((prev) => {
                const patch = applyDelayAdjustment(prev, action, delta)
                const merged: Settings = {
                    ...prev,
                    ...(patch.general ? { general: { ...prev.general, ...patch.general } } : {}),
                    ...(patch.racing ? { racing: { ...prev.racing, ...patch.racing } } : {}),
                    ...(patch.advanced ? { advanced: { ...prev.advanced, ...patch.advanced } } : {}),
                }
                return {
                    ...merged,
                    advanced: {
                        ...merged.advanced,
                        delayCalibrationStats: resetDelayCalibrationActionStats(merged.advanced.delayCalibrationStats, actionId),
                    },
                }
            })
        },
        [advanced.delayCalibrationIncrement, setSettings]
    )

    const approveSuggested = useCallback(
        (actionId: string) => {
            setSettings((prev) => applySuggestedDelayForAction(prev, actionId, delayCalibrationStats))
        },
        [delayCalibrationStats, setSettings]
    )

    const approveAllSuggested = useCallback(() => {
        setSettings((prev) => applyAllSuggestedDelays(prev, delayCalibrationStats))
    }, [delayCalibrationStats, setSettings])

    const styles = useMemo(
        () =>
            StyleSheet.create({
                root: { flex: 1 },
                content: { padding: 16, paddingBottom: 32 },
                actionRow: {
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 8,
                    padding: 12,
                    marginBottom: 10,
                    gap: 8,
                },
                actionHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", gap: 8 },
                delayControls: { flexDirection: "row", alignItems: "center", gap: 8 },
                stepButton: {
                    width: 36,
                    height: 36,
                    borderRadius: 8,
                    borderWidth: 1,
                    borderColor: colors.border,
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: colors.card,
                },
                statsText: { color: colors.mutedForeground, fontSize: 13 },
                suggestedRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
            }),
        [colors]
    )

    return (
        <SearchPageProvider page="AdvancedSettings" scrollViewRef={scrollViewRef}>
            <View style={styles.root}>
                <PageHeader title="Advanced Settings" />
                <ScrollView ref={scrollViewRef} style={{ flex: 1 }} contentContainerStyle={styles.content}>
                    <CustomTitle title="Delay calibration" description="Tune per-action waits using log timing from home-button runs." />

                    <InfoContainer style={{ marginBottom: 16 }}>
                        <Text style={{ color: colors.foreground }}>
                            Enable calibration, then start and stop the bot from the Home scenario button (not the overlay). After stopping, session stats appear below. Use +/- to nudge each delay, or
                            approve suggested values derived from successful timings.
                        </Text>
                    </InfoContainer>

                    <SearchableItem id="advanced-enable-delay-calibration" title="Enable delay calibration" description="Collect per-action timing data during bot runs.">
                        <CustomCheckbox
                            label="Enable delay calibration"
                            checked={advanced.enableDelayCalibration}
                            onCheckedChange={(checked) => updateAdvanced({ enableDelayCalibration: checked })}
                            description="When enabled, the bot emits structured timing logs for calibrated actions. Stats update when you stop from Home."
                        />
                    </SearchableItem>

                    <SearchableItem
                        id="advanced-delay-calibration-increment"
                        title="Delay adjustment step"
                        description="How much each +/- button changes an action delay."
                    >
                        <CustomSlider
                            label="Adjustment step (seconds)"
                            value={advanced.delayCalibrationIncrement ?? defaultSettings.advanced.delayCalibrationIncrement}
                            placeholder={defaultSettings.advanced.delayCalibrationIncrement}
                            onValueChange={(value) => updateAdvanced({ delayCalibrationIncrement: value })}
                            onSlidingComplete={(value) => updateAdvanced({ delayCalibrationIncrement: value })}
                            min={0.01}
                            max={0.5}
                            step={0.01}
                            description="Step size for the +/- buttons next to each action delay."
                        />
                    </SearchableItem>

                    {advanced.lastCalibrationSessionAt && (
                        <Text style={[styles.statsText, { marginTop: 8, marginBottom: 8 }]}>
                            Last session: {new Date(advanced.lastCalibrationSessionAt).toLocaleString()}
                        </Text>
                    )}

                    {hasAnySuggestion && (
                        <CustomButton variant="default" onPress={approveAllSuggested} style={{ marginBottom: 16, alignSelf: "flex-start" }}>
                            Approve all suggested delays
                        </CustomButton>
                    )}

                    <Separator style={{ marginVertical: 12 }} />
                    <CustomTitle title="Per-action delays" description="Current delay, session stats, and fine-tuning controls." />

                    {!hasSessionStats && advanced.enableDelayCalibration && (
                        <Text style={[styles.statsText, { marginBottom: 12 }]}>
                            No calibration data yet. Run the bot from Home and stop from Home to populate stats.
                        </Text>
                    )}

                    {DELAY_CALIBRATION_ACTIONS.map((action) => {
                        const stats = delayCalibrationStats[action.id]
                        const currentDelay = getCurrentDelayForAction(action, settingsSnapshot)
                        const suggested = formatSuggested(stats)

                        return (
                            <SearchableItem
                                key={action.id}
                                id={`advanced-delay-${action.id}`}
                                title={action.label}
                                description={action.description}
                            >
                                <View style={styles.actionRow}>
                                    <View style={styles.actionHeader}>
                                        <View style={{ flex: 1 }}>
                                            <Text style={{ fontWeight: "600", color: colors.foreground }}>{action.label}</Text>
                                            <Text style={{ color: colors.mutedForeground, fontSize: 13, marginTop: 4 }}>{action.description}</Text>
                                        </View>
                                    </View>

                                    <Text style={styles.statsText}>{formatStatsSummary(stats)}</Text>

                                    <View style={styles.delayControls}>
                                        <TouchableOpacity
                                            style={styles.stepButton}
                                            onPress={() => adjustDelay(action.id, -1)}
                                            accessibilityLabel={`Decrease ${action.label} delay`}
                                        >
                                            <Ionicons name="remove" size={20} color={colors.foreground} />
                                        </TouchableOpacity>
                                        <Text style={{ minWidth: 64, textAlign: "center", color: colors.foreground, fontWeight: "600" }}>
                                            {Number.isFinite(currentDelay) ? currentDelay.toFixed(2) : "0.00"}s
                                        </Text>
                                        <TouchableOpacity
                                            style={styles.stepButton}
                                            onPress={() => adjustDelay(action.id, 1)}
                                            accessibilityLabel={`Increase ${action.label} delay`}
                                        >
                                            <Ionicons name="add" size={20} color={colors.foreground} />
                                        </TouchableOpacity>
                                    </View>

                                    {suggested && (
                                        <View style={styles.suggestedRow}>
                                            <Text style={styles.statsText}>Suggested: {suggested}</Text>
                                            <CustomButton variant="outline" onPress={() => approveSuggested(action.id)}>
                                                Apply
                                            </CustomButton>
                                        </View>
                                    )}
                                </View>
                            </SearchableItem>
                        )
                    })}

                    <Separator style={{ marginVertical: 16 }} />
                    <CustomTitle
                        title="Overlay pause / resume"
                        description="Control what runs when you stop and restart from the floating overlay button (not Home)."
                    />

                    <InfoContainer style={{ marginBottom: 16 }}>
                        <Text style={{ color: colors.foreground }}>
                            By default, overlay resume does not force skill checks, agenda reloads, or shop visits that a fresh bot session would normally trigger. Enable the immediate options below to
                            recheck right after overlay start. Conditional options run later when the turn or skill threshold changes.
                        </Text>
                    </InfoContainer>

                    <SearchableItem
                        id="advanced-overlay-resume-recheck-skills"
                        title="Recheck skills on overlay resume"
                        description="Run skill-point check immediately when restarting from the overlay button."
                    >
                        <CustomCheckbox
                            label="Recheck skills on overlay resume"
                            checked={advanced.overlayResumeRecheckSkills}
                            onCheckedChange={(checked) => updateAdvanced({ overlayResumeRecheckSkills: checked })}
                            description="When off, skill checks are skipped on overlay resume unless the threshold option below applies."
                        />
                    </SearchableItem>

                    <SearchableItem
                        id="advanced-overlay-resume-reload-agenda"
                        title="Reload agenda on overlay resume"
                        description="Reload the in-game race agenda immediately when restarting from the overlay button."
                    >
                        <CustomCheckbox
                            label="Reload agenda on overlay resume"
                            checked={advanced.overlayResumeReloadAgenda}
                            onCheckedChange={(checked) => updateAdvanced({ overlayResumeReloadAgenda: checked })}
                            description="When off, agenda reload is skipped except on the same turn if loading was interrupted before it finished."
                        />
                    </SearchableItem>

                    <SearchableItem
                        id="advanced-overlay-resume-recheck-shop"
                        title="Recheck shop on overlay resume"
                        description="Run the Trackblazer initial shop check immediately when restarting from the overlay button."
                    >
                        <CustomCheckbox
                            label="Recheck shop on overlay resume"
                            checked={advanced.overlayResumeRecheckShop}
                            onCheckedChange={(checked) => updateAdvanced({ overlayResumeRecheckShop: checked })}
                            description="When off, the session shop check is skipped on overlay resume unless the turn-change option below applies."
                        />
                    </SearchableItem>

                    <SearchableItem
                        id="advanced-overlay-recheck-shop-turn-change"
                        title="Recheck shop when turn changes"
                        description="After overlay resume, run shop check when the in-game turn advances past the paused turn."
                    >
                        <CustomCheckbox
                            label="Recheck shop when turn changes"
                            checked={advanced.overlayRecheckShopOnTurnChange}
                            onCheckedChange={(checked) => updateAdvanced({ overlayRecheckShopOnTurnChange: checked })}
                            description="Useful when overlay resume skipped the shop but the next turn should still visit it."
                        />
                    </SearchableItem>

                    <SearchableItem
                        id="advanced-overlay-recheck-skills-threshold"
                        title="Recheck skills when over threshold"
                        description="After overlay resume, run skill check when skill points meet the configured threshold."
                    >
                        <CustomCheckbox
                            label="Recheck skills when over threshold"
                            checked={advanced.overlayRecheckSkillsWhenOverThreshold}
                            onCheckedChange={(checked) => updateAdvanced({ overlayRecheckSkillsWhenOverThreshold: checked })}
                            description="Runs on the next main-screen opportunity even when immediate skill recheck is off."
                        />
                    </SearchableItem>
                </ScrollView>
            </View>
        </SearchPageProvider>
    )
}

export default AdvancedSettings
