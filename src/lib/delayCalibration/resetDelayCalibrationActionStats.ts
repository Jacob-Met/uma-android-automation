import { DelayCalibrationActionStats } from "./types"

/** Clears session execution counters while keeping suggested timing from the last calibration run. */
export const resetDelayCalibrationActionStats = (
    stats: Record<string, DelayCalibrationActionStats> | undefined,
    actionId: string
): Record<string, DelayCalibrationActionStats> => {
    const current = stats?.[actionId]
    if (!current) {
        return stats ?? {}
    }

    return {
        ...(stats ?? {}),
        [actionId]: {
            suggestedDelaySec: current.suggestedDelaySec,
            avgSuccessDelayMs: current.avgSuccessDelayMs,
            totalExecutions: 0,
            failureCount: 0,
            tooFastCount: 0,
            tooSlowCount: 0,
        },
    }
}
