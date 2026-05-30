import { Platform, View, type StyleProp, type ViewStyle } from "react-native"
import { BlurView } from "expo-blur"
import { useTheme } from "../../context/ThemeContext"

/** Props for `GlassSurface`. */
export interface GlassSurfaceProps {
    /** Blur intensity 0-100. Default 30. */
    intensity?: number
    /** Tint mode. `"auto"` picks light/dark from the active theme. */
    tint?: "light" | "dark" | "auto"
    /** Children rendered above the blur layer. */
    children?: React.ReactNode
    /** Outer container style. */
    style?: StyleProp<ViewStyle>
}

/**
 * BlurView-backed translucent container. Falls back to a solid `glassBackground` color on Android API < 31 where BlurView is unsupported.
 *
 * @param props See `GlassSurfaceProps`.
 * @returns BlurView wrapper or solid fallback.
 */
export const GlassSurface = ({ intensity = 30, tint = "auto", children, style }: GlassSurfaceProps) => {
    const { colors, theme } = useTheme()
    const resolvedTint: "light" | "dark" = tint === "auto" ? (theme === "dark" ? "dark" : "light") : tint
    const supportsBlur = Number(Platform.Version) >= 31

    if (!supportsBlur) {
        return <View style={[{ backgroundColor: colors.glassBackground, borderColor: colors.glassBorder, borderWidth: 1 }, style]}>{children}</View>
    }

    return (
        <BlurView intensity={intensity} tint={resolvedTint} style={[{ overflow: "hidden", borderColor: colors.glassBorder, borderWidth: 1 }, style]}>
            {children}
        </BlurView>
    )
}
