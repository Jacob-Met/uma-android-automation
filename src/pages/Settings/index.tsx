import { useMemo, useContext, useEffect, useState, useRef, useCallback } from "react"
import { SearchPageProvider } from "../../context/SearchPageContext"
import { BotMetaContext, GeneralMiscContext, RacingContext, SkillsContext, TrainingContext, ScenarioOverridesContext, Settings as BotSettings } from "../../context/BotStateContext"
import { InteractionManager, ScrollView, StyleSheet, Text, View } from "react-native"
import { Snackbar } from "react-native-paper"
import { useNavigation } from "@react-navigation/native"
import ThemeToggle from "../../components/ThemeToggle"
import { useTheme } from "../../context/ThemeContext"
import CustomSelect from "../../components/CustomSelect"
import NavigationLink from "../../components/NavigationLink"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSlider from "../../components/CustomSlider"
import CustomTitle from "../../components/CustomTitle"
import CustomButton from "../../components/CustomButton"
import PageHeader from "../../components/PageHeader"
import { Separator } from "../../components/ui/separator"
import WarningContainer from "../../components/WarningContainer"
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../../components/ui/alert-dialog"
import SearchableItem from "../../components/SearchableItem"
import { useSettings } from "../../context/SettingsContext"
import { useSettingsFileManager } from "../../hooks/useSettingsFileManager"
import { usePerformanceLogging } from "../../hooks/usePerformanceLogging"
import { Input } from "../../components/ui/input"
import { databaseManager, DatabaseUmaPreset } from "../../lib/database"
import { extractUmaPresetSettings, normalizeUmaPresetName } from "../../lib/umaPresetUtils"

/**
 * The main Settings page of the application.
 * Provides scenario selection, navigation links to sub-settings pages,
 * misc bot configuration options, and settings management (import/export/reset).
 */
