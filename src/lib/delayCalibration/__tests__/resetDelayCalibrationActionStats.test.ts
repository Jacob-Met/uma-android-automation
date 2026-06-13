import { resetDelayCalibrationActionStats } from "../resetDelayCalibrationActionStats"
import { DelayCalibrationActionStats } from "../types"

describe("resetDelayCalibrationActionStats", () => {
    it("clears session counters but keeps suggested timing", () => {
        const before: DelayCalibrationActionStats = {
            totalExecutions: 12,
            failureCount: 3,
            tooFastCount: 2,
            tooSlowCount: 1,
            suggestedDelaySec: 0.85,
            avgSuccessDelayMs: 812,
        }

        const result = resetDelayCalibrationActionStats({ game_startup: before }, "game_startup")

        expect(result.game_startup).toEqual({
            totalExecutions: 0,
            failureCount: 0,
            tooFastCount: 0,
            tooSlowCount: 0,
            suggestedDelaySec: 0.85,
            avgSuccessDelayMs: 812,
        })
    })

    it("leaves other actions unchanged", () => {
        const stats = {
            a: {
                totalExecutions: 5,
                failureCount: 1,
                tooFastCount: 1,
                tooSlowCount: 0,
                suggestedDelaySec: 1,
                avgSuccessDelayMs: 900,
            },
            b: {
                totalExecutions: 2,
                failureCount: 0,
                tooFastCount: 0,
                tooSlowCount: 0,
                suggestedDelaySec: null,
                avgSuccessDelayMs: null,
            },
        }

        const result = resetDelayCalibrationActionStats(stats, "a")

        expect(result.b).toBe(stats.b)
    })

    it("returns the original map when the action has no stats", () => {
        expect(resetDelayCalibrationActionStats(undefined, "missing")).toEqual({})
        expect(resetDelayCalibrationActionStats({ a: { totalExecutions: 1, failureCount: 0, tooFastCount: 0, tooSlowCount: 0, suggestedDelaySec: null, avgSuccessDelayMs: null } }, "missing")).toEqual({
            a: { totalExecutions: 1, failureCount: 0, tooFastCount: 0, tooSlowCount: 0, suggestedDelaySec: null, avgSuccessDelayMs: null },
        })
    })
})
