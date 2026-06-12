import { useMemo, useContext, useState, useRef, useCallback } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity, TextInput } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { RacingContext, ScenarioOverridesContext, defaultSettings } from "../../context/BotStateContext"
import { SearchPageProvider } from "../../context/SearchPageContext"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSelect from "../../components/CustomSelect"
import CustomSlider from "../../components/CustomSlider"
import CustomButton from "../../components/CustomButton"
import PageHeader from "../../components/PageHeader"
import SearchableItem from "../../components/SearchableItem"
import InfoContainer from "../../components/InfoContainer"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import racesData from "../../data/races.json"
import {
    UniqueRaceOverrideConfig,
    UniqueRaceOverridesMap,
    defaultUniqueRaceOverrideConfig,
    parseUniqueRaceOverrides,
    serializeUniqueRaceOverrides,
    uniqueRaceOverrideIsActive,
} from "../../lib/uniqueRaceOverrides"

const RACE_STRATEGY_OPTIONS = [
    { value: "Default", label: "Default (per-distance / blanket)" },
    { value: "Auto", label: "Auto" },
    { value: "Front", label: "Front" },
    { value: "Pace", label: "Pace" },
    { value: "Late", label: "Late" },
    { value: "End", label: "End" },
] as const

const ALL_RACE_NAMES = Object.keys(racesData as Record<string, unknown>).sort((a, b) => a.localeCompare(b))

const MAX_SUGGESTIONS = 25

/**
 * Unique race settings — type a race name, tap it, then configure per-race strategy and irregular training.
 */
