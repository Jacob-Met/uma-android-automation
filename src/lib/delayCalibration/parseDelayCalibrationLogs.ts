import { MessageLogEntry } from "../../context/MessageLogContext"
import { DELAY_CALIBRATION_ACTIONS, getDelayCalibrationAction } from "./registry"
import { DelayCalibrationActionStats, emptyDelayCalibrationActionStats } from "./types"

const DELAY_CAL_REGEX =
    /\[DELAY_CAL\]\s+action=(\S+)\s+success=(true|false)\s+plannedSec=([\d.]+)\s+detectToActionMs=(-?\d+)\s+retry=(true|false)\s+failKind=(\S+)/

/**
 * Parses message log entries from a home-button calibration session into per-action stats.
 */
export const parseDelayCalibrationLogs = (entries: MessageLogEntry[]): Record<string, DelayCalibrationActionStats> => {
    const stats: Record<string, DelayCalibrationActionStats> = {}
    const successSamplesMs: Record<string, number[]> = {}
    const retrySamplesMs: Record<string, number[]> = {}

    const ensure = (actionId: string): DelayCalibrationActionStats => {
        if (!stats[actionId]) {
            stats[actionId] = emptyDelayCalibrationActionStats()
        }
        return stats[actionId]
    }

    for (const entry of entries) {
        const msg = entry.message

        const structured = msg.match(DELAY_CAL_REGEX)
        if (structured) {
            const [, actionId, successStr, , detectMsStr, retryStr, failKind] = structured
            const action = getDelayCalibrationAction(actionId)
            if (!action) continue

            const stat = ensure(action.id)
            stat.totalExecutions += 1
            const success = successStr === "true"
            const detectMs = parseInt(detectMsStr, 10)
            const isRetry = retryStr === "true"

            if (success) {
                if (detectMs >= 0) {
                    if (!successSamplesMs[action.id]) successSamplesMs[action.id] = []
                    successSamplesMs[action.id].push(detectMs)
                }
                if (isRetry && detectMs >= 0) {
                    if (!retrySamplesMs[action.id]) retrySamplesMs[action.id] = []
                    retrySamplesMs[action.id].push(detectMs)
                }
            } else {
                stat.failureCount += 1
                if (failKind === "too_fast" || isRetry) {
                    stat.tooFastCount += 1
                    if (detectMs >= 0) {
                        if (!retrySamplesMs[action.id]) retrySamplesMs[action.id] = []
                        retrySamplesMs[action.id].push(detectMs)
                    }
                } else if (failKind === "too_slow") {
                    stat.tooSlowCount += 1
                }
            }
            continue
        }

        for (const action of DELAY_CALIBRATION_ACTIONS) {
            if (action.failurePatterns.some((p) => p.test(msg))) {
                const stat = ensure(action.id)
                stat.totalExecutions += 1
                stat.failureCount += 1
                stat.tooFastCount += 1
                break
            }
        }
    }

    for (const action of DELAY_CALIBRATION_ACTIONS) {
        const stat = stats[action.id]
        if (!stat) continue

        const successMs = successSamplesMs[action.id] ?? []
        const retryMs = retrySamplesMs[action.id] ?? []

        if (successMs.length > 0) {
            const avg = successMs.reduce((a, b) => a + b, 0) / successMs.length
            stat.avgSuccessDelayMs = Math.round(avg)
            stat.suggestedDelaySec = Math.round((avg / 1000) * 20) / 20
        } else if (retryMs.length > 0 && stat.tooFastCount > 0) {
            const avgRetry = retryMs.reduce((a, b) => a + b, 0) / retryMs.length
            stat.avgSuccessDelayMs = Math.round(avgRetry)
            stat.suggestedDelaySec = Math.round((avgRetry / 1000 + 0.05) * 20) / 20
        }
    }

    return stats
}
