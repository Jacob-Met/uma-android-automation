import { logWithTimestamp } from "./logger"

/**
 * Deep merges two objects, preserving nested structure.
 * @param target - The target object to merge into.
 * @param source - The source object to merge from.
 * @returns A new object with merged values from both target and source.
 */
export const deepMerge = <T extends Record<string, any>>(target: T, source: Partial<T>): T => {
    const output = { ...target }
    for (const key in source) {
        if (source[key] && typeof source[key] === "object" && !Array.isArray(source[key]) && source[key] !== null) {
            output[key] = deepMerge((target[key] || {}) as Record<string, any>, source[key] as any) as T[Extract<keyof T, string>]
        } else if (source[key] !== undefined) {
            output[key] = source[key] as T[Extract<keyof T, string>]
        }
    }
    return output
}

/**
 * Persisted settings whose canonical owner is *not* the React-state `Settings` object. They live
 * in SQLite and are read directly by Kotlin (or by a dedicated JS writer) — round-tripping them
 * through React state inflates re-renders and the auto-save batch by hundreds of KB on a populated
 * profile.
 *
 * Each entry is filtered out on **both** directions:
 * - Save path (`convertSettingsToBatch`): never re-write rows React shouldn't own.
 * - Load path (`stripDbOwnedKeys` invoked by `loadSettings`): never absorb them into React state.
 *
 * Categories of entries:
 * - `misc.formattedSettingsString` — built and persisted directly by `MessageLog`'s debounced effect.
 * - `racing.{racesData,epithetsData,characterPresetsData}` — bundled JSON assets for the Smart Race
 *   Solver, written once at bootstrap by `populateSolverData`; only consumed by Kotlin via
 *   `SettingsHelper.getStringSetting`.
 * - `racing.racingPlanData` — bundled racing plan blob written by the racing-plan generator;
 *   only consumed by Kotlin via `SettingsHelper.getStringSetting`.
 * - `trainingEvent.{characterEventData,supportEventData,scenarioEventData}` — bundled JSON event data
 *   written once at bootstrap by `populateEventData`; only consumed by Kotlin.
 */
export const DB_OWNED_KEYS: ReadonlyArray<readonly [string, string]> = [
    ["misc", "formattedSettingsString"],
    ["racing", "racesData"],
    ["racing", "epithetsData"],
    ["racing", "characterPresetsData"],
    ["racing", "racingPlanData"],
    ["trainingEvent", "characterEventData"],
    ["trainingEvent", "supportEventData"],
    ["trainingEvent", "scenarioEventData"],
]

const isDbOwned = (category: string, key: string): boolean => DB_OWNED_KEYS.some(([c, k]) => c === category && k === key)

/**
 * Returns a per-category shallow copy of `settings` with all `DB_OWNED_KEYS` removed. Used on the
 * load path to keep large Kotlin-owned blobs out of React state.
 *
 * @param settings - The nested `category -> key -> value` map returned by `loadAllSettings`.
 * @returns A new map with the same structure, minus DB-owned keys.
 */
export const stripDbOwnedKeys = (settings: Record<string, any>): Record<string, any> => {
    const out: Record<string, any> = {}
    for (const [category, categorySettings] of Object.entries(settings)) {
        if (!categorySettings || typeof categorySettings !== "object") {
            out[category] = categorySettings
            continue
        }
        const filtered: Record<string, any> = {}
        for (const [key, value] of Object.entries(categorySettings as Record<string, any>)) {
            if (isDbOwned(category, key)) continue
            filtered[key] = value
        }
        out[category] = filtered
    }
    return out
}

/**
 * Converts `Settings` object to database batch format.
 *
 * @param settings - The `Settings` object to convert.
 * @returns An array of objects in the format `{ category: string; key: string; value: any }`.
 */
