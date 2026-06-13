export type UniqueRaceOverrideConfig = {
    strategy?: string
    enableIrregularTraining?: boolean
    irregularTrainingMinStatGain?: number
    enableRetryOverride?: boolean
    maxRetries?: number
}

export type UniqueRaceOverridesMap = Record<string, UniqueRaceOverrideConfig>

const EMPTY_CONFIG: UniqueRaceOverrideConfig = {
    strategy: "Default",
    enableIrregularTraining: false,
    enableRetryOverride: false,
}

/** Normalizes legacy string-only entries and object entries into a config map. */
export const parseUniqueRaceOverrides = (json: string | undefined): UniqueRaceOverridesMap => {
    if (!json || json.trim() === "") {
        return {}
    }
    try {
        const parsed = JSON.parse(json) as unknown
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            return {}
        }
        const out: UniqueRaceOverridesMap = {}
        for (const [rawKey, rawValue] of Object.entries(parsed as Record<string, unknown>)) {
            const key = rawKey.trim()
            if (!key) continue
            if (typeof rawValue === "string") {
                out[key] = {
                    strategy: rawValue.trim() || "Default",
                    enableIrregularTraining: false,
                }
                continue
            }
            if (rawValue && typeof rawValue === "object" && !Array.isArray(rawValue)) {
                const obj = rawValue as Record<string, unknown>
                out[key] = {
                    strategy: typeof obj.strategy === "string" ? obj.strategy : "Default",
                    enableIrregularTraining: obj.enableIrregularTraining === true,
                    irregularTrainingMinStatGain:
                        typeof obj.irregularTrainingMinStatGain === "number" ? obj.irregularTrainingMinStatGain : undefined,
                    enableRetryOverride: obj.enableRetryOverride === true,
                    maxRetries: typeof obj.maxRetries === "number" ? obj.maxRetries : undefined,
                }
            }
        }
        return out
    } catch {
        return {}
    }
}

/** Returns true when the override has a non-default strategy or irregular training enabled. */
export const uniqueRaceOverrideIsActive = (config: UniqueRaceOverrideConfig | undefined): boolean => {
    if (!config) return false
    const strategy = config.strategy?.trim() || "Default"
    return strategy !== "Default" || config.enableIrregularTraining === true || config.enableRetryOverride === true
}

/** Serializes overrides, dropping entries with no active settings. */
export const serializeUniqueRaceOverrides = (overrides: UniqueRaceOverridesMap): string => {
    const out: Record<string, UniqueRaceOverrideConfig> = {}
    for (const [key, config] of Object.entries(overrides)) {
        if (!uniqueRaceOverrideIsActive(config)) continue
        out[key] = {
            ...(config.strategy && config.strategy !== "Default" ? { strategy: config.strategy } : {}),
            ...(config.enableIrregularTraining ? { enableIrregularTraining: true } : {}),
            ...(config.enableIrregularTraining && config.irregularTrainingMinStatGain !== undefined
                ? { irregularTrainingMinStatGain: config.irregularTrainingMinStatGain }
                : {}),
            ...(config.enableRetryOverride ? { enableRetryOverride: true, ...(config.maxRetries !== undefined ? { maxRetries: config.maxRetries } : {}) } : {}),
        }
    }
    return JSON.stringify(out)
}

export const defaultUniqueRaceOverrideConfig = (): UniqueRaceOverrideConfig => ({ ...EMPTY_CONFIG })
