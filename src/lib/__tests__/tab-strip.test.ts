import { resolveActiveIndex } from "../../components/ui/tab-strip.helpers"

describe("resolveActiveIndex", () => {
    it("returns the index matching the active key", () => {
        expect(resolveActiveIndex(["a", "b", "c"], "b")).toBe(1)
    })
    it("returns 0 when no key matches", () => {
        expect(resolveActiveIndex(["a", "b", "c"], "z")).toBe(0)
    })
    it("returns 0 for empty key array", () => {
        expect(resolveActiveIndex([], "anything")).toBe(0)
    })
})
