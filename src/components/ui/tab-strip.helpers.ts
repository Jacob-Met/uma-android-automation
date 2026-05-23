// //////////////////////////////////////////////////////////////////////////////////////////////////
// //////////////////////////////////////////////////////////////////////////////////////////////////
// TabStrip helpers
//
// Pure helpers extracted from `tab-strip.tsx` so they can be unit-tested without pulling React Native into the jest module graph.

/** Resolve the index for a given active key against the ordered key list. Falls back to 0. */
export function resolveActiveIndex(keys: string[], activeKey: string): number {
    const idx = keys.indexOf(activeKey)
    return idx >= 0 ? idx : 0
}
