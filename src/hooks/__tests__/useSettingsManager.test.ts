import { deepMerge, convertSettingsToBatch, applyMigrations, normalizeImportedSettings } from "../../lib/settingsUtils"

/** Minimal defaults for migration tests (avoids importing BotStateContext.tsx in Jest). */
const testDefaults = {
    general: { stopAtDates: [] as string[] },
    training: { trainerFriendshipInfluence: 50, statPrioritization: [] as string[] },
    misc: {},
    debug: {},
    trainingEvent: {},
    scenarioOverrides: {
        trackblazerMinStatGainForCharm: 25,
        trackblazerLowMainStatGainItemFloor: 15,
    },
} as any

// ===========================================================================
// deepMerge
// ===========================================================================

describe("deepMerge", () => {
    it("shallow merge: source overrides target", () => {
        const target = { a: 1, b: 2 }
        const source = { b: 3 }
        expect(deepMerge(target, source)).toEqual({ a: 1, b: 3 })
    })

    it("nested merge: preserves nested target keys not in source", () => {
        const target = { nested: { a: 1, b: 2 } }
        const source = { nested: { a: 10 } }
        expect(deepMerge(target, source as any)).toEqual({ nested: { a: 10, b: 2 } })
    })

    it("arrays are replaced entirely, not merged", () => {
        const target = { arr: [1, 2, 3] }
        const source = { arr: [4, 5] }
        expect(deepMerge(target, source)).toEqual({ arr: [4, 5] })
    })

    it("null in source overrides target", () => {
        const target = { a: { b: 1 } }
        const source = { a: null }
        // null is not an object, so it should override
        expect(deepMerge(target, source as any)).toEqual({ a: null })
    })

    it("undefined in source is skipped", () => {
        const target = { a: 1, b: 2 }
        const source = { a: undefined, b: 3 }
        expect(deepMerge(target, source)).toEqual({ a: 1, b: 3 })
    })

    it("empty source returns copy of target", () => {
        const target = { a: 1, b: { c: 2 } }
        const result = deepMerge(target, {})
        expect(result).toEqual({ a: 1, b: { c: 2 } })
        // Should be a new object (not same reference)
        expect(result).not.toBe(target)
    })

    it("merges 3+ levels deep", () => {
        const target = { l1: { l2: { l3: { a: 1, b: 2 } } } }
        const source = { l1: { l2: { l3: { a: 10 } } } }
        expect(deepMerge(target, source as any)).toEqual({ l1: { l2: { l3: { a: 10, b: 2 } } } })
    })

    it("adds new keys from source", () => {
        const target = { a: 1 }
        const source = { b: 2 }
        expect(deepMerge(target, source as any)).toEqual({ a: 1, b: 2 })
    })

    it("creates nested structure when target lacks the key", () => {
        const target = {} as any
        const source = { nested: { a: 1, b: 2 } }
        expect(deepMerge(target, source)).toEqual({ nested: { a: 1, b: 2 } })
    })
})

// ===========================================================================
// convertSettingsToBatch
// ===========================================================================

describe("convertSettingsToBatch", () => {
    it("converts single category with two keys to batch entries", () => {
        const settings = { general: { scenario: "URA", enableCraneGameAttempt: true } } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toHaveLength(2)
        expect(batch).toContainEqual({ category: "general", key: "scenario", value: "URA" })
        expect(batch).toContainEqual({ category: "general", key: "enableCraneGameAttempt", value: true })
    })

    it("converts multiple categories", () => {
        const settings = {
            general: { scenario: "URA" },
            training: { maximumFailureChance: 30 },
        } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toHaveLength(2)
        expect(batch).toContainEqual({ category: "general", key: "scenario", value: "URA" })
        expect(batch).toContainEqual({ category: "training", key: "maximumFailureChance", value: 30 })
    })

    it("handles values of different types", () => {
        const settings = {
            test: {
                str: "hello",
                num: 42,
                bool: false,
                arr: [1, 2],
                obj: { nested: true },
            },
        } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toHaveLength(5)
    })

    it("skips misc.formattedSettingsString so MessageLog's direct DB write isn't clobbered", () => {
        const settings = {
            misc: { formattedSettingsString: "stale react-state value", currentProfileName: "p1" },
            general: { scenario: "URA" },
        } as any
        const batch = convertSettingsToBatch(settings)
        expect(batch).toContainEqual({ category: "misc", key: "currentProfileName", value: "p1" })
        expect(batch).toContainEqual({ category: "general", key: "scenario", value: "URA" })
        expect(batch.find((row) => row.category === "misc" && row.key === "formattedSettingsString")).toBeUndefined()
    })
})

