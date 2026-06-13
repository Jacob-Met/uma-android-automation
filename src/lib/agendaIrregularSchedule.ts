export type AgendaScheduleEntry = {
    status: "learning" | "ready"
    turns: Record<string, string>
    locked?: boolean
}

export type AgendaSchedulesMap = Record<string, AgendaScheduleEntry>

export const AGENDA_SLOT_OPTIONS = [
    "Agenda 1",
    "Agenda 2",
    "Agenda 3",
    "Agenda 4",
    "Agenda 5",
    "Agenda 6",
    "Agenda 7",
    "Agenda 8",
] as const

/** Storage key for irregular schedules — always the selected agenda slot, not the in-game custom title. */
export const getAgendaScheduleKey = (selectedUserAgenda: string | undefined): string =>
    (selectedUserAgenda ?? "").trim() || "Agenda 1"

export const AGENDA_AUTOFILL_RECORDABLE_TURN_MIN = 12
export const AGENDA_AUTOFILL_RECORDABLE_TURN_MAX = 72

/** Turns autofill should count/record — excludes climax and pre-debut unless allowed. */
export const isAgendaAutofillRecordableTurn = (turn: number, allowPreDebut: boolean): boolean => {
    if (turn > AGENDA_AUTOFILL_RECORDABLE_TURN_MAX) {
        return false
    }
    if (turn < AGENDA_AUTOFILL_RECORDABLE_TURN_MIN && !allowPreDebut) {
        return false
    }
    return true
}

export const countRecordableAgendaTurns = (
    turns: Record<string, string>,
    allowPreDebut: boolean
): number =>
    Object.keys(turns).filter((turn) => isAgendaAutofillRecordableTurn(Number(turn), allowPreDebut)).length

/** Legacy key used before schedules were keyed by agenda slot (custom title or slot name). */
export const getLegacyAgendaScheduleKey = (selectedUserAgenda: string | undefined, customAgendaTitle: string | undefined): string =>
    (customAgendaTitle || selectedUserAgenda || "Agenda 1").trim()

export const parseAgendaSchedules = (raw: string | undefined): AgendaSchedulesMap => {
    try {
        const parsed = JSON.parse(raw || "{}") as unknown
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            return {}
        }
        return parsed as AgendaSchedulesMap
    } catch {
        return {}
    }
}

export const parseUserAgendaCustomTitles = (raw: string | undefined): Record<string, string> => {
    try {
        const parsed = JSON.parse(raw || "{}") as unknown
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            return {}
        }
        return parsed as Record<string, string>
    } catch {
        return {}
    }
}

/**
 * Copies a schedule from a legacy effective-name key onto the agenda slot key when needed.
 * Keeps the legacy entry so older builds / exports are not destroyed.
 */
export const ensureScheduleForAgendaSlot = (
    schedules: AgendaSchedulesMap,
    slot: string,
    legacyKey?: string
): { schedules: AgendaSchedulesMap; migrated: boolean } => {
    const scheduleKey = getAgendaScheduleKey(slot)
    if (schedules[scheduleKey]) {
        return { schedules, migrated: false }
    }
    const legacy = legacyKey && legacyKey !== scheduleKey ? schedules[legacyKey] : undefined
    if (!legacy) {
        return { schedules, migrated: false }
    }
    return {
        schedules: { ...schedules, [scheduleKey]: legacy },
        migrated: true,
    }
}

export type AgendaSwitchInput = {
    currentSlot: string
    nextSlot: string
    currentCustomTitle: string
    customTitlesJson: string | undefined
    schedulesJson: string | undefined
}

export type AgendaSwitchOutput = {
    selectedUserAgenda: string
    customAgendaTitle: string
    userAgendaCustomTitles: string
    trackblazerAgendaIrregularSchedules?: string
}

/** Persists the current slot's custom title + schedule, then loads the next slot's settings. */
export const buildAgendaSwitchUpdate = ({
    currentSlot,
    nextSlot,
    currentCustomTitle,
    customTitlesJson,
    schedulesJson,
}: AgendaSwitchInput): AgendaSwitchOutput => {
    const customTitles = parseUserAgendaCustomTitles(customTitlesJson)
    customTitles[getAgendaScheduleKey(currentSlot)] = currentCustomTitle

    let schedules = parseAgendaSchedules(schedulesJson)
    const currentLegacyKey = getLegacyAgendaScheduleKey(currentSlot, currentCustomTitle)
    const currentMigration = ensureScheduleForAgendaSlot(schedules, currentSlot, currentLegacyKey)
    schedules = currentMigration.schedules

    const nextCustomTitle = customTitles[getAgendaScheduleKey(nextSlot)] ?? ""
    const nextLegacyKey = getLegacyAgendaScheduleKey(nextSlot, nextCustomTitle)
    const nextMigration = ensureScheduleForAgendaSlot(schedules, nextSlot, nextLegacyKey)
    schedules = nextMigration.schedules

    const output: AgendaSwitchOutput = {
        selectedUserAgenda: nextSlot,
        customAgendaTitle: nextCustomTitle,
        userAgendaCustomTitles: JSON.stringify(customTitles),
    }

    if (currentMigration.migrated || nextMigration.migrated) {
        output.trackblazerAgendaIrregularSchedules = JSON.stringify(schedules)
    }

    return output
}
