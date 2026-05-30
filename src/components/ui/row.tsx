import { Pressable, Text, View, type StyleProp, type ViewStyle } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { TYPE } from "../../lib/type"
import { SPACING } from "../../lib/spacing"
import { ROW_PADDING_Y } from "../../lib/density"

/** Props for `Row`. */
export interface RowProps {
    /** Primary label rendered in `TYPE.body`. */
    title: string
    /** Optional secondary line rendered in `TYPE.caption` with `textMuted` color. */
    description?: string
    /** Right-aligned slot for controls, mono values, or chevrons. */
    right?: React.ReactNode
    /** Press handler. Makes the row Pressable with a ripple. */
    onPress?: () => void
    /** Disable press feedback and dim the row. */
    disabled?: boolean
    /** Outer container style override. */
    style?: StyleProp<ViewStyle>
}

/**
 * Linear-style settings row. Title + optional description on the left, optional control/value on the right.
 * Hairline divider between adjacent rows is rendered by `Section`, not here.
 *
 * @param props See `RowProps`.
 * @returns Pressable when `onPress` provided, otherwise a static View.
 */
export const Row = ({ title, description, right, onPress, disabled, style }: RowProps) => {
    const { colors } = useTheme()
    const content = (
        <View style={[{ flexDirection: "row", alignItems: "center", paddingVertical: ROW_PADDING_Y, paddingHorizontal: SPACING.lg, opacity: disabled ? 0.5 : 1 }, style]}>
            <View style={{ flex: 1 }}>
                <Text style={[TYPE.body, { color: colors.text }]}>{title}</Text>
                {description ? <Text style={[TYPE.caption, { color: colors.textMuted, marginTop: 1 }]}>{description}</Text> : null}
            </View>
            {right ? <View style={{ marginLeft: SPACING.md }}>{right}</View> : null}
        </View>
    )
    if (onPress && !disabled) {
        return (
            <Pressable onPress={onPress} android_ripple={{ color: colors.ripple, foreground: true }}>
                {content}
            </Pressable>
        )
    }
    return content
}