// ===========================================================================
// applyMigrations
// ===========================================================================

describe("applyMigrations", () => {
    it("migrates ocrConfidence from ocr to trainingEvent", () => {
        const settings = {
            ocr: { ocrConfidence: 85 },
            trainingEvent: { ocrConfidence: 90 },
        } as any

        const { settings: migrated, anyMigrated } = applyMigrations(settings)
        expect(anyMigrated).toBe(true)
        expect(migrated.trainingEvent.ocrConfidence).toBe(85)
        expect((migrated as any).ocr?.ocrConfidence).toBeUndefined()
    })

    it("migrates enableAutomaticOCRRetry from ocr to trainingEvent", () => {
        const settings = {
            ocr: { enableAutomaticOCRRetry: false },
            trainingEvent: { enableAutomaticOCRRetry: true },
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect(migrated.trainingEvent.enableAutomaticOCRRetry).toBe(false)
    })

    it("migrates enableHideOCRComparisonResults from debug to trainingEvent", () => {
        const settings = {
            debug: { enableHideOCRComparisonResults: false },
            trainingEvent: { enableHideOCRComparisonResults: true },
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect(migrated.trainingEvent.enableHideOCRComparisonResults).toBe(false)
    })

    it("migrates ocrThreshold from ocr to debug", () => {
        const settings = {
            ocr: { ocrThreshold: 0.8 },
            debug: { ocrThreshold: 0.7 },
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect(migrated.debug.ocrThreshold).toBe(0.8)
    })

    it("deletes empty ocr object after all fields migrated", () => {
        const settings = {
            ocr: { ocrConfidence: 85 },
            trainingEvent: { ocrConfidence: 90 },
            debug: {},
        } as any

        const { settings: migrated } = applyMigrations(settings)
        expect((migrated as any).ocr).toBeUndefined()
    })

    it("migrates stopAtDate string to stopAtDates array", () => {
        const settings = {
            general: { stopAtDate: "Senior January Early", stopAtDates: [] },
        } as any

        const { settings: migrated, anyMigrated } = applyMigrations(settings)
        expect(anyMigrated).toBe(true)
        expect(migrated.general.stopAtDates).toEqual(["Senior January Early"])
        expect((migrated.general as any).stopAtDate).toBeUndefined()
    })

    it("returns anyMigrated=false when no migration needed", () => {
        const settings = {
            general: { stopAtDates: ["Senior January Early"] },
            trainingEvent: { ocrConfidence: 90 },
            debug: { ocrThreshold: 0.7 },
        } as any

        const { anyMigrated } = applyMigrations(settings)
        expect(anyMigrated).toBe(false)
    })

    it("is idempotent: running twice produces same result", () => {
        const settings = {
            ocr: { ocrConfidence: 85 },
            trainingEvent: { ocrConfidence: 90 },
            debug: {},
            general: { stopAtDate: "Senior January Early", stopAtDates: [] },
        } as any

        const { settings: first } = applyMigrations(settings)
        const { settings: second, anyMigrated } = applyMigrations(first)
        expect(anyMigrated).toBe(false)
        expect(second).toEqual(first)
    })

    it("migrates upstream 5.7 Trackblazer charm threshold key rename", () => {
        const raw = {
            scenarioOverrides: { trackblazerSkipRiskyCharmTrainingBelowGain: 30 },
        } as any
        const settings = deepMerge(testDefaults, raw)

        const { settings: migrated, anyMigrated } = applyMigrations(settings, raw)
        expect(anyMigrated).toBe(true)
        expect(migrated.scenarioOverrides.trackblazerMinStatGainForCharm).toBe(30)
        expect(migrated.scenarioOverrides.trackblazerSkipRiskyCharmTrainingBelowGain).toBeUndefined()
    })

    it("migrates upstream 5.7 Trackblazer bad-mood item floor key rename", () => {
        const raw = {
            scenarioOverrides: { trackblazerSkipBadMoodItemsBelowGain: 20 },
        } as any
        const settings = deepMerge(testDefaults, raw)

        const { settings: migrated, anyMigrated } = applyMigrations(settings, raw)
        expect(anyMigrated).toBe(true)
        expect(migrated.scenarioOverrides.trackblazerLowMainStatGainItemFloor).toBe(20)
        expect(migrated.scenarioOverrides.trackblazerSkipBadMoodItemsBelowGain).toBeUndefined()
    })

    it("drops upstream-only trackblazerForceTrainEnergyFloor", () => {
        const raw = {
            scenarioOverrides: { trackblazerForceTrainEnergyFloor: 50 },
        } as any
        const settings = deepMerge(testDefaults, raw)

        const { settings: migrated, anyMigrated } = applyMigrations(settings, raw)
        expect(anyMigrated).toBe(true)
        expect(migrated.scenarioOverrides.trackblazerForceTrainEnergyFloor).toBeUndefined()
    })

    it("migrates enablePrioritizeNearMaxFriendship to trainerFriendshipInfluence", () => {
        const rawTrue = { training: { enablePrioritizeNearMaxFriendship: true } } as any
        const settingsTrue = deepMerge(testDefaults, rawTrue)
        const { settings: migratedTrue } = applyMigrations(settingsTrue, rawTrue)
        expect(migratedTrue.training.trainerFriendshipInfluence).toBe(100)
        expect(migratedTrue.training.enablePrioritizeNearMaxFriendship).toBeUndefined()

        const rawFalse = { training: { enablePrioritizeNearMaxFriendship: false } } as any
        const settingsFalse = deepMerge(testDefaults, rawFalse)
        const { settings: migratedFalse } = applyMigrations(settingsFalse, rawFalse)
        expect(migratedFalse.training.trainerFriendshipInfluence).toBe(0)
    })

    it("drops upstream-only training keys not supported by the custom fork", () => {
        const raw = {
            training: { enableTrainingLevelWeighting: true, disableStatTargets: true },
        } as any
        const settings = deepMerge(testDefaults, raw)

        const { settings: migrated, anyMigrated } = applyMigrations(settings, raw)
        expect(anyMigrated).toBe(true)
        expect(migrated.training.enableTrainingLevelWeighting).toBeUndefined()
        expect(migrated.training.disableStatTargets).toBeUndefined()
    })

    it("migrates debug overlay settings to misc (upstream 5.7+)", () => {
        const raw = {
            debug: { enableMessageIdDisplay: true, overlayButtonSizeDP: 48 },
        } as any
        const settings = deepMerge(testDefaults, raw)

        const { settings: migrated, anyMigrated } = applyMigrations(settings, raw)
        expect(anyMigrated).toBe(true)
        expect(migrated.misc.enableMessageIdDisplay).toBe(true)
        expect(migrated.misc.overlayButtonSizeDP).toBe(48)
        expect(migrated.debug.enableMessageIdDisplay).toBeUndefined()
        expect(migrated.debug.overlayButtonSizeDP).toBeUndefined()
    })

    it("drops removed enableSwipeBasedScrolling and enablePauseResume", () => {
        const raw = {
            general: { enableSwipeBasedScrolling: true },
            debug: { enablePauseResume: true },
        } as any
        const settings = deepMerge(testDefaults, raw)

        const { settings: migrated, anyMigrated } = applyMigrations(settings, raw)
        expect(anyMigrated).toBe(true)
        expect(migrated.general.enableSwipeBasedScrolling).toBeUndefined()
        expect(migrated.debug.enablePauseResume).toBeUndefined()
    })
})

// ===========================================================================
// normalizeImportedSettings
// ===========================================================================

describe("normalizeImportedSettings", () => {
    it("merges partial upstream 5.7 export with defaults and applies migrations", () => {
        const upstreamExport = {
            general: { scenario: "Trackblazer" },
            training: { enablePrioritizeNearMaxFriendship: true, maximumFailureChance: 35 },
            scenarioOverrides: {
                trackblazerSkipRiskyCharmTrainingBelowGain: 28,
                trackblazerSkipBadMoodItemsBelowGain: 18,
                trackblazerForceTrainEnergyFloor: 40,
            },
            debug: { enableMessageIdDisplay: false },
        } as any

        const normalized = normalizeImportedSettings(upstreamExport, testDefaults)
        expect(normalized.general.scenario).toBe("Trackblazer")
        expect(normalized.training.maximumFailureChance).toBe(35)
        expect(normalized.training.trainerFriendshipInfluence).toBe(100)
        expect(normalized.scenarioOverrides.trackblazerMinStatGainForCharm).toBe(28)
        expect(normalized.scenarioOverrides.trackblazerLowMainStatGainItemFloor).toBe(18)
        expect(normalized.scenarioOverrides.trackblazerForceTrainEnergyFloor).toBeUndefined()
        expect(normalized.misc.enableMessageIdDisplay).toBe(false)
    })
})
