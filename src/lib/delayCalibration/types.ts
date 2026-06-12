/** Per-action stats accumulated from a home-button calibration session. */
export interface DelayCalibrationActionStats {
    totalExecutions: number
    failureCount: number
    tooFastCount: number
    tooSlowCount: number
    /** Suggested delay in seconds based on successful timing samples. */
    suggestedDelaySec: number | null
    /** Average detection-to-action ms on successful runs. */
    avgSuccessDelayMs: number | null
}

export const emptyDelayCalibrationActionStats = (): DelayCalibrationActionStats => ({
    totalExecutions: 0,
    failureCount: 0,
    tooFastCount: 0,
    tooSlowCount: 0,
    suggestedDelaySec: null,
    avgSuccessDelayMs: null,
})
