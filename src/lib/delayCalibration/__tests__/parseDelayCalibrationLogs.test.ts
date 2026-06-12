import { parseDelayCalibrationLogs } from "../parseDelayCalibrationLogs"

describe("parseDelayCalibrationLogs", () => {
    it("aggregates structured DELAY_CAL lines", () => {
        const entries = [
            {
                id: 1,
                message: "[DELAY_CAL] action=training.enter success=true plannedSec=0.1 detectToActionMs=120 retry=false failKind=none",
            },
            {
                id: 2,
                message: "[DELAY_CAL] action=training.enter success=false plannedSec=0.1 detectToActionMs=80 retry=true failKind=too_fast",
            },
            {
                id: 3,
                message: "[DELAY_CAL] action=trackblazer.itemAnimation success=true plannedSec=4 detectToActionMs=4100 retry=false failKind=none",
            },
        ]

        const stats = parseDelayCalibrationLogs(entries)
        expect(stats["training.enter"].totalExecutions).toBe(2)
        expect(stats["training.enter"].failureCount).toBe(1)
        expect(stats["training.enter"].tooFastCount).toBe(1)
        expect(stats["training.enter"].suggestedDelaySec).toBe(0.1)
        expect(stats["trackblazer.itemAnimation"].totalExecutions).toBe(1)
        expect(stats["trackblazer.itemAnimation"].suggestedDelaySec).toBe(4.1)
    })

    it("maps failure log patterns when structured lines are absent", () => {
        const stats = parseDelayCalibrationLogs([
            { id: 1, message: "[WARN] getActiveStat:: Timed out while trying to detect the active stat." },
        ])
        expect(stats["training.enter"].totalExecutions).toBe(1)
        expect(stats["training.enter"].failureCount).toBe(1)
        expect(stats["training.enter"].tooFastCount).toBe(1)
    })
})