const UniqueRaceSettings = () => {
    usePerformanceLogging("UniqueRaceSettings")
    const { colors } = useTheme()
    const { racing, updateRacing } = useContext(RacingContext)
    const { scenarioOverrides } = useContext(ScenarioOverridesContext)
    const scrollViewRef = useRef<ScrollView>(null)

    const globalMaxRetries =
        scenarioOverrides?.trackblazerMaxRetriesPerRace ?? defaultSettings.scenarioOverrides.trackblazerMaxRetriesPerRace

    const racingSettings = { ...defaultSettings.racing, ...racing }
    const { enableUniqueRaceStrategyOverrides, uniqueRaceStrategyOverrides } = racingSettings

    const overrides = useMemo(() => parseUniqueRaceOverrides(uniqueRaceStrategyOverrides), [uniqueRaceStrategyOverrides])
    const configuredRaceNames = useMemo(
        () => Object.keys(overrides).filter((name) => uniqueRaceOverrideIsActive(overrides[name])).sort((a, b) => a.localeCompare(b)),
        [overrides]
    )

    const [searchQuery, setSearchQuery] = useState("")
    const [selectedRaceName, setSelectedRaceName] = useState<string | null>(null)

    const selectedConfig = selectedRaceName ? overrides[selectedRaceName] ?? defaultUniqueRaceOverrideConfig() : null

    const persistOverrides = useCallback(
        (next: UniqueRaceOverridesMap) => {
            updateRacing({ uniqueRaceStrategyOverrides: serializeUniqueRaceOverrides(next) })
        },
        [updateRacing]
    )

    const updateRaceConfig = useCallback(
        (raceName: string, patch: Partial<UniqueRaceOverrideConfig>) => {
            const current = overrides[raceName] ?? defaultUniqueRaceOverrideConfig()
            const merged = { ...current, ...patch }
            const next = { ...overrides, [raceName]: merged }
            if (!uniqueRaceOverrideIsActive(merged)) {
                delete next[raceName]
            }
            persistOverrides(next)
        },
        [overrides, persistOverrides]
    )

    const handleSelectRace = useCallback((raceName: string) => {
        const trimmed = raceName.trim()
        if (!trimmed) return
        setSelectedRaceName(trimmed)
        setSearchQuery(trimmed)
    }, [])

    const handleRemoveRace = useCallback(
        (raceName: string) => {
            const next = { ...overrides }
            delete next[raceName]
            persistOverrides(next)
            if (selectedRaceName === raceName) {
                setSelectedRaceName(null)
            }
        },
        [overrides, persistOverrides, selectedRaceName]
    )

    const filteredSuggestions = useMemo(() => {
        const query = searchQuery.trim().toLowerCase()
        if (!query) {
            return ALL_RACE_NAMES.slice(0, MAX_SUGGESTIONS)
        }
        return ALL_RACE_NAMES.filter((name) => name.toLowerCase().includes(query)).slice(0, MAX_SUGGESTIONS)
    }, [searchQuery])

    const showUseTypedName =
        searchQuery.trim().length > 0 &&
        !ALL_RACE_NAMES.some((name) => name.toLowerCase() === searchQuery.trim().toLowerCase())

    const styles = useMemo(
        () =>
            StyleSheet.create({
                root: {
                    flex: 1,
                    margin: 10,
                    backgroundColor: colors.background,
                },
                section: {
                    marginBottom: 24,
                },
                label: {
                    fontSize: 16,
                    fontWeight: "600",
                    color: colors.foreground,
                    marginBottom: 8,
                },
                hint: {
                    fontSize: 14,
                    color: colors.mutedForeground ?? colors.foreground,
                    opacity: 0.75,
                    marginBottom: 8,
                },
                searchInput: {
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 8,
                    padding: 12,
                    fontSize: 16,
                    color: colors.foreground,
                    backgroundColor: colors.card,
                },
                suggestionRow: {
                    paddingVertical: 12,
                    paddingHorizontal: 12,
                    borderBottomWidth: 1,
                    borderBottomColor: colors.border,
                },
                suggestionText: {
                    fontSize: 14,
                    color: colors.foreground,
                },
                suggestionsBox: {
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 8,
                    marginTop: 8,
                    maxHeight: 280,
                    backgroundColor: colors.card,
                },
                configuredRow: {
                    flexDirection: "row",
                    alignItems: "center",
                    paddingVertical: 10,
                    paddingHorizontal: 12,
                    borderWidth: 1,
                    borderColor: colors.border,
                    borderRadius: 8,
                    marginBottom: 8,
                    backgroundColor: colors.card,
                },
                configuredRowSelected: {
                    borderColor: colors.primary,
                    borderWidth: 2,
                },
                configuredName: {
                    flex: 1,
                    fontSize: 14,
                    color: colors.foreground,
                    marginRight: 8,
                },
                badgeRow: {
                    flexDirection: "row",
                    flexWrap: "wrap",
                    gap: 6,
                    marginTop: 4,
                },
                badge: {
                    fontSize: 12,
                    fontWeight: "600",
                    color: colors.primary,
                },
                configPanel: {
                    borderWidth: 1,
                    borderColor: colors.primary,
                    borderRadius: 8,
                    padding: 16,
                    backgroundColor: colors.card,
                },
                configTitle: {
                    fontSize: 16,
                    fontWeight: "700",
                    color: colors.foreground,
                    marginBottom: 16,
                },
            }),
        [colors]
    )

    const renderBadges = (config: UniqueRaceOverrideConfig) => {
        const badges: string[] = []
        if (config.strategy && config.strategy !== "Default") {
            badges.push(config.strategy)
        }
        if (config.enableIrregularTraining) {
            badges.push("Irregular")
        }
        if (config.enableRetryOverride) {
            badges.push(`Retries: ${config.maxRetries ?? globalMaxRetries}`)
        }
        if (badges.length === 0) {
            return <Text style={styles.hint}>No overrides set</Text>
        }
        return (
            <View style={styles.badgeRow}>
                {badges.map((badge) => (
                    <Text key={badge} style={styles.badge}>
                        {badge}
                    </Text>
                ))}
            </View>
        )
    }

    return (
        <View style={styles.root}>
            <PageHeader title="Unique Race Settings" />

            <SearchPageProvider page="UniqueRaceSettings" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled showsVerticalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1, paddingBottom: 24 }}>
                    <View className="m-1">
                        <SearchableItem
                            id="unique-race-strategy-overrides-enable"
                            title="Enable Unique Race Overrides"
                            description="When enabled, per-race strategy and irregular-training settings apply only for matching races, then normal behavior resumes."
                        >
                            <CustomCheckbox
                                checked={enableUniqueRaceStrategyOverrides}
                                onCheckedChange={(checked) => updateRacing({ enableUniqueRaceStrategyOverrides: checked })}
                                label="Enable Unique Race Overrides"
                                className="my-2"
                                searchId="enable-unique-race-strategy-overrides"
                            />
                        </SearchableItem>

                        <InfoContainer>
                            Type a race name below and tap a match to configure it. Names should match OCR detection (use Debug → Race List Detection Test to copy exact titles).
                        </InfoContainer>

                        {enableUniqueRaceStrategyOverrides && (
                            <>
                                <View style={styles.section}>
                                    <Text style={styles.label}>Find Race</Text>
                                    <Text style={styles.hint}>Type to filter the race list, then tap a race to open its settings.</Text>
                                    <TextInput
                                        style={styles.searchInput}
                                        value={searchQuery}
                                        onChangeText={setSearchQuery}
                                        placeholder="Type race name..."
                                        placeholderTextColor={colors.mutedForeground}
                                        autoCorrect={false}
                                        autoCapitalize="none"
                                    />

                                    {showUseTypedName && (
                                        <TouchableOpacity
                                            style={[styles.suggestionRow, { borderBottomWidth: 0, marginTop: 8 }]}
                                            onPress={() => handleSelectRace(searchQuery.trim())}
                                        >
                                            <Text style={[styles.suggestionText, { color: colors.primary, fontWeight: "600" }]}>
                                                Configure custom name: "{searchQuery.trim()}"
                                            </Text>
                                        </TouchableOpacity>
                                    )}

                                    <ScrollView style={styles.suggestionsBox} nestedScrollEnabled keyboardShouldPersistTaps="handled">
                                        {filteredSuggestions.map((raceName) => (
                                            <TouchableOpacity key={raceName} style={styles.suggestionRow} onPress={() => handleSelectRace(raceName)}>
                                                <Text style={styles.suggestionText}>{raceName}</Text>
                                            </TouchableOpacity>
                                        ))}
                                        {filteredSuggestions.length === 0 && (
                                            <Text style={[styles.hint, { padding: 12 }]}>No races match your search.</Text>
                                        )}
                                    </ScrollView>
                                </View>

                                {selectedRaceName && selectedConfig && (
                                    <View style={styles.section}>
                                        <View style={styles.configPanel}>
                                            <Text style={styles.configTitle} numberOfLines={3}>
                                                {selectedRaceName}
                                            </Text>

                                            <CustomSelect
                                                searchId="unique-race-strategy-picker"
                                                label="Running Style Override"
                                                description="Default uses your normal per-distance or blanket strategy for this race."
                                                width="100%"
                                                options={[...RACE_STRATEGY_OPTIONS]}
                                                value={selectedConfig.strategy ?? "Default"}
                                                onValueChange={(value) => updateRaceConfig(selectedRaceName, { strategy: value ?? "Default" })}
                                                placeholder="Default"
                                            />

                                            <View style={{ marginTop: 16 }}>
                                                <CustomCheckbox
                                                    searchId="unique-race-enable-irregular-training"
                                                    checked={selectedConfig.enableIrregularTraining ?? false}
                                                    onCheckedChange={(checked) => updateRaceConfig(selectedRaceName, { enableIrregularTraining: checked })}
                                                    label="Enable Irregular Training for This Race"
                                                    description="When this race is scheduled or mandatory, still evaluate irregular training before racing (requires Trackblazer irregular training enabled)."
                                                />
                                            </View>

                                            <View style={{ marginTop: 16 }}>
                                                <CustomCheckbox
                                                    searchId="unique-race-enable-retry-override"
                                                    checked={selectedConfig.enableRetryOverride ?? false}
                                                    onCheckedChange={(checked) =>
                                                        updateRaceConfig(selectedRaceName, {
                                                            enableRetryOverride: checked,
                                                            maxRetries: selectedConfig.maxRetries ?? globalMaxRetries,
                                                        })
                                                    }
                                                    label="Override Max Retries for This Race"
                                                    description={`When enabled, uses the retry count below on a loss instead of the global max (${globalMaxRetries}).`}
                                                />
                                            </View>

                                            {selectedConfig.enableRetryOverride && (
                                                <View style={{ marginTop: 8 }}>
                                                    <CustomSlider
                                                        searchId="unique-race-max-retries"
                                                        parentId="unique-race-enable-retry-override"
                                                        searchCondition={selectedConfig.enableRetryOverride}
                                                        value={selectedConfig.maxRetries ?? globalMaxRetries}
                                                        placeholder={globalMaxRetries}
                                                        onValueChange={(value) => updateRaceConfig(selectedRaceName, { maxRetries: value })}
                                                        onSlidingComplete={(value) => updateRaceConfig(selectedRaceName, { maxRetries: value })}
                                                        min={0}
                                                        max={5}
                                                        step={1}
                                                        label="Max Retries for This Race"
                                                        showValue={true}
                                                        showLabels={true}
                                                        description="Number of retry attempts allowed if this race is lost."
                                                    />
                                                </View>
                                            )}

                                            <CustomButton variant="destructive" onPress={() => handleRemoveRace(selectedRaceName)} style={{ marginTop: 16 }}>
                                                Remove Race Configuration
                                            </CustomButton>
                                        </View>
                                    </View>
                                )}

                                <View style={styles.section}>
                                    <Text style={styles.label}>Configured Races ({configuredRaceNames.length})</Text>
                                    {configuredRaceNames.length === 0 ? (
                                        <Text style={styles.hint}>No races configured yet. Type and select a race above.</Text>
                                    ) : (
                                        configuredRaceNames.map((raceName) => {
                                            const config = overrides[raceName]
                                            const isSelected = selectedRaceName === raceName
                                            return (
                                                <TouchableOpacity
                                                    key={raceName}
                                                    style={[styles.configuredRow, isSelected && styles.configuredRowSelected]}
                                                    onPress={() => handleSelectRace(raceName)}
                                                >
                                                    <View style={{ flex: 1 }}>
                                                        <Text style={styles.configuredName} numberOfLines={2}>
                                                            {raceName}
                                                        </Text>
                                                        {renderBadges(config)}
                                                    </View>
                                                </TouchableOpacity>
                                            )
                                        })
                                    )}
                                </View>
                            </>
                        )}
                    </View>
                </ScrollView>
            </SearchPageProvider>
        </View>
    )
}

export default UniqueRaceSettings
