import type { ViewStyle } from "react-native"

/**
 * Outer-wrapper style for long-press option rows. Sits ABOVE the inner Pressable. Carries the rounded
 * clipping outline and the outward extension, so Android's view clipping rounds off the inner
 * Pressable's foreground ripple on every API level (foreground drawables ignore `clipToOutline`
 * on API <= 28, so the clip has to come from an ancestor).
 */
export const pressSurfaceOuter: ViewStyle = {
    marginHorizontal: 0,
    borderRadius: 24,
    overflow: "hidden",
}

/**
 * Inner-Pressable style. Padding gives the ripple breathing room around the content.
 */
export const pressSurfaceInner: ViewStyle = {
    paddingHorizontal: 8,
    paddingVertical: 8,
}

/**
 * Square Pressable style clipped to a perfect circle. Use for icon-only press targets so the foreground
 * ripple respects the circular outline (via `overflow: 'hidden'`) on every Android API level.
 * @param size The width/height of the square in pixels. The border radius is derived as half of this.
 * @returns A ViewStyle suitable for spreading onto a `Pressable.style`.
 */
export const circularPress = (size: number = 40): ViewStyle => ({
    width: size,
    height: size,
    borderRadius: size / 2,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
})
