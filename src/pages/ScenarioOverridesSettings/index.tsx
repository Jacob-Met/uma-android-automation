import { useMemo, useContext, useRef, useState, useCallback, useEffect } from "react"
import { View, Text, ScrollView, StyleSheet, Image, TouchableOpacity, InteractionManager } from "react-native"
import { Divider } from "react-native-paper"
import { useRoute } from "@react-navigation/native"
import { useTheme } from "../../context/ThemeContext"
import { ScenarioOverridesContext, BotMetaContext, GeneralMiscContext, Settings } from "../../context/BotStateContext"
import { SearchPageProvider } from "../../context/SearchPageContext"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomAccordion from "../../components/CustomAccordion"
import CustomButton from "../../components/CustomButton"
import AgendaIrregularScheduleEditor from "./components/AgendaIrregularScheduleEditor"
import { Input } from "../../components/ui/input"
import { CircleCheckBig, Trash2 } from "lucide-react-native"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import trackblazerIcons from "./icons"

/**
 * Maps a settings searchId prefix to the accordion section value it lives under.
 * Used to auto-expand the right section when the user navigates here from in-app search.
 */
const SECTION_BY_SEARCH_PREFIX: ReadonlyArray<readonly [string, string]> = [["trackblazer-", "trackblazer"]]

/**
 * Maps a Home-page scenario value to the accordion section value it should default-expand.
 */
const SECTION_BY_SCENARIO: Readonly<Record<string, string>> = {
    Trackblazer: "trackblazer",
}

const DEFAULT_IRREGULAR_GRADE_GAINS: Record<string, number> = { G1: 30, G2: 30, G3: 30 }

const parseIrregularMinGainByGrade = (json: string | undefined): Record<string, number> => {
    try {
        if (!json || json.trim() === "") {
            return { ...DEFAULT_IRREGULAR_GRADE_GAINS }
        }
        const parsed = JSON.parse(json) as unknown
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            return { ...DEFAULT_IRREGULAR_GRADE_GAINS }
        }
        const out = { ...DEFAULT_IRREGULAR_GRADE_GAINS }
        for (const grade of ["G1", "G2", "G3"] as const) {
            const value = (parsed as Record<string, unknown>)[grade]
            if (typeof value === "number") {
                out[grade] = value
            }
        }
        return out
    } catch {
        return { ...DEFAULT_IRREGULAR_GRADE_GAINS }
    }
}

/**
 * The Scenario Overrides Settings page.
 * Provides configuration for scenario-specific behavior overrides.
 */
