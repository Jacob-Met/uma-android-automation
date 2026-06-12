/** Parses the advanced.perActionDelayOverrides JSON string (or legacy object form from SQLite). */
export const parsePerActionDelayOverrides = (json: string | Record<string, unknown> | undefined | null): Record<string, number> => {
    if (json == null) return {}

    if (typeof json === "object" && !Array.isArray(json)) {
        const out: Record<string, number> = {}
        for (const [key, val] of Object.entries(json)) {
            if (typeof val === "number" && Number.isFinite(val)) {
                out[key] = val
            }
        }
        return out
    }

    if (typeof json !== "string" || json.trim() === "") return {}

    try {
        const parsed = JSON.parse(json) as unknown
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {}
        const out: Record<string, number> = {}
        for (const [key, val] of Object.entries(parsed as Record<string, unknown>)) {
            if (typeof val === "number" && Number.isFinite(val)) {
                out[key] = val
            }
        }
        return out
    } catch {
        return {}
    }
}

export const serializePerActionDelayOverrides = (overrides: Record<string, number>): string => JSON.stringify(overrides)