export const convertSettingsToBatch = (settings: Record<string, any>) => {
    const batch: Array<{ category: string; key: string; value: any }> = []

    Object.entries(settings).forEach(([category, categorySettings]) => {
        Object.entries(categorySettings).forEach(([key, value]) => {
            if (isDbOwned(category, key)) return
            batch.push({ category, key, value })
        })
    })

    return batch
}

/**
 * Applies all registered migrations to the Settings object.
 * @param settings - The Settings object to apply migrations to (already merged with defaults).
 * @param rawSettings - Optional raw settings (pre-merge) used to detect fields that were absent in the persisted store.
 *   Required by migrations that need to distinguish "user never set this" from "user set this to the default value".
 * @returns An object containing the migrated Settings object and a boolean indicating whether any migrations were applied.
 */
export const applyMigrations = (settings: any, rawSettings?: any): { settings: any; anyMigrated: boolean } => {
    let anyMigrated = false
    let migratedSettings = settings

    // Migration: Move Training Event specific OCR settings to trainingEvent category.
    const ocr = (migratedSettings as any).ocr
    const debug = (migratedSettings as any).debug

    if (ocr?.ocrConfidence !== undefined) {
        migratedSettings.trainingEvent.ocrConfidence = ocr.ocrConfidence
        delete ocr.ocrConfidence
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated ocrConfidence to trainingEvent category.")
    }

    if (ocr?.enableAutomaticOCRRetry !== undefined) {
        migratedSettings.trainingEvent.enableAutomaticOCRRetry = ocr.enableAutomaticOCRRetry
        delete ocr.enableAutomaticOCRRetry
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated enableAutomaticOCRRetry to trainingEvent category.")
    }

    if (debug?.enableHideOCRComparisonResults !== undefined) {
        migratedSettings.trainingEvent.enableHideOCRComparisonResults = debug.enableHideOCRComparisonResults
        delete debug.enableHideOCRComparisonResults
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated enableHideOCRComparisonResults to trainingEvent category.")
    }

    if (ocr?.ocrThreshold !== undefined) {
        migratedSettings.debug.ocrThreshold = ocr.ocrThreshold
        delete ocr.ocrThreshold
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated ocrThreshold to debug category.")
    }

    // After moving all OCR settings, delete the empty ocr object.
    if (migratedSettings && (migratedSettings as any).ocr && Object.keys((migratedSettings as any).ocr).length === 0) {
        delete (migratedSettings as any).ocr
    }

    // Migration: Mirror statPrioritization into eventChoiceStatPriority and summerTrainingStatPriority for users
    // upgrading from a version that only had a single stat priority list. The new keys are absent in the persisted
    // settings, so deepMerge fills them with the canonical default — but we want them to match the user's main list.
    const rawTraining = rawSettings?.training as any
    const training = migratedSettings.training as any
    if (training && rawTraining) {
        if (rawTraining.eventChoiceStatPriority === undefined && Array.isArray(training.statPrioritization)) {
            training.eventChoiceStatPriority = [...training.statPrioritization]
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Mirrored statPrioritization into eventChoiceStatPriority for upgrade.")
        }
        if (rawTraining.summerTrainingStatPriority === undefined && Array.isArray(training.statPrioritization)) {
            training.summerTrainingStatPriority = [...training.statPrioritization]
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Mirrored statPrioritization into summerTrainingStatPriority for upgrade.")
        }
    }

    // Migration: Convert single stopAtDate string to stopAtDates array.
    const general = migratedSettings.general as any
    if (general?.stopAtDate !== undefined && typeof general.stopAtDate === "string") {
        migratedSettings.general.stopAtDates = [general.stopAtDate]
        delete general.stopAtDate
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated stopAtDate to stopAtDates array.")
    }

    // Migration: Drop the removed enablePopupCheck setting.
    if (general?.enablePopupCheck !== undefined) {
        delete general.enablePopupCheck
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Dropped removed setting enablePopupCheck.")
    }

    // Migration: Drop upstream-only general settings not supported by the custom fork.
    if (general?.enableSwipeBasedScrolling !== undefined) {
        delete general.enableSwipeBasedScrolling
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Dropped upstream-only setting enableSwipeBasedScrolling.")
    }

    // Migration: Move enableMessageIdDisplay and overlayButtonSizeDP from debug (upstream 5.7+) to misc (custom fork).
    const misc = (migratedSettings as any).misc
    const rawMisc = rawSettings?.misc as any
    if (debug?.enableMessageIdDisplay !== undefined && rawMisc?.enableMessageIdDisplay === undefined) {
        misc.enableMessageIdDisplay = debug.enableMessageIdDisplay
        delete debug.enableMessageIdDisplay
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated enableMessageIdDisplay from debug to misc.")
    }
    if (debug?.overlayButtonSizeDP !== undefined && rawMisc?.overlayButtonSizeDP === undefined) {
        misc.overlayButtonSizeDP = debug.overlayButtonSizeDP
        delete debug.overlayButtonSizeDP
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Migrated overlayButtonSizeDP from debug to misc.")
    }

    // Migration: Drop removed pause/resume setting from older custom exports.
    if (debug?.enablePauseResume !== undefined) {
        delete debug.enablePauseResume
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Dropped removed setting enablePauseResume.")
    }

    // Migration: Upstream master 5.7 Trackblazer key renames → custom fork keys.
    const scenarioOverrides = migratedSettings.scenarioOverrides as any
    const rawScenarioOverrides = rawSettings?.scenarioOverrides as any
    if (scenarioOverrides && rawScenarioOverrides) {
        if (rawScenarioOverrides.trackblazerSkipRiskyCharmTrainingBelowGain !== undefined) {
            if (rawScenarioOverrides.trackblazerMinStatGainForCharm === undefined) {
                scenarioOverrides.trackblazerMinStatGainForCharm = rawScenarioOverrides.trackblazerSkipRiskyCharmTrainingBelowGain
            }
            delete scenarioOverrides.trackblazerSkipRiskyCharmTrainingBelowGain
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Migrated trackblazerSkipRiskyCharmTrainingBelowGain to trackblazerMinStatGainForCharm.")
        }
        if (rawScenarioOverrides.trackblazerSkipBadMoodItemsBelowGain !== undefined) {
            if (rawScenarioOverrides.trackblazerLowMainStatGainItemFloor === undefined) {
                scenarioOverrides.trackblazerLowMainStatGainItemFloor = rawScenarioOverrides.trackblazerSkipBadMoodItemsBelowGain
            }
            delete scenarioOverrides.trackblazerSkipBadMoodItemsBelowGain
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Migrated trackblazerSkipBadMoodItemsBelowGain to trackblazerLowMainStatGainItemFloor.")
        }
        if (rawScenarioOverrides.trackblazerForceTrainEnergyFloor !== undefined) {
            delete scenarioOverrides.trackblazerForceTrainEnergyFloor
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Dropped removed setting trackblazerForceTrainEnergyFloor.")
        }
        if (Array.isArray(scenarioOverrides.trackblazerIrregularTrainingAgendaGrades)) {
            const grades = scenarioOverrides.trackblazerIrregularTrainingAgendaGrades as string[]
            if (grades.includes("PRE_OP")) {
                scenarioOverrides.trackblazerIrregularTrainingAgendaGrades = grades.filter((g) => g !== "PRE_OP")
                if (scenarioOverrides.trackblazerEnableIrregularTrainingAgendaPreOp === undefined) {
                    scenarioOverrides.trackblazerEnableIrregularTrainingAgendaPreOp = true
                }
                anyMigrated = true
                logWithTimestamp("[SettingsManager] Migrated PRE_OP agenda grade to trackblazerEnableIrregularTrainingAgendaPreOp toggle.")
            }
        }
    }

    // Migration: seed per-slot custom agenda titles from legacy single customAgendaTitle field.
    const racing = migratedSettings.racing as Record<string, unknown> | undefined
    const rawRacing = rawSettings?.racing as Record<string, unknown> | undefined
    if (racing && rawRacing) {
        if (racing.userAgendaCustomTitles === undefined) {
            racing.userAgendaCustomTitles = "{}"
            anyMigrated = true
        }
        const legacyCustomTitle = typeof rawRacing.customAgendaTitle === "string" ? rawRacing.customAgendaTitle.trim() : ""
        const selectedSlot =
            typeof rawRacing.selectedUserAgenda === "string" && rawRacing.selectedUserAgenda.trim() !== ""
                ? rawRacing.selectedUserAgenda.trim()
                : "Agenda 1"
        if (legacyCustomTitle && (!racing.userAgendaCustomTitles || racing.userAgendaCustomTitles === "{}")) {
            racing.userAgendaCustomTitles = JSON.stringify({ [selectedSlot]: legacyCustomTitle })
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Seeded userAgendaCustomTitles from legacy customAgendaTitle.")
        }
    }

    // Migration: Upstream training settings removed or replaced in the custom fork.
    if (training && rawTraining) {
        if (rawTraining.enablePrioritizeNearMaxFriendship !== undefined && rawTraining.trainerFriendshipInfluence === undefined) {
            training.trainerFriendshipInfluence = rawTraining.enablePrioritizeNearMaxFriendship ? 100 : 0
            delete training.enablePrioritizeNearMaxFriendship
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Migrated enablePrioritizeNearMaxFriendship to trainerFriendshipInfluence.")
        }
        if (rawTraining.enableTrainingLevelWeighting !== undefined) {
            delete training.enableTrainingLevelWeighting
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Dropped removed setting enableTrainingLevelWeighting.")
        }
        if (rawTraining.disableStatTargets !== undefined) {
            delete training.disableStatTargets
            anyMigrated = true
            logWithTimestamp("[SettingsManager] Dropped removed setting disableStatTargets.")
        }
    }

    const rawAdvanced = rawSettings?.advanced as Record<string, unknown> | undefined
    let advanced = migratedSettings.advanced as Record<string, unknown> | undefined
    if (!advanced) {
        advanced = {}
        migratedSettings.advanced = advanced
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Initialized advanced settings namespace.")
    }
    if (rawAdvanced?.perActionDelayOverrides === undefined && advanced.perActionDelayOverrides === undefined) {
        advanced.perActionDelayOverrides = "{}"
        anyMigrated = true
    } else if (advanced.perActionDelayOverrides != null && typeof advanced.perActionDelayOverrides !== "string") {
        advanced.perActionDelayOverrides = JSON.stringify(advanced.perActionDelayOverrides)
        anyMigrated = true
        logWithTimestamp("[SettingsManager] Normalized perActionDelayOverrides object to JSON string.")
    }
    if (rawAdvanced?.delayCalibrationStats === undefined && advanced.delayCalibrationStats === undefined) {
        advanced.delayCalibrationStats = {}
        anyMigrated = true
    }
    const overlayResumeDefaults: Record<string, boolean> = {
        overlayResumeRecheckSkills: false,
        overlayResumeReloadAgenda: false,
        overlayResumeRecheckShop: false,
        overlayRecheckShopOnTurnChange: false,
        overlayRecheckSkillsWhenOverThreshold: false,
    }
    for (const [key, defaultValue] of Object.entries(overlayResumeDefaults)) {
        if (advanced[key] === undefined) {
            advanced[key] = defaultValue
            anyMigrated = true
        }
    }

    return { settings: migratedSettings, anyMigrated }
}

/**
 * Merges an imported settings object with defaults and applies all import/upgrade migrations.
 * @param decoded - Parsed settings from an import file (partial is OK).
 * @param defaults - Canonical default settings for the app build.
 */
export const normalizeImportedSettings = <T extends Record<string, any>>(decoded: Partial<T>, defaults: T): T => {
    const merged = deepMerge(defaults, decoded)
    const { settings } = applyMigrations(merged, decoded)
    return settings as T
}