const ScenarioOverridesSettings = () => {
    usePerformanceLogging("ScenarioOverridesSettings")
    const { colors } = useTheme()
    const { scenarioOverrides, updateScenarioOverrides } = useContext(ScenarioOverridesContext)
    const { defaultSettings } = useContext(BotMetaContext)
    const { general } = useContext(GeneralMiscContext)
    const route = useRoute<any>()
    const scrollViewRef = useRef<ScrollView>(null)

    const [searchQuery, setSearchQuery] = useState("")

    // Compute which accordion sections should be expanded on first render.
    // Priority: a `targetId` route param from in-app search wins, otherwise default to the user's currently selected scenario.
    // Captured once at first render so re-entering the page or changing the scenario later does not stomp on the user's manual toggles.
    const accordionDefaultValue = useMemo<string[]>(() => {
        const targetId = typeof route.params?.targetId === "string" ? (route.params.targetId as string) : undefined
        if (targetId) {
            for (const [prefix, section] of SECTION_BY_SEARCH_PREFIX) {
                if (targetId.startsWith(prefix)) return [section]
            }
        }
        const fromScenario = SECTION_BY_SCENARIO[general.scenario as string]
        return fromScenario ? [fromScenario] : []
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    // Two-phase mount, mirroring the TrainingSettings deferral pattern from PR #299. Renders the page header on the first paint
    // so navigation feels instant; the heavy accordion body commits one tick later via InteractionManager. When navigating in
    // from in-app search, skip the deferral so SearchableItem's measureLayout-based scroll-to runs against mounted content.
    const hasTargetId = typeof route.params?.targetId === "string" && (route.params.targetId as string).length > 0
    const [showBody, setShowBody] = useState<boolean>(hasTargetId)
    useEffect(() => {
        if (showBody) return
        const handle = InteractionManager.runAfterInteractions(() => setShowBody(true))
        return () => handle.cancel()
    }, [showBody])

    const filteredItems = useMemo(() => {
        return Object.keys(trackblazerIcons).filter((itemName) => {
            const item = trackblazerIcons[itemName]
            const query = searchQuery.toLowerCase()
            return itemName.toLowerCase().includes(query) || item.description.toLowerCase().includes(query)
        })
    }, [searchQuery])

    /**
     * Update a scenario override setting.
     * @param key The key of the setting to update.
     * @param value The value to set the setting to.
     */
    const updateOverrideSetting = useCallback(
        (key: keyof Settings["scenarioOverrides"], value: any) => {
            updateScenarioOverrides({ [key]: value } as Partial<Settings["scenarioOverrides"]>)
        },
        [updateScenarioOverrides]
    )

    const irregularMinGainByGrade = useMemo(
        () => parseIrregularMinGainByGrade(scenarioOverrides.trackblazerIrregularTrainingMinStatGainByGrade),
        [scenarioOverrides.trackblazerIrregularTrainingMinStatGainByGrade]
    )

    const updateIrregularMinGainForGrade = useCallback(
        (grade: string, value: number) => {
            updateOverrideSetting(
                "trackblazerIrregularTrainingMinStatGainByGrade",
                JSON.stringify({ ...irregularMinGainByGrade, [grade]: value })
            )
        },
        [irregularMinGainByGrade, updateOverrideSetting]
    )

    /**
     * Toggle the exclusion status of an item.
     * @param itemName The name of the item to toggle.
     */
    const handleItemPress = useCallback(
        (itemName: string) => {
            const currentExcluded = scenarioOverrides.trackblazerExcludedItems
            if (currentExcluded.includes(itemName)) {
                updateOverrideSetting(
                    "trackblazerExcludedItems",
                    currentExcluded.filter((id) => id !== itemName)
                )
            } else {
                updateOverrideSetting("trackblazerExcludedItems", [...currentExcluded, itemName])
            }
        },
        [scenarioOverrides.trackblazerExcludedItems, updateOverrideSetting]
    )

    const styles = useMemo(
        () =>
            StyleSheet.create({
                root: {
                    flex: 1,
                    flexDirection: "column",
                    justifyContent: "center",
                    margin: 10,
                    backgroundColor: colors.background,
                },
                section: {
                    marginBottom: 8,
                },
                accordionDescription: {
                    fontSize: 14,
                    color: colors.foreground,
                    opacity: 0.7,
                    marginBottom: 12,
                },
                itemContainer: {
                    backgroundColor: colors.card,
                    padding: 12,
                    borderRadius: 8,
                    marginBottom: 8,
                    flexDirection: "row",
                    justifyContent: "space-between",
                    alignItems: "center",
                },
            }),
        [colors]
    )

    return (
        <View style={styles.root}>
            <PageHeader title="Scenario Overrides Settings" />

            <SearchPageProvider page="ScenarioOverridesSettings" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                    <View className="m-1">
                        {showBody && (
                            <CustomAccordion
                                type="single"
                                defaultValue={accordionDefaultValue}
                                sections={[
                                    {
                                        value: "trackblazer",
                                        title: "Trackblazer Overrides",
                                        children: (
                                            <>
                                                <Text style={styles.accordionDescription}>Specific overrides for the Trackblazer scenario.</Text>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-consecutive-races-limit"
                                                        value={scenarioOverrides.trackblazerConsecutiveRacesLimit}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerConsecutiveRacesLimit}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerConsecutiveRacesLimit", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerConsecutiveRacesLimit", value)}
                                                        min={3}
                                                        max={30}
                                                        step={1}
                                                        label="Consecutive Races Limit"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Sets the maximum number of consecutive races the bot is allowed to run in the Trackblazer scenario before stopping. Note that a -30 stat penalty can apply starting from 3 consecutive races."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-energy-threshold"
                                                        value={scenarioOverrides.trackblazerEnergyThreshold}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerEnergyThreshold}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerEnergyThreshold", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerEnergyThreshold", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Energy Threshold to use Energy Items"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Energy level below which the bot uses energy items before training (not at main-screen entry). Emergency race recovery still uses reserved items when needed."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-enable-energy-item-high-failure-train"
                                                        checked={scenarioOverrides.trackblazerEnableEnergyItemForHighFailureTraining}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableEnergyItemForHighFailureTraining", checked)}
                                                        label="Use Energy Items for High-Failure Training"
                                                        description="When failure on the selected training is risky (≥20%) and main stat gain meets the minimum: queue an energy item before training, then re-analyze. Vita 65 (+65) and Royal Kale Juice (+100) ignore failure-above-max margins when no Good-Luck Charm is used; charm is preferred when both charm and +65/+100 are available. Vita 20/40 still use their margins. Never combines charm and failure-mitigation energy on the same turn."
                                                        className="my-2"
                                                    />
                                                </View>

                                                {scenarioOverrides.trackblazerEnableEnergyItemForHighFailureTraining && (
                                                    <>
                                                        <View style={styles.section}>
                                                            <CustomSlider
                                                                searchId="trackblazer-vita20-failure-above-minimum"
                                                                value={scenarioOverrides.trackblazerVita20FailureAboveMinimum}
                                                                placeholder={defaultSettings.scenarioOverrides.trackblazerVita20FailureAboveMinimum}
                                                                onValueChange={(value) => updateOverrideSetting("trackblazerVita20FailureAboveMinimum", value)}
                                                                onSlidingComplete={(value) => updateOverrideSetting("trackblazerVita20FailureAboveMinimum", value)}
                                                                min={0}
                                                                max={50}
                                                                step={1}
                                                                label="Vita 20 — Failure Above Maximum (%)"
                                                                labelUnit=""
                                                                showValue={true}
                                                                showLabels={true}
                                                                description="High-failure train: failure must exceed max failure chance by at least this much before Vita 20 (+20 energy) is used."
                                                            />
                                                        </View>

                                                        <View style={styles.section}>
                                                            <CustomSlider
                                                                searchId="trackblazer-vita40-failure-above-minimum"
                                                                value={scenarioOverrides.trackblazerVita40FailureAboveMinimum}
                                                                placeholder={defaultSettings.scenarioOverrides.trackblazerVita40FailureAboveMinimum}
                                                                onValueChange={(value) => updateOverrideSetting("trackblazerVita40FailureAboveMinimum", value)}
                                                                onSlidingComplete={(value) => updateOverrideSetting("trackblazerVita40FailureAboveMinimum", value)}
                                                                min={0}
                                                                max={50}
                                                                step={1}
                                                                label="Vita 40 — Failure Above Maximum (%)"
                                                                labelUnit=""
                                                                showValue={true}
                                                                showLabels={true}
                                                                description="High-failure train: failure must exceed max failure chance by at least this much before Vita 40 (+40 energy) is used."
                                                            />
                                                        </View>

                                                        <View style={styles.section}>
                                                            <CustomSlider
                                                                searchId="trackblazer-vita65-failure-above-minimum"
                                                                value={scenarioOverrides.trackblazerVita65FailureAboveMinimum}
                                                                placeholder={defaultSettings.scenarioOverrides.trackblazerVita65FailureAboveMinimum}
                                                                onValueChange={(value) => updateOverrideSetting("trackblazerVita65FailureAboveMinimum", value)}
                                                                onSlidingComplete={(value) => updateOverrideSetting("trackblazerVita65FailureAboveMinimum", value)}
                                                                min={0}
                                                                max={50}
                                                                step={1}
                                                                label="Vita 65 — Failure Above Maximum (%)"
                                                                labelUnit=""
                                                                showValue={true}
                                                                showLabels={true}
                                                                description="Low-tier high-failure train: failure must exceed max failure chance by at least this much before Vita 65 (+65) is considered on the margin path. Vita 65 and Royal Kale Juice (+100) also fire without any failure margin when charm is unavailable; charm wins when both are available."
                                                            />
                                                        </View>

                                                        <View style={styles.section}>
                                                            <CustomSlider
                                                                searchId="trackblazer-energy-item-min-main-stat-gain"
                                                                value={scenarioOverrides.trackblazerEnergyItemMinMainStatGain}
                                                                placeholder={defaultSettings.scenarioOverrides.trackblazerEnergyItemMinMainStatGain}
                                                                onValueChange={(value) => updateOverrideSetting("trackblazerEnergyItemMinMainStatGain", value)}
                                                                onSlidingComplete={(value) => updateOverrideSetting("trackblazerEnergyItemMinMainStatGain", value)}
                                                                min={1}
                                                                max={100}
                                                                step={1}
                                                                label="Minimum Main Stat Gain for Energy Train"
                                                                labelUnit=""
                                                                showValue={true}
                                                                showLabels={true}
                                                                description="Minimum main stat gain on the selected training before the high-failure energy-item path may fire."
                                                            />
                                                        </View>
                                                    </>
                                                )}

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-max-retries-per-race"
                                                        value={scenarioOverrides.trackblazerMaxRetriesPerRace}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerMaxRetriesPerRace}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerMaxRetriesPerRace", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerMaxRetriesPerRace", value)}
                                                        min={0}
                                                        max={5}
                                                        step={1}
                                                        label="Max Retries per Race"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="The maximum number of times the bot will attempt to retry a failed race in the Trackblazer scenario."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-enable-climax-charm-training"
                                                        checked={scenarioOverrides.trackblazerEnableClimaxCharmTraining}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableClimaxCharmTraining", checked)}
                                                        label="Enable Climax Charm Training"
                                                        description="During Trackblazer Climax (turns 73–75), when you have enough unused Good-Luck Charms for remaining turns: train instead of rest, bypass charm conservation floors, and pick the highest non-maxed stat. Turn off to use normal charm rules during Climax."
                                                        className="my-2"
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-min-stat-gain-for-charm"
                                                        value={scenarioOverrides.trackblazerMinStatGainForCharm}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerMinStatGainForCharm}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerMinStatGainForCharm", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerMinStatGainForCharm", value)}
                                                        min={20}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Good-Luck Charm"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="The minimum expected gain for the main training stat required to use a Good-Luck Charm instead of skipping training."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-save-good-luck-charm-for-summer"
                                                        checked={scenarioOverrides.trackblazerSaveGoodLuckCharmForSummer}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerSaveGoodLuckCharmForSummer", checked)}
                                                        label="Save Failure Mitigation Pool for Finale (Senior 65–72)"
                                                        description="When enabled, hold back a combined pool of Good-Luck Charm + Vita 65 (+65) + Royal Kale Juice (+100) during Senior pre-Finale turns 65–72 so items are saved for Finale 73–75. Summer (37–40, 61–64) is excluded — spend freely during Summer. Pool size is set below. Override still allows spending from the reserve on high-gain risky trains. Kale at ≤20% energy bypasses the pool. Climax charm training bypasses conservation."
                                                        className="my-2"
                                                    />
                                                </View>

                                                {scenarioOverrides.trackblazerSaveGoodLuckCharmForSummer && (
                                                    <View style={styles.section}>
                                                        <CustomSlider
                                                            searchId="trackblazer-failure-mitigation-pool-reserve"
                                                            value={scenarioOverrides.trackblazerFailureMitigationPoolReserve}
                                                            placeholder={defaultSettings.scenarioOverrides.trackblazerFailureMitigationPoolReserve}
                                                            onValueChange={(value) => updateOverrideSetting("trackblazerFailureMitigationPoolReserve", value)}
                                                            onSlidingComplete={(value) => updateOverrideSetting("trackblazerFailureMitigationPoolReserve", value)}
                                                            min={0}
                                                            max={10}
                                                            step={1}
                                                            label="Failure Mitigation Pool Reserve"
                                                            showValue={true}
                                                            showLabels={true}
                                                            description="Combined units to conserve across Good-Luck Charm, Vita 65, and Royal Kale Juice (any mix) during Senior 65–72 only. While pool total is at or below this count, items are not used except via the override below or Kale at ≤20% energy. 0 disables the pool."
                                                        />
                                                    </View>
                                                )}

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-summer-charm-override-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerSummerCharmOverrideMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerSummerCharmOverrideMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerSummerCharmOverrideMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerSummerCharmOverrideMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Failure Mitigation Pool Override Min Main Gain"
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Paired with Save Failure Mitigation Pool. Allows spending from the reserve when main gain is at least this on a risky training that needs Charm or +65/+100 and has no other mitigation (including spare pool stock above the reserve). 0 disables the override."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-low-main-stat-gain-item-floor"
                                                        value={scenarioOverrides.trackblazerLowMainStatGainItemFloor}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerLowMainStatGainItemFloor}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerLowMainStatGainItemFloor", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerLowMainStatGainItemFloor", value)}
                                                        min={0}
                                                        max={50}
                                                        step={1}
                                                        label="Low Main Stat Gain Item Floor"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="When mood is BAD or AWFUL, refuse to use Reset Whistle / Good-Luck Charm / Megaphone if main-stat gain is below this floor. Prevents wasting items on structurally low-return turns where the mood multiplier caps the stat gains."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-coaching-megaphone-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerCoachingMegaphoneMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerCoachingMegaphoneMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerCoachingMegaphoneMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerCoachingMegaphoneMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Coaching Megaphone"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Only use Coaching Megaphone when the selected training's main stat gain meets this minimum. Set to 0 to disable. Ignored during summer training."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-motivating-megaphone-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerMotivatingMegaphoneMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerMotivatingMegaphoneMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerMotivatingMegaphoneMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerMotivatingMegaphoneMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Motivating Megaphone"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Only use Motivating Megaphone when the selected training's main stat gain meets this minimum. Set to 0 to disable. Ignored during summer training."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-empowering-megaphone-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerEmpoweringMegaphoneMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerEmpoweringMegaphoneMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerEmpoweringMegaphoneMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerEmpoweringMegaphoneMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Empowering Megaphone"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Only use Empowering Megaphone when the selected training's main stat gain meets this minimum. Set to 0 to disable. Ignored during summer training."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-speed-ankle-weight-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerSpeedAnkleWeightMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerSpeedAnkleWeightMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerSpeedAnkleWeightMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerSpeedAnkleWeightMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Speed Ankle Weights"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Outside summer: ankle weights use the Good-Luck Charm min gain floor when failure is above max (raw gain, megaphone bonus not included). Ignored during summer. Per-stat sliders below are unused."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-stamina-ankle-weight-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerStaminaAnkleWeightMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerStaminaAnkleWeightMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerStaminaAnkleWeightMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerStaminaAnkleWeightMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Stamina Ankle Weights"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Only use Stamina Ankle Weights when the selected Stamina training's main stat gain meets this minimum. Set to 0 to disable."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-power-ankle-weight-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerPowerAnkleWeightMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerPowerAnkleWeightMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerPowerAnkleWeightMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerPowerAnkleWeightMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Power Ankle Weights"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Only use Power Ankle Weights when the selected Power training's main stat gain meets this minimum. Set to 0 to disable."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-guts-ankle-weight-min-stat-gain"
                                                        value={scenarioOverrides.trackblazerGutsAnkleWeightMinStatGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerGutsAnkleWeightMinStatGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerGutsAnkleWeightMinStatGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerGutsAnkleWeightMinStatGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Minimum Main Stat Gain for Guts Ankle Weights"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Only use Guts Ankle Weights when the selected Guts training's main stat gain meets this minimum. Set to 0 to disable."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 8 }}>Summer Training Reserves</Text>
                                                    <Text style={{ fontSize: 13, color: colors.foreground, opacity: 0.8, marginBottom: 12 }}>
                                                        Hold items back outside summer so more are available during summer training. Both reserves are ignored while summer training is active.
                                                    </Text>
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-ankle-weight-summer-reserve"
                                                        value={scenarioOverrides.trackblazerAnkleWeightSummerReserve}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerAnkleWeightSummerReserve}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerAnkleWeightSummerReserve", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerAnkleWeightSummerReserve", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Ankle Weights Summer Reserve (per type)"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Number of each ankle weight type to keep outside summer training. Set to 0 to disable."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-megaphone-summer-reserve"
                                                        value={scenarioOverrides.trackblazerMegaphoneSummerReserve}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerMegaphoneSummerReserve}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerMegaphoneSummerReserve", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerMegaphoneSummerReserve", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Megaphone Summer Reserve (total)"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Total megaphone units to keep outside summer training (best tier reserved first: Empowering, then Motivating, then Coaching). Set to 0 to disable."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 8 }}>Item Conservation</Text>
                                                    <Text style={{ fontSize: 13, color: colors.foreground, opacity: 0.8, marginBottom: 12 }}>
                                                        Controls how aggressively the bot saves items for high-value turns. Set any threshold to 0 to disable that conservation rule. Race-item reserves
                                                        apply from Turn 65 onward; Glow Stick Min Fans applies at all times.
                                                    </Text>
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-energy-item-reserve"
                                                        value={scenarioOverrides.trackblazerEnergyItemReserve}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerEnergyItemReserve}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerEnergyItemReserve", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerEnergyItemReserve", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Energy Item Emergency Reserve"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Number of energy items (lowest-tier first) to keep reserved for emergency race recovery."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-cupcake-reserve"
                                                        value={scenarioOverrides.trackblazerCupcakeReserve}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerCupcakeReserve}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerCupcakeReserve", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerCupcakeReserve", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Cupcake Reserve for Kale Juice Synergy"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Number of cupcakes (Plain preferred) to keep so the mood penalty from Royal Kale Juice can be offset."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-master-hammer-finale-reserve"
                                                        value={scenarioOverrides.trackblazerMasterHammerFinaleReserve}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerMasterHammerFinaleReserve}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerMasterHammerFinaleReserve", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerMasterHammerFinaleReserve", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Master Cleat Hammer Finale Reserve"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Master Cleat Hammers held back for Finale days (73-75). Only surplus above this reserve is spent on pre-finale G1/G2 races (from turn 13). Days 73-74 require 2+ copies before spending one; Day 75 uses any remainder."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-artisan-hammer-min-stock-for-g3"
                                                        value={scenarioOverrides.trackblazerArtisanHammerMinStockForG3}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerArtisanHammerMinStockForG3}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerArtisanHammerMinStockForG3", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerArtisanHammerMinStockForG3", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Artisan Hammer Min Stock for G3"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Minimum Artisan Cleat Hammer inventory before the bot spends one on a G3 race (from Turn 65 onward)."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-artisan-hammer-min-stock-for-g2"
                                                        value={scenarioOverrides.trackblazerArtisanHammerMinStockForG2}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerArtisanHammerMinStockForG2}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerArtisanHammerMinStockForG2", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerArtisanHammerMinStockForG2", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Artisan Hammer Min Stock for G2"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Minimum Artisan Cleat Hammer inventory before the bot spends one on a G2 race. G1 is always allowed."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-glow-stick-final-reserve"
                                                        value={scenarioOverrides.trackblazerGlowStickFinalReserve}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerGlowStickFinalReserve}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerGlowStickFinalReserve", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerGlowStickFinalReserve", value)}
                                                        min={0}
                                                        max={3}
                                                        step={1}
                                                        label="Glow Stick Final-Day Reserve"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Glow Sticks held back for Day 75 (the Final). Pre-final-day races only spend sticks above this reserve."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-glow-stick-min-fans"
                                                        value={scenarioOverrides.trackblazerGlowStickMinFans}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerGlowStickMinFans}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerGlowStickMinFans", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerGlowStickMinFans", value)}
                                                        min={0}
                                                        max={30000}
                                                        step={1000}
                                                        label="Glow Stick Minimum Fans"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Minimum projected fan gain on a race before the bot uses a Glow Stick."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-enable-irregular-training"
                                                        checked={scenarioOverrides.trackblazerEnableIrregularTraining}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableIrregularTraining", checked)}
                                                        label="Enable Irregular Training"
                                                        description="When enabled, the bot will occasionally check for highly profitable training sessions before opting for extra races."
                                                    />
                                                </View>

                                                {scenarioOverrides.trackblazerEnableIrregularTraining && (
                                                    <>
                                                    <View style={styles.section}>
                                                        <CustomSlider
                                                            searchId="trackblazer-irregular-training-min-stat-gain"
                                                            value={scenarioOverrides.trackblazerIrregularTrainingMinStatGain}
                                                            placeholder={defaultSettings.scenarioOverrides.trackblazerIrregularTrainingMinStatGain}
                                                            onValueChange={(value) => updateOverrideSetting("trackblazerIrregularTrainingMinStatGain", value)}
                                                            onSlidingComplete={(value) => updateOverrideSetting("trackblazerIrregularTrainingMinStatGain", value)}
                                                            min={20}
                                                            max={100}
                                                            step={5}
                                                            label="Minimum Main Stat Gain for Irregular Training (Default)"
                                                            labelUnit=""
                                                            showValue={true}
                                                            showLabels={true}
                                                            description="Default minimum main stat gain when no race-grade or unique-race override applies."
                                                        />
                                                    </View>
                                                    <View style={styles.section}>
                                                        <Text style={{ fontSize: 16, color: colors.foreground, marginBottom: 8 }}>Minimum Stat Gain by Race Grade</Text>
                                                        <Text style={{ fontSize: 13, color: colors.mutedForeground, marginBottom: 12 }}>
                                                            Overrides the default threshold on days with a mapped G1/G2/G3 race. Unique race overrides take priority.
                                                        </Text>
                                                        {(["G1", "G2", "G3"] as const).map((grade) => (
                                                            <View key={grade} style={{ marginBottom: 12 }}>
                                                                <CustomSlider
                                                                    searchId={`trackblazer-irregular-training-min-stat-gain-${grade.toLowerCase()}`}
                                                                    value={irregularMinGainByGrade[grade] ?? scenarioOverrides.trackblazerIrregularTrainingMinStatGain}
                                                                    placeholder={scenarioOverrides.trackblazerIrregularTrainingMinStatGain}
                                                                    onValueChange={(value) => updateIrregularMinGainForGrade(grade, value)}
                                                                    onSlidingComplete={(value) => updateIrregularMinGainForGrade(grade, value)}
                                                                    min={20}
                                                                    max={100}
                                                                    step={5}
                                                                    label={`${grade} Minimum Main Stat Gain`}
                                                                    labelUnit=""
                                                                    showValue={true}
                                                                    showLabels={true}
                                                                />
                                                            </View>
                                                        ))}
                                                    </View>
                                                    <View style={styles.section}>
                                                        <CustomCheckbox
                                                            searchId="trackblazer-enable-wit-irregular-training"
                                                            checked={scenarioOverrides.trackblazerEnableWitIrregularTraining}
                                                            onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableWitIrregularTraining", checked)}
                                                            label="Enable Wit Irregular Training"
                                                            description="When enabled, always scan every training tab even if Speed fails the pre-check. Wit may qualify with safe failure, Good-Luck Charm (when Wit is in top-3 stat priority or Charm on Low-Priority Wit is enabled in Training settings), or energy mitigation."
                                                        />
                                                    </View>
                                                    <View style={styles.section}>
                                                        <CustomCheckbox
                                                            searchId="trackblazer-irregular-training-include-active-megaphone-bonus"
                                                            checked={scenarioOverrides.trackblazerIrregularTrainingIncludeActiveMegaphoneBonus}
                                                            onCheckedChange={(checked) => updateOverrideSetting("trackblazerIrregularTrainingIncludeActiveMegaphoneBonus", checked)}
                                                            label="Include Active Megaphone in Irregular Gain"
                                                            description="When enabled, irregular qualification counts the bonus from an already-active megaphone. Megaphone upgrades and ankle weights on irregular turns still require base (pre-item) gain to meet irregular thresholds (per grade, unique race, failure mitigation, and reserves). Reset Whistles are never used on irregular turns."
                                                        />
                                                    </View>
                                                    <View style={styles.section}>
                                                        <CustomCheckbox
                                                            searchId="trackblazer-enable-irregular-training-with-agenda"
                                                            checked={scenarioOverrides.trackblazerEnableIrregularTrainingWithAgenda}
                                                            onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableIrregularTrainingWithAgenda", checked)}
                                                            label="Allow Irregular Training With User Agenda"
                                                            description="When user in-game agenda is enabled, evaluate irregular training on agenda race days using the mapped schedule and selected grades."
                                                        />
                                                    </View>
                                                    {scenarioOverrides.trackblazerEnableIrregularTrainingWithAgenda && (
                                                        <>
                                                        <View style={styles.section}>
                                                            <CustomCheckbox
                                                                searchId="trackblazer-enable-irregular-training-agenda-pre-debut"
                                                                checked={scenarioOverrides.trackblazerEnableIrregularTrainingAgendaPreDebut}
                                                                onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableIrregularTrainingAgendaPreDebut", checked)}
                                                                label="Allow Agenda Irregular During Pre-Debut"
                                                                description="When enabled, pre-debut turns (1–11) may be included in agenda irregular evaluation and autofill. Off by default — agenda is usually loaded after debut."
                                                            />
                                                        </View>
                                                        <View style={styles.section}>
                                                            <CustomCheckbox
                                                                searchId="trackblazer-enable-irregular-training-agenda-pre-op"
                                                                checked={scenarioOverrides.trackblazerEnableIrregularTrainingAgendaPreOp}
                                                                onCheckedChange={(checked) => updateOverrideSetting("trackblazerEnableIrregularTrainingAgendaPreOp", checked)}
                                                                label="Allow Pre-Op / OP Agenda Irregular Training"
                                                                description="When enabled, Pre-Op and OP agenda race days may evaluate irregular training using the same minimum stat gain threshold as G3."
                                                            />
                                                        </View>
                                                        <View style={styles.section}>
                                                            <Text style={{ fontSize: 16, color: colors.foreground, marginBottom: 8 }}>Irregular Training Agenda Grades</Text>
                                                            <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                                                                {["G1", "G2", "G3"].map((grade) => (
                                                                    <View
                                                                        key={grade}
                                                                        style={{
                                                                            padding: 10,
                                                                            borderRadius: 8,
                                                                            marginRight: 8,
                                                                            marginBottom: 8,
                                                                            backgroundColor: scenarioOverrides.trackblazerIrregularTrainingAgendaGrades.includes(grade) ? colors.primary : colors.card,
                                                                        }}
                                                                        onTouchEnd={() => {
                                                                            const current = scenarioOverrides.trackblazerIrregularTrainingAgendaGrades
                                                                            if (current.includes(grade)) {
                                                                                updateOverrideSetting("trackblazerIrregularTrainingAgendaGrades", current.filter((g) => g !== grade))
                                                                            } else {
                                                                                updateOverrideSetting("trackblazerIrregularTrainingAgendaGrades", [...current, grade])
                                                                            }
                                                                        }}
                                                                    >
                                                                        <Text style={{ fontSize: 14, fontWeight: "600", color: scenarioOverrides.trackblazerIrregularTrainingAgendaGrades.includes(grade) ? colors.background : colors.foreground }}>{grade}</Text>
                                                                    </View>
                                                                ))}
                                                            </View>
                                                        </View>
                                                        <AgendaIrregularScheduleEditor updateOverrideSetting={updateOverrideSetting} />
                                                        </>
                                                    )}
                                                    </>
                                                )}

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-whistle-forces-training"
                                                        checked={scenarioOverrides.trackblazerWhistleForcesTraining}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerWhistleForcesTraining", checked)}
                                                        label="Reset Whistle Forces Training"
                                                        description="Whether or not using a Reset Whistle means it can ignore the failure chance thresholds in the Training Settings page. If enabled, the bot will pick the best available training after usage even if it's risky."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-save-reset-whistles-for-summer"
                                                        checked={scenarioOverrides.trackblazerSaveResetWhistlesForSummer}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerSaveResetWhistlesForSummer", checked)}
                                                        label="Save Reset Whistles for Summer"
                                                        description="When enabled, Reset Whistles are only used during Summer training (Classic turns 37–40, Senior turns 61–64). When disabled, whistles may also be used on Senior turns after 60 (except the Finale save window below)."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomCheckbox
                                                        searchId="trackblazer-save-reset-whistles-for-finale"
                                                        checked={scenarioOverrides.trackblazerSaveResetWhistlesForFinale}
                                                        onCheckedChange={(checked) => updateOverrideSetting("trackblazerSaveResetWhistlesForFinale", checked)}
                                                        label="Save Reset Whistles for Finale"
                                                        description="When enabled, do not use Reset Whistles on Senior turns 65–72 (after the second Summer through pre-Finale). Whistles are allowed again during Finale turns 73–75. Classic/Senior Summer windows are unchanged."
                                                        className="my-2"
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-whistle-priority-min-rainbow"
                                                        value={scenarioOverrides.trackblazerWhistlePriorityMinRainbow}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerWhistlePriorityMinRainbow}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerWhistlePriorityMinRainbow", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerWhistlePriorityMinRainbow", value)}
                                                        min={0}
                                                        max={5}
                                                        step={1}
                                                        label="Whistle Priority Min Rainbows"
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="During Summer and Finale (73–75): train a top-3 priority stat with at least this many rainbows instead of using a Reset Whistle. Requires at least one qualifying orange Uma/Riko/Sirius friendship bar on screen first. Summer uses Summer Training Priority; Finale uses Stat Prioritization. 0 disables. Evaluated before Good-Luck Charm."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-whistle-post-shuffle-min-failure"
                                                        value={scenarioOverrides.trackblazerWhistlePostShuffleMinFailure}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerWhistlePostShuffleMinFailure}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerWhistlePostShuffleMinFailure", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerWhistlePostShuffleMinFailure", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Post-Whistle Recovery Min Failure %"
                                                        labelUnit="%"
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="During Summer and Finale only: after a Reset Whistle reshuffle, recover energy instead of training when failure is at/above this and main gain is below Post-Whistle Recovery Min Main Gain. Uses pre-charm failure values. 0 disables."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-whistle-post-shuffle-min-main-gain"
                                                        value={scenarioOverrides.trackblazerWhistlePostShuffleMinMainGain}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerWhistlePostShuffleMinMainGain}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerWhistlePostShuffleMinMainGain", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerWhistlePostShuffleMinMainGain", value)}
                                                        min={0}
                                                        max={100}
                                                        step={5}
                                                        label="Post-Whistle Recovery Min Main Gain"
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Paired with Post-Whistle Recovery Min Failure %. During Summer and Finale only: recover when both conditions are met after a whistle reshuffle, before Good-Luck Charm is applied. 0 disables."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <CustomSlider
                                                        searchId="trackblazer-shop-check-frequency"
                                                        value={scenarioOverrides.trackblazerShopCheckFrequency}
                                                        placeholder={defaultSettings.scenarioOverrides.trackblazerShopCheckFrequency}
                                                        onValueChange={(value) => updateOverrideSetting("trackblazerShopCheckFrequency", value)}
                                                        onSlidingComplete={(value) => updateOverrideSetting("trackblazerShopCheckFrequency", value)}
                                                        min={1}
                                                        max={4}
                                                        step={1}
                                                        label="Shop Check Frequency"
                                                        labelUnit=""
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Sets the frequency of shop checks after races in the Trackblazer scenario. 1 = every race, 2 = 1 day after, 3 = 2 days after, etc."
                                                    />
                                                </View>

                                                <View style={styles.section}>
                                                    <Text style={{ fontSize: 16, color: colors.foreground, marginBottom: 8 }}>Race Grades to check Shop Afterwards</Text>
                                                    <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7, marginBottom: 12 }}>
                                                        Select which race grades should trigger a shop check after the race in the Trackblazer scenario.
                                                    </Text>
                                                    <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                                                        {["G1", "G2", "G3"].map((grade) => (
                                                            <View
                                                                key={grade}
                                                                style={{
                                                                    padding: 10,
                                                                    borderRadius: 8,
                                                                    marginRight: 8,
                                                                    marginBottom: 8,
                                                                    backgroundColor: scenarioOverrides.trackblazerShopCheckGrades.includes(grade) ? colors.primary : colors.card,
                                                                }}
                                                                onTouchEnd={() => {
                                                                    const currentGrades = scenarioOverrides.trackblazerShopCheckGrades
                                                                    if (currentGrades.includes(grade)) {
                                                                        updateOverrideSetting(
                                                                            "trackblazerShopCheckGrades",
                                                                            currentGrades.filter((g) => g !== grade)
                                                                        )
                                                                    } else {
                                                                        updateOverrideSetting("trackblazerShopCheckGrades", [...currentGrades, grade])
                                                                    }
                                                                }}
                                                            >
                                                                <Text
                                                                    style={{
                                                                        fontSize: 14,
                                                                        fontWeight: "600",
                                                                        color: scenarioOverrides.trackblazerShopCheckGrades.includes(grade) ? colors.background : colors.foreground,
                                                                    }}
                                                                >
                                                                    {grade}
                                                                </Text>
                                                            </View>
                                                        ))}
                                                    </View>
                                                </View>

                                                <View style={styles.section}>
                                                    <Text style={{ fontSize: 16, color: colors.foreground, marginBottom: 8 }}>Race Grades to use Race Retries on</Text>
                                                    <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7, marginBottom: 12 }}>
                                                        Select which race grades should allow using a Race Retry in the Trackblazer scenario.
                                                    </Text>
                                                    <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                                                        {["G1", "G2", "G3"].map((grade) => (
                                                            <View
                                                                key={grade}
                                                                style={{
                                                                    padding: 10,
                                                                    borderRadius: 8,
                                                                    marginRight: 8,
                                                                    marginBottom: 8,
                                                                    backgroundColor: scenarioOverrides.trackblazerRetryRacesBeforeFinalGrades.includes(grade) ? colors.primary : colors.card,
                                                                }}
                                                                onTouchEnd={() => {
                                                                    const currentGrades = scenarioOverrides.trackblazerRetryRacesBeforeFinalGrades
                                                                    if (currentGrades.includes(grade)) {
                                                                        updateOverrideSetting(
                                                                            "trackblazerRetryRacesBeforeFinalGrades",
                                                                            currentGrades.filter((g) => g !== grade)
                                                                        )
                                                                    } else {
                                                                        updateOverrideSetting("trackblazerRetryRacesBeforeFinalGrades", [...currentGrades, grade])
                                                                    }
                                                                }}
                                                            >
                                                                <Text
                                                                    style={{
                                                                        fontSize: 14,
                                                                        fontWeight: "600",
                                                                        color: scenarioOverrides.trackblazerRetryRacesBeforeFinalGrades.includes(grade) ? colors.background : colors.foreground,
                                                                    }}
                                                                >
                                                                    {grade}
                                                                </Text>
                                                            </View>
                                                        ))}
                                                    </View>
                                                </View>

                                                <View style={styles.section}>
                                                    <Text style={{ fontSize: 16, color: colors.foreground, marginBottom: 8 }}>Preferred Track Distances</Text>
                                                    <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7, marginBottom: 12 }}>
                                                        Select preferred track distances for extra race selection. Matching races will be prioritized. Leave empty for no preference.
                                                    </Text>
                                                    <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                                                        {["Sprint", "Mile", "Medium", "Long"].map((distance) => (
                                                            <View
                                                                key={distance}
                                                                style={{
                                                                    padding: 10,
                                                                    borderRadius: 8,
                                                                    marginRight: 8,
                                                                    marginBottom: 8,
                                                                    backgroundColor: scenarioOverrides.trackblazerPreferredDistances.includes(distance) ? colors.primary : colors.card,
                                                                }}
                                                                onTouchEnd={() => {
                                                                    const current = scenarioOverrides.trackblazerPreferredDistances
                                                                    if (current.includes(distance)) {
                                                                        updateOverrideSetting(
                                                                            "trackblazerPreferredDistances",
                                                                            current.filter((d) => d !== distance)
                                                                        )
                                                                    } else {
                                                                        updateOverrideSetting("trackblazerPreferredDistances", [...current, distance])
                                                                    }
                                                                }}
                                                            >
                                                                <Text
                                                                    style={{
                                                                        fontSize: 14,
                                                                        fontWeight: "600",
                                                                        color: scenarioOverrides.trackblazerPreferredDistances.includes(distance) ? colors.background : colors.foreground,
                                                                    }}
                                                                >
                                                                    {distance}
                                                                </Text>
                                                            </View>
                                                        ))}
                                                    </View>
                                                </View>

                                                <View style={styles.section}>
                                                    <Text style={{ fontSize: 16, color: colors.foreground, marginBottom: 8 }}>Preferred Track Surfaces</Text>
                                                    <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7, marginBottom: 12 }}>
                                                        Select preferred track surfaces for extra race selection. Matching races will be prioritized. Leave empty for no preference.
                                                    </Text>
                                                    <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                                                        {["Turf", "Dirt"].map((surface) => (
                                                            <View
                                                                key={surface}
                                                                style={{
                                                                    padding: 10,
                                                                    borderRadius: 8,
                                                                    marginRight: 8,
                                                                    marginBottom: 8,
                                                                    backgroundColor: scenarioOverrides.trackblazerPreferredSurfaces.includes(surface) ? colors.primary : colors.card,
                                                                }}
                                                                onTouchEnd={() => {
                                                                    const current = scenarioOverrides.trackblazerPreferredSurfaces
                                                                    if (current.includes(surface)) {
                                                                        updateOverrideSetting(
                                                                            "trackblazerPreferredSurfaces",
                                                                            current.filter((s) => s !== surface)
                                                                        )
                                                                    } else {
                                                                        updateOverrideSetting("trackblazerPreferredSurfaces", [...current, surface])
                                                                    }
                                                                }}
                                                            >
                                                                <Text
                                                                    style={{
                                                                        fontSize: 14,
                                                                        fontWeight: "600",
                                                                        color: scenarioOverrides.trackblazerPreferredSurfaces.includes(surface) ? colors.background : colors.foreground,
                                                                    }}
                                                                >
                                                                    {surface}
                                                                </Text>
                                                            </View>
                                                        ))}
                                                    </View>
                                                </View>

                                                <Divider style={{ marginVertical: 16 }} />

                                                <View style={styles.section}>
                                                    <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 12, gap: 12 }}>
                                                        <View style={{ flex: 1 }}>
                                                            <Text style={{ fontSize: 16, color: colors.foreground }}>Items to Exclude from Shop</Text>
                                                            <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7, marginTop: 4 }}>
                                                                Selected {scenarioOverrides.trackblazerExcludedItems.length} / {Object.keys(trackblazerIcons).length} items
                                                            </Text>
                                                        </View>
                                                        <View style={{ flexDirection: "row", gap: 8 }}>
                                                            <CustomButton icon={<Trash2 size={16} color={colors.foreground} />} onPress={() => updateOverrideSetting("trackblazerExcludedItems", [])}>
                                                                Clear
                                                            </CustomButton>
                                                        </View>
                                                    </View>

                                                    <Text style={{ fontSize: 14, color: colors.foreground, opacity: 0.7, marginBottom: 12 }}>
                                                        Select items that the bot will never purchase from the shop in the Trackblazer scenario.
                                                    </Text>

                                                    <View style={{ marginBottom: 16 }}>
                                                        <Input
                                                            style={{
                                                                borderWidth: 1,
                                                                borderColor: colors.border,
                                                                borderRadius: 8,
                                                                padding: 12,
                                                                fontSize: 16,
                                                                color: colors.foreground,
                                                                backgroundColor: colors.background,
                                                                marginBottom: 12,
                                                            }}
                                                            value={searchQuery}
                                                            onChangeText={setSearchQuery}
                                                            placeholder="Search items by name..."
                                                        />
                                                        <View style={{ height: 400 }}>
                                                            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={true}>
                                                                {filteredItems.map((itemName) => (
                                                                    <TouchableOpacity key={itemName} onPress={() => handleItemPress(itemName)} style={styles.itemContainer}>
                                                                        <View style={{ flexDirection: "row", alignItems: "center", gap: 8, flex: 1 }}>
                                                                            <Image source={trackblazerIcons[itemName].icon} style={{ width: 48, height: 48, marginRight: 8 }} />
                                                                            <View style={{ flex: 1 }}>
                                                                                <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground }}>{itemName}</Text>
                                                                                <Text style={{ fontSize: 12, color: colors.foreground, opacity: 0.6, marginTop: 2 }}>
                                                                                    {trackblazerIcons[itemName].description}
                                                                                </Text>
                                                                            </View>
                                                                            {scenarioOverrides.trackblazerExcludedItems.includes(itemName) && <CircleCheckBig size={18} color={"green"} />}
                                                                        </View>
                                                                    </TouchableOpacity>
                                                                ))}
                                                            </ScrollView>
                                                        </View>
                                                    </View>
                                                </View>
                                            </>
                                        ),
                                    },
                                ]}
                            />
                        )}
                    </View>
                </ScrollView>
            </SearchPageProvider>
        </View>
    )
}

export default ScenarioOverridesSettings
