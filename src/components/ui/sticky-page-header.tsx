import { View, type LayoutChangeEvent, type StyleProp, type ViewStyle } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { SPACING } from "../../lib/spacing"

/** Props for `StickyPageHeader`. */
export interface StickyPageHeaderProps {
    /** Header children (title, left slot, right slot). */
    children: React.ReactNode
    /** Outer container style override. */
    style?: StyleProp<ViewStyle>
    /** Forwarded `onLayout` for callers that need to measure the rendered header height (e.g. to position an overlay flush below it). */
    onLayout?: (event: LayoutChangeEvent) => void
}

/**
 * Top-bar wrapper for the app's primary page header. Renders a solid `bg` background. Consumers should mount this as a sibling
 * above their main scroll container - not inside a `ScrollView` - so the inner `Pressable`s do not lose touches to the parent
 * scroll view's pan-responder on Android.
 *
 * @param props See `StickyPageHeaderProps`.
 * @returns Solid top bar.
 */
export const StickyPageHeader = ({ children, style, onLayout }: StickyPageHeaderProps) => {
    const { colors } = useTheme()
    return (
        <View
            onLayout={onLayout}
            style={[
                {
                    backgroundColor: colors.bg,
                    paddingHorizontal: 0,
                    paddingVertical: SPACING.sm,
                },
                style,
            ]}
        >
            {children}
        </View>
    )
}
