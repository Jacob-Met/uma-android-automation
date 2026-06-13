import { useMemo, useContext, useRef, useCallback } from "react"
import { View, Text, TextInput, ScrollView, StyleSheet } from "react-native"
import { useNavigation } from "@react-navigation/native"
import { useTheme } from "../../context/ThemeContext"
import { RacingContext, ScenarioOverridesContext, defaultSettings, Settings } from "../../context/BotStateContext"
import { SearchPageProvider } from "../../context/SearchPageContext"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSlider from "../../components/CustomSlider"
import CustomSelect from "../../components/CustomSelect"
import CustomTitle from "../../components/CustomTitle"
import { Input } from "../../components/ui/input"
import NavigationLink from "../../components/NavigationLink"
import PageHeader from "../../components/PageHeader"
import WarningContainer from "../../components/WarningContainer"
import SearchableItem from "../../components/SearchableItem"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import {
    buildAgendaSwitchUpdate,
    getAgendaScheduleKey,
    parseUserAgendaCustomTitles,
} from "../../lib/agendaIrregularSchedule"

/** Available race strategy values for blanket and per-distance pickers. */
const RACE_STRATEGY_OPTIONS = [
    { value: "Default", label: "Default" },
    { value: "Auto", label: "Auto" },
    { value: "Front", label: "Front" },
    { value: "Pace", label: "Pace" },
    { value: "Late", label: "Late" },
    { value: "End", label: "End" },
] as const

const PER_DISTANCE_BUCKETS = ["Short", "Mile", "Medium", "Long"] as const

/**
 * The Racing Settings page.
 * Provides configuration for fan farming, race retries, mandatory race handling, race strategies (Junior vs. Original),
 * force racing, in-game race agenda, and navigation to the Smart Race Solver Settings sub-page.
 */
