import { Settings } from "../../context/BotStateContext"
import { applyDelayAdjustment } from "./applyDelayAdjustment"
import { DELAY_CALIBRATION_ACTIONS, getCurrentDelayForAction } from "./registry"
import { resetDelayCalibrationActionStats } from "./resetDelayCalibrationActionStats"
import { DelayCalibrationActionStats } from "./types"

const mergeSettingsPatch = (settings: Settings, patch: Partial<Settings>): Settings => ({
    ...settings,
    ...(patch.general ? { general: { ...settings.general, ...patch.general } } : {}),
    ...(patch.racing ? { racing: { ...settings.racing, ...patch.racing } } : {}),
    ...(patch.advanced ? { advanced: { ...settings.advanced, ...patch.advanced } } : {}),
})

const withResetStats = (settings: Settings, actionId: string): Settings => ({
    ...settings,
    advanced: {
        ...settings.advanced,
        delayCalibrationStats: resetDelayCalibrationActionStats(settings.advanced.delayCalibrationStats, actionId),
    },
})

/** Applies suggested delay values from calibration stats to the matching settings keys. */
export const applyAllSuggestedDelays = (settings: Settings, stats: Record<string, DelayCalibrationActionStats>): Settings => {
    let next = settings
    for (const action of DELAY_CALIBRATION_ACTIONS) {
        const suggested = stats[action.id]?.suggestedDelaySec
        if (suggested == null) continue
        const current = getCurrentDelayForAction(action, next)
        const delta = suggested - current
        if (Math.abs(delta) < 0.001) continue
        next = mergeSettingsPatch(next, applyDelayAdjustment(next, action, delta))
        next = withResetStats(next, action.id)
    }
    return next
}

/** Applies a single action's suggested delay, if present. */
export const applySuggestedDelayForAction = (settings: Settings, actionId: string, stats: Record<string, DelayCalibrationActionStats>): Settings => {
    const action = DELAY_CALIBRATION_ACTIONS.find((a) => a.id === actionId)
    const suggested = stats[actionId]?.suggestedDelaySec
    if (!action || suggested == null) return settings
    const current = getCurrentDelayForAction(action, settings)
    const delta = suggested - current
    if (Math.abs(delta) < 0.001) return settings
    const patched = mergeSettingsPatch(settings, applyDelayAdjustment(settings, action, delta))
    return withResetStats(patched, actionId)
}
