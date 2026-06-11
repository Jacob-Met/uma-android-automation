import { Settings } from "../context/BotStateContext"
import { DB_OWNED_KEYS } from "./settingsUtils"

/** Settings categories stored per detected Uma Musume (training event overrides stay global). */
export const UMA_PRESET_CATEGORIES = ["training", "trainingStatTarget", "racing", "skills", "scenarioOverrides"] as const

export type UmaPresetCategory = (typeof UMA_PRESET_CATEGORIES)[number]

/** Normalize OCR / user-entered Uma names for preset lookup. */
export const normalizeUmaPresetName = (name: string): string => name.trim().replace(/\s+/g, " ").toLowerCase()

/**
 * Extracts the subset of settings saved into an Uma preset.
 * Excludes global/system slices and Kotlin-owned racing blobs.
 */
export const extractUmaPresetSettings = (settings: Settings): Partial<Settings> => {
    const result: Partial<Settings> = {}

    for (const category of UMA_PRESET_CATEGORIES) {
        const slice = settings[category]
        if (!slice || typeof slice !== "object") {
            continue
        }

        const copy = { ...(slice as Record<string, unknown>) }
        for (const [ownedCategory, ownedKey] of DB_OWNED_KEYS) {
            if (ownedCategory === category) {
                delete copy[ownedKey]
            }
        }
        ;(result as Record<string, unknown>)[category] = copy
    }

    return result
}
