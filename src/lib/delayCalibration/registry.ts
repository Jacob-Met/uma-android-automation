import { Settings } from "../../context/BotStateContext"
import { parsePerActionDelayOverrides, serializePerActionDelayOverrides } from "./perActionDelays"

/** Where the tunable delay value lives for an action. */
export type DelaySource =
    | { type: "settings"; category: keyof Settings; key: string }
    | { type: "advanced"; key: string; defaultValue: number }

export interface DelayCalibrationActionDefinition {
    id: string
    label: string
    description: string
    delaySource: DelaySource
    /** Log substring patterns that map a failure line to this action (too fast). */
    failurePatterns: RegExp[]
    /** Structured [DELAY_CAL] action id emitted from Kotlin when calibration is enabled. */
    calActionId: string
}

export const DELAY_CALIBRATION_ACTIONS: DelayCalibrationActionDefinition[] = [
    {
        id: "training.enter",
        label: "Training screen entry",
        description: "Wait after opening the training screen before stat analysis.",
        delaySource: { type: "settings", category: "general", key: "trainingWaitDelay" },
        failurePatterns: [
            /getActiveStat:: Timed out/i,
            /Could not confirm bot is on the Training screen/i,
            /handleTrackblazerTraining:: Failed to enter Training screen/i,
            /analyzeTrainings:: Skipping training due to not being able to confirm/i,
        ],
        calActionId: "training.enter",
    },
    {
        id: "training.tabSwitch",
        label: "Training tab switch",
        description: "Settle time after switching stat tabs during full training scan.",
        delaySource: { type: "advanced", key: "training.tabSwitch", defaultValue: 0.5 },
        failurePatterns: [/goToStat:: Timed out while waiting for/i, /Failed to navigate to .* training tab/i],
        calActionId: "training.tabSwitch",
    },
    {
        id: "general.wait",
        label: "General / loading wait",
        description: "Default pacing between actions and loading-spinner polling.",
        delaySource: { type: "settings", category: "general", key: "waitDelay" },
        failurePatterns: [/analyzeEnergyBar:: Failed/i, /updateDate:: date\.update\(\) failed/i],
        calActionId: "general.wait",
    },
    {
        id: "general.dialog",
        label: "Dialog open wait",
        description: "Wait after opening or handling dialogs.",
        delaySource: { type: "settings", category: "general", key: "dialogWaitDelay" },
        failurePatterns: [/getTitle:: Failed to match any dialogs/i],
        calActionId: "general.dialog",
    },
    {
        id: "general.dialogTap",
        label: "Dialog multi-tap spacing",
        description: "Delay between repeated taps on dialogs.",
        delaySource: { type: "settings", category: "general", key: "dialogTapDelay" },
        failurePatterns: [],
        calActionId: "general.dialogTap",
    },
    {
        id: "dialog.postClose",
        label: "Dialog post-close cushion",
        description: "Fixed wait after closing a handled dialog.",
        delaySource: { type: "advanced", key: "dialog.postClose", defaultValue: 0.5 },
        failurePatterns: [],
        calActionId: "dialog.postClose",
    },
    {
        id: "racing.agenda",
        label: "Race agenda load",
        description: "Extra pacing while loading the in-game race agenda.",
        delaySource: { type: "settings", category: "racing", key: "agendaWaitDelay" },
        failurePatterns: [],
        calActionId: "racing.agenda",
    },
    {
        id: "race.prepStrategy",
        label: "Race prep strategy / distance OCR",
        description: "Wait on race prep before per-distance strategy and distance OCR.",
        delaySource: { type: "settings", category: "racing", key: "raceStrategyWaitDelay" },
        failurePatterns: [/Per-distance strategy enabled but race distance unknown/i, /No double-star prediction found on race prep screen/i],
        calActionId: "race.prepStrategy",
    },
    {
        id: "trackblazer.itemAnimation",
        label: "Trackblazer item-use animation",
        description: "Wait after confirming training item use before closing the dialog.",
        delaySource: { type: "advanced", key: "trackblazer.itemAnimation", defaultValue: 4.0 },
        failurePatterns: [],
        calActionId: "trackblazer.itemAnimation",
    },
    {
        id: "trackblazer.shopEnter",
        label: "Trackblazer shop entry",
        description: "Wait before reading shop coins / training items UI.",
        delaySource: { type: "advanced", key: "trackblazer.shopEnter", defaultValue: 0.5 },
        failurePatterns: [/updateShopCoins:: Failed to find Training Items button/i, /Aborting buying process due to failed Shop Coins update/i],
        calActionId: "trackblazer.shopEnter",
    },
    {
        id: "game.startup",
        label: "Bot startup banner wait",
        description: "Initial wait when the bot starts before the main loop.",
        delaySource: { type: "advanced", key: "game.startup", defaultValue: 3.0 },
        failurePatterns: [],
        calActionId: "game.startup",
    },
    {
        id: "game.tapPost",
        label: "Post-tap cushion",
        description: "Fixed wait after each tap gesture.",
        delaySource: { type: "advanced", key: "game.tapPost", defaultValue: 0.2 },
        failurePatterns: [],
        calActionId: "game.tapPost",
    },
]

export const getDelayCalibrationAction = (id: string): DelayCalibrationActionDefinition | undefined =>
    DELAY_CALIBRATION_ACTIONS.find((a) => a.id === id || a.calActionId === id)

export const getCurrentDelayForAction = (action: DelayCalibrationActionDefinition, settings: Settings): number => {
    const src = action.delaySource
    if (src.type === "settings") {
        const slice = settings[src.category] as Record<string, unknown>
        const val = slice[src.key]
        return typeof val === "number" ? val : 0
    }
    return parsePerActionDelayOverrides(settings.advanced.perActionDelayOverrides)[src.key] ?? src.defaultValue
}