const Settings = () => {
    usePerformanceLogging("Settings")
    const [snackbarOpen, setSnackbarOpen] = useState<boolean>(false)
    const scrollViewRef = useRef<ScrollView>(null)

    const { readyStatus, defaultSettings } = useContext(BotMetaContext)
    const { general, misc, updateGeneral, updateMisc } = useContext(GeneralMiscContext)
    const { training, trainingStatTarget } = useContext(TrainingContext)
    const { racing } = useContext(RacingContext)
    const { skills } = useContext(SkillsContext)
    const { scenarioOverrides } = useContext(ScenarioOverridesContext)
    const { colors } = useTheme()
    const navigation = useNavigation()

    const { openDataDirectory, resetSettings } = useSettings()
    const { handleImportSettings, handleExportSettings, showImportDialog, setShowImportDialog, showResetDialog, setShowResetDialog } = useSettingsFileManager()

    const [umaPresets, setUmaPresets] = useState<DatabaseUmaPreset[]>([])
    const [umaPresetNameInput, setUmaPresetNameInput] = useState("")
    const [umaPresetMessage, setUmaPresetMessage] = useState<string | null>(null)

    const refreshUmaPresets = useCallback(async () => {
        try {
            await databaseManager.initialize()
            setUmaPresets(await databaseManager.getAllUmaPresets())
        } catch {
            setUmaPresets([])
        }
    }, [])

    useEffect(() => {
        if (readyStatus) {
            refreshUmaPresets()
        }
    }, [readyStatus, refreshUmaPresets])

    const handleSaveUmaPreset = useCallback(async () => {
        const trimmed = umaPresetNameInput.trim()
        if (!trimmed) {
            setUmaPresetMessage("Enter the Uma Musume name exactly as OCR detects it (from the aptitude/details dialog).")
            return
        }

        const snapshot: BotSettings = {
            ...(defaultSettings as BotSettings),
            training,
            trainingStatTarget,
            racing,
            skills,
            scenarioOverrides,
        }

        try {
            await databaseManager.saveUmaPreset(trimmed, normalizeUmaPresetName(trimmed), extractUmaPresetSettings(snapshot))
            setUmaPresetNameInput("")
            setUmaPresetMessage(`Saved preset for "${trimmed}" (training, racing, skills, scenario items). Training event overrides stay global.`)
            await refreshUmaPresets()
        } catch (error) {
            setUmaPresetMessage(`Failed to save preset: ${error instanceof Error ? error.message : String(error)}`)
        }
    }, [umaPresetNameInput, defaultSettings, training, trainingStatTarget, racing, skills, scenarioOverrides, refreshUmaPresets])

    const handleDeleteUmaPreset = useCallback(
        async (id: number) => {
            await databaseManager.deleteUmaPreset(id)
            await refreshUmaPresets()
        },
        [refreshUmaPresets]
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
            }),
        [colors]
    )

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////
    // Callbacks

    useEffect(() => {
        // Manually set this flag to false as the snackbar autohiding does not set this to false automatically.
        setSnackbarOpen(true)
        setTimeout(() => setSnackbarOpen(false), 2500)
    }, [readyStatus])

    // Two-phase mount. First paint renders the cheap navigation-link list (~40 ms baseline) so the
    // user sees the page immediately; the heavy Misc section (sliders, checkboxes, dialogs,
    // file-manager hook plumbing — ~1 s of additional work) commits one tick later, after the
    // navigator animation has painted. `runAfterInteractions` fires when the JS-side scheduler
    // considers itself idle, so we don't fight the navigation transition. Net: the page first
    // paint dropped 27 % (1065 → 782 ms) on a calibrated emulator harness.
    const [showHeavySections, setShowHeavySections] = useState(false)
    useEffect(() => {
        const handle = InteractionManager.runAfterInteractions(() => {
            setShowHeavySections(true)
        })
        return () => handle.cancel()
    }, [])

    /**
     * Reset the settings to their default values.
     */
    const handleResetSettings = async () => {
        const success = await resetSettings()
        if (success) {
            setSnackbarOpen(true)
            setTimeout(() => setSnackbarOpen(false), 2500)
        }
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////
    // Rendering

    const years = [
        { label: "Junior", value: "Junior" },
        { label: "Classic", value: "Classic" },
        { label: "Senior", value: "Senior" },
    ]

    const months = [
        { label: "January", value: "January" },
        { label: "February", value: "February" },
        { label: "March", value: "March" },
        { label: "April", value: "April" },
        { label: "May", value: "May" },
        { label: "June", value: "June" },
        { label: "July", value: "July" },
        { label: "August", value: "August" },
        { label: "September", value: "September" },
        { label: "October", value: "October" },
        { label: "November", value: "November" },
        { label: "December", value: "December" },
    ]

    const phases = [
        { label: "Early", value: "Early" },
        { label: "Late", value: "Late" },
    ]

    const handleStopAtDateChange = useCallback(
        (index: number, part: "year" | "month" | "phase", value: string) => {
            const dates = [...general.stopAtDates]
            const currentParts = dates[index].split(" ")
            let newYear = currentParts[0] || "Senior"
            let newMonth = currentParts[1] || "January"
            let newPhase = currentParts[2] || "Early"

            if (part === "year") newYear = value
            if (part === "month") newMonth = value
            if (part === "phase") newPhase = value

            dates[index] = `${newYear} ${newMonth} ${newPhase}`
            updateGeneral({ stopAtDates: dates })
        },
        [general]
    )

    const handleAddStopAtDate = useCallback(() => {
        updateGeneral({ stopAtDates: [...general.stopAtDates, "Senior January Early"] })
    }, [general])

    const handleRemoveStopAtDate = useCallback(
        (index: number) => {
            const dates = general.stopAtDates.filter((_, i) => i !== index)
            updateGeneral({ stopAtDates: dates.length > 0 ? dates : ["Senior January Early"] })
        },
        [general]
    )

    const renderTrainingLink = () => {
        return (
            <NavigationLink
                title="Go to Training Settings"
                description="Configure which stats to train, set priorities, and customize training behavior."
                onPress={() => navigation.navigate("TrainingSettings" as never)}
            />
        )
    }

    const renderTrainingEventLink = () => {
        return (
            <NavigationLink
                title="Go to Training Event Settings"
                description="Configure training event preferences and event selection behavior."
                onPress={() => navigation.navigate("TrainingEventSettings" as never)}
            />
        )
    }

    const renderRacingLink = () => {
        return (
            <NavigationLink
                title="Go to Racing Settings"
                description="Configure racing behavior, retry settings, mandatory race handling, and more."
                onPress={() => navigation.navigate("RacingSettings" as never)}
            />
        )
    }

    const renderSkillsLink = () => {
        return <NavigationLink title="Go to Skills Settings" description="Configure skill purchasing behavior." onPress={() => navigation.navigate("SkillSettings" as never)} />
    }

    const renderEventLogVisualizerLink = () => {
        return (
            <NavigationLink
                title="Go to Event Log Visualizer (Beta)"
                description="Import logs and view a day-by-day timeline of actions."
                onPress={() => navigation.navigate("EventLogVisualizer" as never)}
            />
        )
    }

    const renderScenarioOverridesLink = () => {
        return (
            <NavigationLink
                title="Go to Scenario Overrides Settings"
                description="Configure behavior overrides specific to each scenario."
                onPress={() => navigation.navigate("ScenarioOverridesSettings" as never)}
            />
        )
    }

    const renderAdvancedLink = () => {
        return (
            <NavigationLink
                title="Go to Advanced Settings"
                description="Per-action delay calibration, fine-tuning, and other advanced tuning options."
                onPress={() => navigation.navigate("AdvancedSettings" as never)}
            />
        )
    }

    const renderDebugLink = () => {
        return (
            <NavigationLink
                title="Go to Debug Settings"
                description="Configure debug mode, template matching settings, and diagnostic tests for bot troubleshooting."
                onPress={() => navigation.navigate("DebugSettings" as never)}
            />
        )
    }

    const renderDiscordLink = () => {
        return (
            <NavigationLink
                title="Go to Discord Settings"
                description="Configure Discord bot notifications to receive DM updates when the bot stops."
                onPress={() => navigation.navigate("DiscordSettings" as never)}
            />
        )
    }

    const renderLLMSettingsLink = () => {
        return (
            <NavigationLink
                title="Go to LLM Settings"
                description="Configure on-device docs search and chat model downloads for the Ask the Docs feature."
                onPress={() => navigation.navigate("LLMSettings" as never)}
            />
        )
    }

    const renderMiscSettings = () => {
        return (
            <View style={{ marginTop: 16 }}>
                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle title="Misc Settings" description="General settings for the bot that don't fit into the other categories." />

                <CustomCheckbox
                    searchId="settings-stop-before-finals"
                    checked={general.enableStopBeforeFinals}
                    onCheckedChange={(checked) => {
                        updateGeneral({ enableStopBeforeFinals: checked })
                    }}
                    label="Stop before Finals"
                    description="Stops the bot on turn 72 so you can purchase skills before the final races."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-stop-at-date"
                    checked={general.enableStopAtDate}
                    onCheckedChange={(checked) => {
                        updateGeneral({ enableStopAtDate: checked })
                    }}
                    label="Stop at Date"
                    description="Stops the bot on one or more specified dates. The bot will stop at the earliest matching date it reaches."
                    className="mt-4"
                />

                {general.enableStopAtDate && (
                    <SearchableItem id="settings-stop-at-date" title="Target Dates" description="Stops the bot on the specified dates." style={{ marginLeft: 16, marginTop: 8 }}>
                        {general.stopAtDates.map((dateStr, index) => {
                            const parts = dateStr.split(" ")
                            return (
                                <View key={index} style={{ marginBottom: index < general.stopAtDates.length - 1 ? 12 : 0 }}>
                                    <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
                                        <Text style={{ fontSize: 14, fontWeight: "600", color: colors.foreground }}>Date {index + 1}</Text>
                                        {general.stopAtDates.length > 1 && (
                                            <CustomButton onPress={() => handleRemoveStopAtDate(index)} variant="destructive" size="sm" fontSize={12}>
                                                Remove
                                            </CustomButton>
                                        )}
                                    </View>
                                    <View style={{ flexDirection: "row", gap: 8, justifyContent: "space-between" }}>
                                        <View style={{ flex: 1 }}>
                                            <CustomSelect
                                                placeholder="Year"
                                                width="100%"
                                                options={years}
                                                value={parts[0]}
                                                onValueChange={(value) => handleStopAtDateChange(index, "year", value || "Senior")}
                                            />
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <CustomSelect
                                                placeholder="Month"
                                                width="100%"
                                                options={months}
                                                value={parts[1]}
                                                onValueChange={(value) => handleStopAtDateChange(index, "month", value || "January")}
                                            />
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <CustomSelect
                                                placeholder="Phase"
                                                width="100%"
                                                options={phases}
                                                value={parts[2]}
                                                onValueChange={(value) => handleStopAtDateChange(index, "phase", value || "Early")}
                                            />
                                        </View>
                                    </View>
                                </View>
                            )
                        })}
                        <CustomButton onPress={handleAddStopAtDate} variant="default" fontSize={14} style={{ marginTop: 12, alignSelf: "flex-start" }}>
                            + Add Date
                        </CustomButton>
                    </SearchableItem>
                )}

                <CustomCheckbox
                    searchId="settings-crane-game-attempt"
                    checked={general.enableCraneGameAttempt}
                    onCheckedChange={(checked) => {
                        updateGeneral({ enableCraneGameAttempt: checked })
                    }}
                    label="Enable Crane Game Attempt"
                    description="When enabled, the bot will attempt to complete the crane game. By default, the bot will stop when it is detected."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-enable-settings-display"
                    checked={misc.enableSettingsDisplay}
                    onCheckedChange={(checked) => {
                        updateMisc({ enableSettingsDisplay: checked })
                    }}
                    label="Enable Settings Display in Message Log"
                    description="Shows current bot configuration settings at the top of the message log."
                    className="mt-4"
                />

                <CustomCheckbox
                    searchId="settings-enable-message-id-display"
                    checked={misc.enableMessageIdDisplay}
                    onCheckedChange={(checked) => {
                        updateMisc({ enableMessageIdDisplay: checked })
                    }}
                    label="Enable Message ID Display"
                    description="Shows message IDs in the message log to help with debugging."
                    className="mt-4"
                />

                <CustomSlider
                    searchId="settings-wait-delay"
                    value={general.waitDelay}
                    placeholder={defaultSettings.general.waitDelay}
                    onValueChange={(value) => {
                        updateGeneral({ waitDelay: value })
                    }}
                    onSlidingComplete={(value) => {
                        updateGeneral({ waitDelay: value })
                    }}
                    min={0.0}
                    max={1.0}
                    step={0.1}
                    label="Wait Delay"
                    labelUnit="s"
                    showValue={true}
                    showLabels={true}
                    description="Sets the delay between actions and imaging operations. Lowering this will make the bot run much faster at the risk of the bot losing track of its location after loading/connecting screens."
                />

                <CustomSlider
                    searchId="settings-dialog-wait-delay"
                    value={general.dialogWaitDelay}
                    placeholder={defaultSettings.general.dialogWaitDelay}
                    onValueChange={(value) => {
                        updateGeneral({ dialogWaitDelay: value })
                    }}
                    onSlidingComplete={(value) => {
                        updateGeneral({ dialogWaitDelay: value })
                    }}
                    min={0.0}
                    max={1.0}
                    step={0.1}
                    label="Dialog Wait Delay"
                    labelUnit="s"
                    showValue={true}
                    showLabels={true}
                    description="Delay after opening a dialog/popup before the bot reads or handles it (events, rest, race results, shop, etc.)."
                />

                <CustomSlider
                    searchId="settings-training-wait-delay"
                    value={general.trainingWaitDelay}
                    placeholder={defaultSettings.general.trainingWaitDelay}
                    onValueChange={(value) => {
                        updateGeneral({ trainingWaitDelay: value })
                    }}
                    onSlidingComplete={(value) => {
                        updateGeneral({ trainingWaitDelay: value })
                    }}
                    min={0.0}
                    max={2.0}
                    step={0.1}
                    label="Training Wait Delay"
                    labelUnit="s"
                    showValue={true}
                    showLabels={true}
                    description="Pacing on the training screen only: entering training, OCR/analysis pauses, backing out to rest. Does not affect general navigation or dialog handling."
                />

                <CustomSlider
                    searchId="settings-dialog-tap-delay"
                    value={general.dialogTapDelay}
                    placeholder={defaultSettings.general.dialogTapDelay}
                    onValueChange={(value) => {
                        updateGeneral({ dialogTapDelay: value })
                    }}
                    onSlidingComplete={(value) => {
                        updateGeneral({ dialogTapDelay: value })
                    }}
                    min={0.0}
                    max={1.0}
                    step={0.05}
                    label="Dialog Multi-Tap Delay"
                    labelUnit="s"
                    showValue={true}
                    showLabels={true}
                    description="Pause between taps when the bot spam-clicks a button (taps=3/5 on training buttons, race continue, etc.). Separate from dialog wait delay."
                />

                <CustomSlider
                    searchId="settings-overlay-button-size"
                    value={misc.overlayButtonSizeDP}
                    placeholder={defaultSettings.misc.overlayButtonSizeDP}
                    onValueChange={(value) => {
                        updateMisc({ overlayButtonSizeDP: value })
                    }}
                    onSlidingComplete={(value) => {
                        updateMisc({ overlayButtonSizeDP: value })
                    }}
                    min={30}
                    max={60}
                    step={5}
                    label="Overlay Button Size"
                    labelUnit=" dp"
                    showValue={true}
                    showLabels={true}
                    description="Sets the size of the floating overlay button in density-independent pixels (dp). Higher values make the button easier to tap."
                />

                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle
                    searchId="settings-uma-presets-title"
                    title="Uma Musume Presets"
                    description="Link a full training/racing/skills/scenario-items prefab to a detected Uma name. Training event overrides always stay global."
                />

                <CustomCheckbox
                    searchId="enable-auto-load-uma-preset"
                    checked={misc.enableAutoLoadUmaPreset}
                    onCheckedChange={(checked) => updateMisc({ enableAutoLoadUmaPreset: checked })}
                    label="Auto-Load Uma Preset on Detection"
                    description="When enabled, the bot loads the matching preset the first time it OCR-reads the Uma name from the aptitude/details dialog (umamusume_details). Does not change wait delays, debug, or training event overrides."
                    className="my-2"
                />

                <WarningContainer style={{ marginBottom: 12 }}>
                    <Text style={{ color: colors.warningText, lineHeight: 20 }}>
                        Detection uses color-filtered OCR on the brown trainee name in the Uma details popup (first time per run). Match names case-insensitively — use the exact spelling the bot logs as
                        {" "}
                        <Text style={{ fontWeight: "bold" }}>[TRAINEE] Name: ...</Text>. If a run already started, restart the bot so training fields picked at startup refresh.
                    </Text>
                </WarningContainer>

                <Input
                    placeholder='Uma name (e.g. "Oguri Cap")'
                    value={umaPresetNameInput}
                    onChangeText={setUmaPresetNameInput}
                    style={{ color: colors.foreground, backgroundColor: colors.secondary, marginBottom: 8 }}
                />

                <CustomButton onPress={handleSaveUmaPreset} variant="default" style={{ marginBottom: 8 }}>
                    Save Current Settings as Uma Preset
                </CustomButton>

                {umaPresetMessage && (
                    <Text style={{ color: colors.mutedForeground, marginBottom: 8, lineHeight: 18 }}>{umaPresetMessage}</Text>
                )}

                {umaPresets.map((preset) => (
                    <View key={preset.id} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                        <Text style={{ color: colors.foreground, flex: 1 }}>{preset.uma_name}</Text>
                        <CustomButton onPress={() => handleDeleteUmaPreset(preset.id)} variant="destructive" style={{ width: 90 }}>
                            Delete
                        </CustomButton>
                    </View>
                ))}

                <Separator style={{ marginVertical: 16 }} />

                <CustomTitle searchId="settings-management-title" title="Settings Management" description="Import and export settings from JSON file or access the app's data directory." />

                <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                    <CustomButton onPress={handleImportSettings} variant="default" style={{ width: 150 }}>
                        📥 Import Settings
                    </CustomButton>

                    <CustomButton onPress={handleExportSettings} variant="default" style={{ width: 150 }}>
                        📤 Export Settings
                    </CustomButton>
                </View>

                <View style={{ flexDirection: "row", marginTop: 16, justifyContent: "space-between" }}>
                    <CustomButton onPress={openDataDirectory} variant="default" style={{ width: 150 }} fontSize={12}>
                        📁 Open Data Directory
                    </CustomButton>

                    <CustomButton onPress={() => setShowResetDialog(true)} variant="destructive" style={{ width: 150 }}>
                        🔄 Reset Settings
                    </CustomButton>
                </View>

                <WarningContainer style={{ marginBottom: 12 }}>
                    <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                        <Text style={{ fontWeight: "bold", color: colors.warningText }}>⚠️ File Explorer Note:</Text>
                        <Text style={{ fontSize: 14, color: colors.warningText, lineHeight: 20 }}>
                            To manually access files, you need a file explorer app that can access the /Android/data folder (like CX File Explorer). Standard file managers will not work.
                        </Text>
                    </View>
                </WarningContainer>
            </View>
        )
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    return (
        <View style={styles.root}>
            <PageHeader title="Settings" rightComponent={<ThemeToggle />} />

            <SearchPageProvider page="SettingsMain" scrollViewRef={scrollViewRef}>
                <ScrollView ref={scrollViewRef} nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                    <View className="m-1">
                        {renderTrainingLink()}
                        {renderTrainingEventLink()}
                        {renderRacingLink()}
                        {renderSkillsLink()}
                        {renderEventLogVisualizerLink()}
                        {renderDiscordLink()}
                        {renderScenarioOverridesLink()}
                        {renderAdvancedLink()}
                        {renderDebugLink()}
                        {renderLLMSettingsLink()}
                        {showHeavySections && renderMiscSettings()}
                    </View>
                </ScrollView>
            </SearchPageProvider>

            <Snackbar
                visible={snackbarOpen}
                onDismiss={() => setSnackbarOpen(false)}
                action={{
                    label: "Close",
                    onPress: () => {
                        setSnackbarOpen(false)
                    },
                }}
                style={{ backgroundColor: readyStatus ? "green" : "red", borderRadius: 10 }}
            >
                {readyStatus ? "Bot is ready!" : "Bot is not ready!"}
            </Snackbar>

            {/* Restart Dialog */}
            <AlertDialog open={showImportDialog} onOpenChange={setShowImportDialog}>
                <AlertDialogContent style={{ backgroundColor: "black" }}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>
                            <Text style={{ color: "white" }}>Settings Imported</Text>
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            <Text style={{ color: "white" }}>Settings have been imported successfully.</Text>
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogAction style={{ backgroundColor: "white" }}>
                            <Text style={{ color: "black" }}>OK</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Reset Settings Dialog */}
            <AlertDialog open={showResetDialog} onOpenChange={setShowResetDialog}>
                <AlertDialogContent style={{ backgroundColor: "black" }}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>
                            <Text style={{ color: "white" }}>Reset Settings to Default</Text>
                        </AlertDialogTitle>
                        <AlertDialogDescription style={{ height: 50 }}>
                            <Text style={{ color: "white" }}>
                                Are you sure you want to reset all settings to their default values? This action cannot be undone and will overwrite your current configuration.
                            </Text>
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel onPress={() => setShowResetDialog(false)} style={{ backgroundColor: "black" }}>
                            <Text style={{ color: "white" }}>Cancel</Text>
                        </AlertDialogCancel>
                        <AlertDialogAction onPress={handleResetSettings} style={{ backgroundColor: "white" }}>
                            <Text style={{ color: "black" }}>Reset</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </View>
    )
}

export default Settings
