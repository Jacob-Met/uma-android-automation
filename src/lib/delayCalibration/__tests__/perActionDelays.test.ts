import { parsePerActionDelayOverrides, serializePerActionDelayOverrides } from "../perActionDelays"

describe("parsePerActionDelayOverrides", () => {
    it("parses a JSON string map", () => {
        expect(parsePerActionDelayOverrides('{"game.startup":3}')).toEqual({ "game.startup": 3 })
    })

    it("accepts a legacy object value loaded from SQLite", () => {
        expect(parsePerActionDelayOverrides({ "game.startup": 3, bad: "x" })).toEqual({ "game.startup": 3 })
    })

    it("returns empty map for blank input", () => {
        expect(parsePerActionDelayOverrides(undefined)).toEqual({})
        expect(parsePerActionDelayOverrides("{}")).toEqual({})
    })
})

describe("serializePerActionDelayOverrides", () => {
    it("serializes overrides to JSON", () => {
        expect(serializePerActionDelayOverrides({ "game.startup": 3 })).toBe('{"game.startup":3}')
    })
})