const RacingSettings = () => {
    usePerformanceLogging("RacingSettings")
    const { colors } = useTheme()
    const navigation = useNavigation()
    const { racing, updateRacing } = useContext(RacingContext)
    const { scenarioOverrides, updateScenarioOverrides } = useContext(ScenarioOverridesContext)
    const scrollViewRef = useRef<ScrollView>(null)

    // Merge current racing settings with defaults to handle missing properties.
    const racingSettings = { ...defaultSettings.racing, ...racing }
    const {
        enableFarmingFans,
        ignoreConsecutiveRaceWarning,
        ignoreLowEnergyRacingBlock,
        daysToRunExtraRaces,
        disableRaceRetries,
        enableFreeRaceRetry,
        enableCompleteCareerOnFailure,
        enableStopOnMandatoryRaces,
        enableForceRacing,
        juniorYearRaceStrategy,
        originalRaceStrategy,
        enablePerDistanceStrategy,
        juniorYearPerDistanceStrategies,
        originalPerDistanceStrategies,
        enableUserInGameRaceAgenda,
        limitRacesToInGameAgenda,
        skipSummerTrainingForAgenda,
        customAgendaTitle,
        userAgendaCustomTitles,
        enableSkipRaceSimulation,
        agendaWaitDelay,
        raceStrategyWaitDelay,
    } = racingSettings

    /**
     * Update a racing setting with special handling for the in-game race agenda.
     * When the in-game race agenda is enabled, it automatically disables the Farming Fans and Smart Race Solver settings to prevent conflicts.
     * @param key The key of the setting to update.
     * @param value The value to set the setting to.
     */
    const updateRacingSetting = useCallback(
        (key: keyof Settings["racing"], value: any) => {
            if (key === "enableUserInGameRaceAgenda" && value) {
                updateRacing((prev) => ({
                    // Disable Farming Fans and the Smart Race Solver when User In Game Race Agenda is enabled.
                    ...prev,
                    enableFarmingFans: false,
                    enableUserInGameRaceAgenda: true,
                    enableSmartRaceSolver: false,
                }))
            } else {
                updateRacing({ [key]: value } as Partial<Settings["racing"]>)
            }
        },
        [updateRacing]
    )

    const handleSelectedAgendaChange = useCallback(
        (nextSlot: string | null) => {
            if (!nextSlot || nextSlot === racingSettings.selectedUserAgenda) {
                return
            }

            const switchUpdate = buildAgendaSwitchUpdate({
                currentSlot: racingSettings.selectedUserAgenda,
                nextSlot,
                currentCustomTitle: customAgendaTitle,
                customTitlesJson: userAgendaCustomTitles,
                schedulesJson: scenarioOverrides.trackblazerAgendaIrregularSchedules,
            })

            updateRacing({
                selectedUserAgenda: switchUpdate.selectedUserAgenda,
                customAgendaTitle: switchUpdate.customAgendaTitle,
                userAgendaCustomTitles: switchUpdate.userAgendaCustomTitles,
            })

            if (switchUpdate.trackblazerAgendaIrregularSchedules) {
                updateScenarioOverrides({
                    trackblazerAgendaIrregularSchedules: switchUpdate.trackblazerAgendaIrregularSchedules,
                })
            }
        },
        [
            customAgendaTitle,
            racingSettings.selectedUserAgenda,
            scenarioOverrides.trackblazerAgendaIrregularSchedules,
            updateRacing,
            updateScenarioOverrides,
            userAgendaCustomTitles,
        ]
    )

    const handleCustomAgendaTitleChange = useCallback(
        (text: string) => {
            const slot = getAgendaScheduleKey(racingSettings.selectedUserAgenda)
            const customTitles = parseUserAgendaCustomTitles(userAgendaCustomTitles)
            updateRacing({
                customAgendaTitle: text,
                userAgendaCustomTitles: JSON.stringify({ ...customTitles, [slot]: text }),
            })
        },
        [racingSettings.selectedUserAgenda, updateRacing, userAgendaCustomTitles]
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
                    marginBottom: 24,
                },
                inputContainer: {
                    marginBottom: 16,
                },
                inputLabel: {
                    fontSize: 16,
                    color: colors.foreground,
                    marginBottom: 8,
                },
                input: {
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 8,
                    padding: 12,
                    fontSize: 16,
                    color: colors.foreground,
                    backgroundColor: colors.background,
                },
                inputDescription: {
                    fontSize: 14,
                    color: colors.foreground,
                    opacity: 0.7,
                    marginTop: 4,
                },
                perDistanceGroupLabel: {
                    fontSize: 12,
                    fontWeight: "600",
                    letterSpacing: 1,
                    color: colors.foreground,
                    opacity: 0.6,
                    marginTop: 12,
                    marginBottom: 8,
                },
                perDistanceBody: {
                    gap: 8,
                    marginBottom: 8,
                },
                perDistanceItem: {
                    flexDirection: "row",
                    alignItems: "center",
                    gap: 12,
                },
                perDistanceDistanceLabel: {
                    flex: 1,
                    fontSize: 16,
                    color: colors.foreground,
                },
            }),
        [colors]
    )

    return (
        <View style={styles.root}>
            <PageHeader title="Racing Settings" />

            <SearchPageProvider page="RacingSettings" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                    <View className="m-1">
                        <View style={styles.section}>
                            <CustomCheckbox
                                searchId="enable-skip-race-simulation"
                                checked={enableSkipRaceSimulation}
                                onCheckedChange={(checked) => updateRacingSetting("enableSkipRaceSimulation", checked)}
                                label="Skip Race Simulation (View Results)"
                                description="When enabled (default), the bot always clicks View Results on the race prep screen and never taps Race Again. When disabled, it runs the full race simulation via the manual race button instead."
                                className="my-2"
                            />
                        </View>

                        <View style={styles.section}>
                            <CustomCheckbox
                                searchId="enable-farming-fans"
                                checked={enableFarmingFans}
                                onCheckedChange={(checked) => updateRacingSetting("enableFarmingFans", checked)}
                                label="Enable Farming Fans"
                                description="When enabled, the bot will start running extra races to gain fans."
                                className="my-2"
                            />
                        </View>

                        <SearchableItem id="days-to-run-extra-races" title="Days to Run Extra Races" description="Controls when extra races can be run using modulo arithmetic." style={styles.section}>
                            <Text style={styles.inputLabel}>Days to Run Extra Races</Text>
                            <Input
                                style={styles.input}
                                value={daysToRunExtraRaces.toString()}
                                onChangeText={(text) => {
                                    const value = parseInt(text) || 1
                                    updateRacingSetting("daysToRunExtraRaces", value)
                                }}
                                keyboardType="numeric"
                                placeholder="5"
                            />
                            <Text style={styles.inputDescription}>
                                Controls when extra races can be run using modulo arithmetic. For example, if set to 5, extra races will only be available on days 5, 10, 15, etc. (when current day % 5
                                = 0). Note: This setting has no effect when Smart Race Solver is enabled, as the solver controls race scheduling based on epithet completion analysis.
                            </Text>
                        </SearchableItem>

                        <View style={styles.section}>
                            <CustomCheckbox
                                searchId="ignore-consecutive-race-warning"
                                checked={ignoreConsecutiveRaceWarning}
                                onCheckedChange={(checked) => updateRacingSetting("ignoreConsecutiveRaceWarning", checked)}
                                label="Ignore Consecutive Race Warning"
                                description="When enabled, the bot will ignore the warning popup about consecutive races and continue racing."
                                className="my-2"
                            />
                        </View>

                        <View style={styles.section}>
                            <CustomCheckbox
                                searchId="ignore-low-energy-racing-block"
                                checked={ignoreLowEnergyRacingBlock}
                                onCheckedChange={(checked) => updateRacingSetting("ignoreLowEnergyRacingBlock", checked)}
                                label="Ignore Low Energy Racing Block"
                                description="When enabled, the Trackblazer bot will not block racing when energy is critically low (<=1%) with 3+ consecutive races. Useful to avoid the larger -80 penalty from skipping derby races."
                                className="my-2"
                            />
                        </View>

                        <CustomTitle title="Mandatory Race Settings" />

                        <View style={styles.inputContainer}>
                            <CustomCheckbox
                                searchId="disable-race-retries"
                                checked={disableRaceRetries}
                                onCheckedChange={(checked) => updateRacingSetting("disableRaceRetries", checked)}
                                label="Disable Race Retries"
                                description="When enabled, the bot will not retry mandatory races if they fail and will stop."
                                className="my-2"
                            />
                            <CustomCheckbox
                                searchId="enable-free-race-retry"
                                searchCondition={disableRaceRetries}
                                parentId="disable-race-retries"
                                checked={enableFreeRaceRetry}
                                onCheckedChange={(checked) => updateRacingSetting("enableFreeRaceRetry", checked)}
                                label="Allow Daily Free Race Retry"
                                description="When enabled, the bot will attempt to retry a failed mandatory race only if the daily free race retry is available."
                                className="my-2"
                            />
                            <CustomCheckbox
                                searchId="enable-complete-career-on-failure"
                                checked={enableCompleteCareerOnFailure}
                                onCheckedChange={(checked) => updateRacingSetting("enableCompleteCareerOnFailure", checked)}
                                label="Complete Career on Failure"
                                description="When enabled, the bot will proceed to the career completion screen when a mandatory race is failed and it has run out of retries (or if retries are disabled). This is as opposed to the bot stopping at the Try Again dialog."
                                className="my-2"
                            />
                            <CustomCheckbox
                                searchId="enable-stop-on-mandatory-races"
                                checked={enableStopOnMandatoryRaces}
                                onCheckedChange={(checked) => updateRacingSetting("enableStopOnMandatoryRaces", checked)}
                                label="Stop on Mandatory Races"
                                description="When enabled, the bot will automatically stop when it encounters a mandatory race, allowing you to manually handle them."
                                className="my-2"
                            />
                        </View>

                        <CustomTitle title="Strategy" />

                        <View style={styles.inputContainer}>
                            <CustomCheckbox
                                searchId="enable-per-distance-strategy"
                                checked={enablePerDistanceStrategy}
                                onCheckedChange={(checked) => updateRacingSetting("enablePerDistanceStrategy", checked)}
                                label="Per-Distance Strategy"
                                description="When enabled, set a different running style for each track distance (Short, Mile, Medium, Long). Applies to extra races and mandatory/debut/goal races."
                                className="my-2"
                            />
                        </View>

                        {!enablePerDistanceStrategy ? (
                            <>
                                <View style={styles.inputContainer}>
                                    <Text style={styles.inputLabel}>Junior Year Race Strategy</Text>
                                    <CustomSelect
                                        searchId="junior-year-race-strategy"
                                        searchTitle="Junior Year Race Strategy"
                                        searchDescription="The race strategy to use for all races during Junior Year."
                                        options={[...RACE_STRATEGY_OPTIONS]}
                                        value={juniorYearRaceStrategy}
                                        onValueChange={(value) => updateRacingSetting("juniorYearRaceStrategy", value)}
                                        placeholder="Select strategy"
                                    />
                                    <Text style={styles.inputDescription}>
                                        The race strategy to use for all races during Junior Year. If Auto is selected, the bot will auto-select the best strategy that puts them closest to the front
                                        of the pack.
                                    </Text>
                                </View>
                                <View style={styles.inputContainer}>
                                    <Text style={styles.inputLabel}>Original Race Strategy</Text>
                                    <CustomSelect
                                        searchId="original-race-strategy"
                                        searchTitle="Original Race Strategy"
                                        searchDescription="The race strategy to reset to after Junior Year. The bot will use this strategy for races in Year 2 and beyond."
                                        options={[...RACE_STRATEGY_OPTIONS]}
                                        value={originalRaceStrategy}
                                        onValueChange={(value) => updateRacingSetting("originalRaceStrategy", value)}
                                        placeholder="Select strategy"
                                    />
                                    <Text style={styles.inputDescription}>
                                        The race strategy to reset to after Junior Year. The bot will use this strategy for races in Year 2 and beyond. If Auto is selected, the bot will auto-select
                                        the best strategy that puts them closest to the front of the pack. If Default is selected, the bot will not change whatever strategy is currently in effect.
                                    </Text>
                                </View>
                            </>
                        ) : (
                            <>
                                <View style={styles.inputContainer}>
                                    <Text style={styles.inputDescription}>
                                        Set a different running style for each distance. Auto picks the best style. Default leaves the in-game style unchanged. The bot re-applies the correct style
                                        when the distance or year bucket changes.
                                    </Text>
                                </View>
                                <View style={styles.inputContainer}>
                                    <Text style={styles.perDistanceGroupLabel}>JUNIOR YEAR</Text>
                                    <View style={styles.perDistanceBody}>
                                        {PER_DISTANCE_BUCKETS.map((distance) => (
                                            <View key={`junior-${distance}`} style={styles.perDistanceItem}>
                                                <Text style={styles.perDistanceDistanceLabel}>{distance}</Text>
                                                <CustomSelect
                                                    searchId={`junior-strategy-${distance.toLowerCase()}`}
                                                    searchTitle={`Junior Year ${distance} Distance Strategy`}
                                                    searchDescription={`The race strategy to use for ${distance.toLowerCase()} distance races during Junior Year.`}
                                                    width={140}
                                                    options={[...RACE_STRATEGY_OPTIONS]}
                                                    value={juniorYearPerDistanceStrategies?.[distance] ?? "Default"}
                                                    onValueChange={(value) => {
                                                        const updated = { ...juniorYearPerDistanceStrategies, [distance]: value }
                                                        updateRacingSetting("juniorYearPerDistanceStrategies", updated)
                                                    }}
                                                    placeholder="Default"
                                                />
                                            </View>
                                        ))}
                                    </View>
                                </View>
                                <View style={styles.inputContainer}>
                                    <Text style={[styles.perDistanceGroupLabel, { marginTop: 0 }]}>CLASSIC AND SENIOR YEAR</Text>
                                    <View style={styles.perDistanceBody}>
                                        {PER_DISTANCE_BUCKETS.map((distance) => (
                                            <View key={`original-${distance}`} style={styles.perDistanceItem}>
                                                <Text style={styles.perDistanceDistanceLabel}>{distance}</Text>
                                                <CustomSelect
                                                    searchId={`original-strategy-${distance.toLowerCase()}`}
                                                    searchTitle={`Original ${distance} Distance Strategy`}
                                                    searchDescription={`The race strategy to use for ${distance.toLowerCase()} distance races in Year 2 and beyond.`}
                                                    width={140}
                                                    options={[...RACE_STRATEGY_OPTIONS]}
                                                    value={originalPerDistanceStrategies?.[distance] ?? "Default"}
                                                    onValueChange={(value) => {
                                                        const updated = { ...originalPerDistanceStrategies, [distance]: value }
                                                        updateRacingSetting("originalPerDistanceStrategies", updated)
                                                    }}
                                                    placeholder="Default"
                                                />
                                            </View>
                                        ))}
                                    </View>
                                </View>
                                <View style={styles.inputContainer}>
                                    <CustomSlider
                                        searchId="race-strategy-wait-delay"
                                        searchCondition={enablePerDistanceStrategy}
                                        parentId="enable-per-distance-strategy"
                                        value={raceStrategyWaitDelay}
                                        placeholder={defaultSettings.racing.raceStrategyWaitDelay}
                                        onValueChange={(value) => updateRacingSetting("raceStrategyWaitDelay", value)}
                                        onSlidingComplete={(value) => updateRacingSetting("raceStrategyWaitDelay", value)}
                                        min={0}
                                        max={3}
                                        step={0.05}
                                        label="Per-Distance Race Strategy Wait Delay"
                                        labelUnit="s"
                                        showValue={true}
                                        showLabels={true}
                                        description="Extra wait after opening the running-style dialog when per-distance strategy is enabled. Increase if style buttons are clicked before the dialog finishes loading."
                                    />
                                </View>
                            </>
                        )}

                        <View style={styles.section}>
                            <CustomCheckbox
                                searchId="enable-force-racing"
                                checked={enableForceRacing}
                                onCheckedChange={(checked) => updateRacingSetting("enableForceRacing", checked)}
                                label="Force Racing"
                                description="When enabled, the bot will skip all training, rest, and mood recovery activities and focus exclusively on racing every day."
                                className="my-2"
                            />
                            {enableForceRacing && <WarningContainer>⚠️ Warning: Enabling this will override all other racing settings and they will be ignored.</WarningContainer>}
                        </View>

                        <View style={styles.section}>
                            <CustomSlider
                                searchId="agenda-wait-delay"
                                searchCondition={enableUserInGameRaceAgenda}
                                parentId="enable-user-in-game-race-agenda"
                                value={agendaWaitDelay}
                                placeholder={defaultSettings.racing.agendaWaitDelay}
                                onValueChange={(value) => updateRacingSetting("agendaWaitDelay", value)}
                                onSlidingComplete={(value) => updateRacingSetting("agendaWaitDelay", value)}
                                min={0}
                                max={3}
                                step={0.05}
                                label="Agenda Selection Wait Delay"
                                labelUnit="s"
                                showValue={true}
                                showLabels={true}
                                description="Extra pacing while opening the agenda tab, scrolling the agenda list, and confirming load. Does not affect general dialog or race flow delays."
                            />
                        </View>

                        <CustomCheckbox
                            searchId="enable-user-in-game-race-agenda"
                            checked={enableUserInGameRaceAgenda}
                            onCheckedChange={(checked) => updateRacingSetting("enableUserInGameRaceAgenda", checked)}
                            label="Enable User In-Game Race Agenda"
                            description={
                                "When enabled, the bot will load your selected in-game race agenda instead of using the racing plan settings. Note that this will disable the farming fans and racing plan settings."
                            }
                            style={{ marginBottom: 16 }}
                        />

                        <CustomSelect
                            searchId="user-in-game-race-agenda"
                            searchTitle="Select User In-Game Race Agenda"
                            searchDescription="The in-game race agenda to use when 'Enable User In-Game Race Agenda' is enabled."
                            searchCondition={enableUserInGameRaceAgenda}
                            parentId="enable-user-in-game-race-agenda"
                            placeholder="Select an Agenda"
                            width="100%"
                            options={[
                                { value: "Agenda 1", label: "Agenda 1" },
                                { value: "Agenda 2", label: "Agenda 2" },
                                { value: "Agenda 3", label: "Agenda 3" },
                                { value: "Agenda 4", label: "Agenda 4" },
                                { value: "Agenda 5", label: "Agenda 5" },
                                { value: "Agenda 6", label: "Agenda 6" },
                                { value: "Agenda 7", label: "Agenda 7" },
                                { value: "Agenda 8", label: "Agenda 8" },
                            ]}
                            value={racingSettings.selectedUserAgenda}
                            onValueChange={handleSelectedAgendaChange}
                            style={{ marginBottom: 16 }}
                        />

                        <SearchableItem
                            id="custom-agenda-title"
                            title="Custom Agenda Title"
                            description="If you renamed your agenda in-game, enter the custom title here. Leave blank to use the selected agenda name above."
                            condition={enableUserInGameRaceAgenda}
                            parentId="enable-user-in-game-race-agenda"
                            style={{ marginBottom: 16 }}
                        >
                            <Text style={styles.inputLabel}>Custom Agenda Title (Optional)</Text>
                            <Text style={styles.inputDescription}>If you renamed your agenda in-game, enter the custom title here. Leave blank to use the selected agenda name above.</Text>
                            <TextInput
                                style={[styles.input, !enableUserInGameRaceAgenda && { opacity: 0.5 }]}
                                value={customAgendaTitle}
                                onChangeText={handleCustomAgendaTitleChange}
                                placeholder="Leave blank to use selected agenda name"
                                placeholderTextColor={"gray"}
                                editable={enableUserInGameRaceAgenda}
                                autoCapitalize="none"
                                autoCorrect={false}
                            />
                        </SearchableItem>

                        <CustomCheckbox
                            searchId="limit-races-to-in-game-agenda"
                            searchCondition={enableUserInGameRaceAgenda}
                            parentId="enable-user-in-game-race-agenda"
                            checked={limitRacesToInGameAgenda}
                            onCheckedChange={(checked) => updateRacingSetting("limitRacesToInGameAgenda", checked)}
                            label="Limit Extra Races to Agenda"
                            description="When enabled, the bot will override the racing behavior of any scenario such that it will not run any extra races except for the ones scheduled by the selected user's in-game racing agenda."
                            style={{ marginBottom: 16 }}
                        />

                        <CustomCheckbox
                            searchId="skip-summer-training-for-agenda"
                            searchCondition={enableUserInGameRaceAgenda}
                            parentId="enable-user-in-game-race-agenda"
                            checked={skipSummerTrainingForAgenda}
                            onCheckedChange={(checked) => updateRacingSetting("skipSummerTrainingForAgenda", checked)}
                            label="Skip Summer Training for Agenda"
                            description="When enabled, the bot will perform scheduled races from the in-game racing agenda during Summer instead of prioritizing Summer training. Note that this requires 'Enable User In-Game Race Agenda' to be enabled."
                            style={{ marginBottom: 16 }}
                        />

                        <NavigationLink
                            title="Go to Unique Race Settings"
                            description="Type a race name, tap to configure per-race running style and irregular training overrides."
                            onPress={() => navigation.navigate("UniqueRaceSettings" as never)}
                            style={{ ...styles.section, marginTop: 0 }}
                        />

                        <NavigationLink
                            title="Go to Smart Race Solver Settings"
                            description="Plans every turn of the career to maximize score by targeting epithet rewards. The bot only races when the solver picks a race; other turns become training or rest."
                            disabled={!enableFarmingFans || enableForceRacing || enableUserInGameRaceAgenda}
                            disabledDescription="Farming Fans must be enabled and Force Racing and User In-Game Race Agenda settings must be disabled in order to use the Smart Race Solver."
                            onPress={() => navigation.navigate("SmartRaceSolverSettings" as never)}
                            style={{ ...styles.section, marginTop: 0 }}
                        />
                    </View>
                </ScrollView>
            </SearchPageProvider>
        </View>
    )
}

export default RacingSettings
