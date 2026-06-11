/** Parses `skillHintLevels` storage (`"id:level,id:level"`). */
export function parseSkillHintLevels(raw: string | undefined): Record<number, number> {
    if (!raw) return {}
    const result: Record<number, number> = {}
    for (const part of raw.split(",")) {
        const trimmed = part.trim()
        if (!trimmed) continue
        const [idPart, levelPart] = trimmed.split(":")
        const id = Number(idPart)
        const level = Number(levelPart)
        if (!Number.isInteger(id) || !Number.isInteger(level)) continue
        result[id] = Math.min(5, Math.max(0, level))
    }
    return result
}

/** Serializes per-skill hint overrides, omitting empty maps. */
export function serializeSkillHintLevels(levels: Record<number, number>): string {
    return Object.entries(levels)
        .filter(([, level]) => Number.isInteger(level) && level >= 0 && level <= 5)
        .sort(([a], [b]) => Number(a) - Number(b))
        .map(([id, level]) => `${id}:${level}`)
        .join(",")
}

export function countSkillHintLevelOverrides(raw: string | undefined): number {
    return Object.keys(parseSkillHintLevels(raw)).length
}
