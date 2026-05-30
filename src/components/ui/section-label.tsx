import { Text, type TextStyle, type StyleProp, type ViewStyle, View } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { TYPE } from "../../lib/type"
import { SPACING } from "../../lib/spacing"

/** Props for `SectionLabel`. */
export interface SectionLabelProps {
    /** Visible label text (rendered uppercase via `TYPE.monoLabel`). */
    label: string
    /** Optional right slot rendered inline with the label (used by `Section` for the collapse chevron). */
    right?: React.ReactNode
    /** Optional override style on the outer container. */
    style?: StyleProp<ViewStyle>
}

/**
 * Uppercase Geist Mono label used above each grouped section. Pairs visually with the warm-stone bg + brand accents.
 *
 * @param props See `SectionLabelProps`.
 * @returns Inline label row with optional right slot.
 */
export const SectionLabel = ({ label, right, style }: SectionLabelProps) => {
    const { colors } = useTheme()
    const textStyle: TextStyle = { ...TYPE.monoLabel, color: colors.textMuted }
    return (
        <View style={[{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: SPACING.sm, paddingHorizontal: SPACING.xs }, style]}>
            <Text style={textStyle}>{label}</Text>
            {right}
        </View>
    )
}
