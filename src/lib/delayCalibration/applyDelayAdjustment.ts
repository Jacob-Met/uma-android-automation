import { Settings } from "../../context/BotStateContext"
import { DelayCalibrationActionDefinition } from "./registry"
import { parsePerActionDelayOverrides, serializePerActionDelayOverrides } from "./perActionDelays"

/** Applies a +/- delta to the delay backing an action, clamped to [0, 10] seconds. */
export const applyDelayAdjustment = (
    settings: Settings,
    action: DelayCalibrationActionDefinition,
    deltaSec: number
): Partial<Settings> => {
    const src = action.delaySource
    const clamp = (v: number) => Math.max(0, Math.min(10, Math.round(v * 100) / 100))

    if (src.type === "settings") {
        const slice = settings[src.category] as Record<string, number>
        const current = slice[src.key] ?? 0
        return {
            [src.category]: {
                ...settings[src.category],
                [src.key]: clamp(current + deltaSec),
            },
        } as Partial<Settings>
    }

    const overrides = parsePerActionDelayOverrides(settings.advanced.perActionDelayOverrides)
    const current = overrides[src.key] ?? src.defaultValue
    overrides[src.key] = clamp(current + deltaSec)
    return {
        advanced: {
            ...settings.advanced,
            perActionDelayOverrides: serializePerActionDelayOverrides(overrides),
        },
    }
}
