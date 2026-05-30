import { SPACING } from "../spacing"

describe("spacing tokens", () => {
    it("exposes the 4/8-grid scale", () => {
        expect(SPACING).toEqual({ xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 })
    })

    it("keys are ordered smallest to largest", () => {
        const values = Object.values(SPACING)
        const sorted = [...values].sort((a, b) => a - b)
        expect(values).toEqual(sorted)
    })
})
