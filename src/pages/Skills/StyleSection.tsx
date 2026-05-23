import React, { useContext, useMemo } from "react"
import { View, Text, StyleSheet } from "react-native"
import { SectionLabel } from "../../components/ui/section-label"
import InfoCallout from "../../components/ui/info-callout"
import CustomSelect from "../../components/CustomSelect"
import { SkillsContext, defaultSettings } from "../../context/BotStateContext"
import { useTheme } from "../../context/ThemeContext"
import { TYPE } from "../../lib/type"
import { SPACING } from "../../lib/spacing"

/** Options for the Running Style picker. */
const RUNNING_STYLE_OPTIONS = [
    { value: "inherit", label: "Use [Racing Settings] -> [Original Race Strategy]" },
    { value: "no_preference", label: "Any" },
    { value: "front_runner", label: "Front Runner" },
    { value: "pace_chaser", label: "Pace Chaser" },
    { value: "late_surger", label: "Late Surger" },
    { value: "end_closer", label: "End Closer" },
]

/** Options for the Track Distance picker. */
const TRACK_DISTANCE_OPTIONS = [
    { value: "inherit", label: "Use [Training Settings] -> [Preferred Distance Override]" },
    { value: "no_preference", label: "Any" },
    { value: "sprint", label: "Sprint" },
    { value: "mile", label: "Mile" },
    { value: "medium", label: "Medium" },
    { value: "long", label: "Long" },
]

/** Options for the Track Surface picker. */
const TRACK_SURFACE_OPTIONS = [
    { value: "no_preference", label: "Any" },
    { value: "turf", label: "Turf" },
    { value: "dirt", label: "Dirt" },
]

/**
 * Global Style settings. Hosts the Running Style override picker and a long-form explainer describing how the running style filter affects which skills the bot considers.
 * @returns A section with the Running Style select and a collapsible explainer callout.
 */
const StyleSection: React.FC = () => {
    const { colors } = useTheme()
    const { skills, updateSkills } = useContext(SkillsContext)
    const merged = { ...defaultSettings.skills, ...skills }
    const { preferredRunningStyle, preferredTrackDistance, preferredTrackSurface } = merged

    const styles = useMemo(
        () =>
            StyleSheet.create({
                selectWrap: { paddingHorizontal: SPACING.sm, marginBottom: SPACING.sm },
                infoBlock: { marginTop: SPACING.sm },
                infoLabel: { ...TYPE.body, color: colors.text, fontWeight: "600" },
                infoDescription: { ...TYPE.body, color: colors.text, opacity: 0.8 },
            }),
        [colors]
    )

    return (
        <>
            <SectionLabel label="Style" />
            <View style={styles.selectWrap}>
                <CustomSelect
                    searchId="skill-plan-running-style"
                    options={RUNNING_STYLE_OPTIONS}
                    value={preferredRunningStyle}
                    defaultValue={defaultSettings.skills.preferredRunningStyle}
                    onValueChange={(value) => updateSkills({ preferredRunningStyle: value } as any)}
                    label="Running Style for Skills"
                    description="Dictates which skills are considered for purchase based on the preferred running style."
                    placeholder="Select Running Style"
                />
            </View>
            <View style={styles.selectWrap}>
                <CustomSelect
                    searchId="preferred-distance-override"
                    options={TRACK_DISTANCE_OPTIONS}
                    value={preferredTrackDistance}
                    defaultValue={defaultSettings.skills.preferredTrackDistance}
                    onValueChange={(value) => updateSkills({ preferredTrackDistance: value } as any)}
                    label="Track Distance for Skills"
                    description="Dictates which skills are considered for purchase based on the track distance."
                    placeholder="Select Track Distance"
                />
            </View>
            <View style={styles.selectWrap}>
                <CustomSelect
                    searchId="preferred-track-surface"
                    options={TRACK_SURFACE_OPTIONS}
                    value={preferredTrackSurface}
                    defaultValue={defaultSettings.skills.preferredTrackSurface}
                    onValueChange={(value) => updateSkills({ preferredTrackSurface: value } as any)}
                    label="Track Surface for Skills"
                    description="Dictates which skills are considered for purchase based on the terrain."
                    placeholder="Select Track Surface"
                />
            </View>
            <InfoCallout title="How Running Style affects skill picks">
                <Text style={styles.infoLabel}>There are two different groups of Running Style skills.</Text>
                <View style={styles.infoBlock}>
                    <Text style={styles.infoDescription}>
                        The first are skills that specifically say in their description that they are for a specific running style. These cannot be activated unless the trainee is using that running
                        style.
                    </Text>
                </View>
                <View style={styles.infoBlock}>
                    <Text style={styles.infoDescription}>
                        The second are skills that do not say they are for a running style, but have activation conditions which limit which styles would actually be able to activate them (ignoring
                        rare cases).
                    </Text>
                </View>
                <View style={styles.infoBlock}>
                    <Text style={styles.infoDescription}>
                        This setting will filter skills based on both of these conditions. This helps us avoid having situations like an End Closer purchasing a skill like "Keeping the Lead". This
                        skill doesn't require using the Front Runner style to activate, but it does require the runner to be in the lead mid-race which is very unlikely for an End Closer.
                    </Text>
                </View>
                <Text style={[styles.infoLabel, { marginTop: SPACING.md }]}>Detailed breakdown of examples:</Text>
                <View style={styles.infoBlock}>
                    <Text style={styles.infoLabel}>Use [Racing Settings] {"->"} [Original Race Strategy]</Text>
                    <Text style={styles.infoDescription}>
                        - Inherits the running style from your Racing Settings. For example, if you set the Strategy to "Late Surger" in Racing Settings, only Late Surger skills will be considered.
                    </Text>
                </View>
                <View style={styles.infoBlock}>
                    <Text style={styles.infoLabel}>Any</Text>
                    <Text style={styles.infoDescription}>
                        - Does not filter any skills based on running style. For example, even if your trainee is an "End Closer", the bot may still purchase "Pace Chaser Corners O" (a Pace Chaser
                        skill) if it's available.
                    </Text>
                </View>
                <View style={styles.infoBlock}>
                    <Text style={styles.infoLabel}>Front Runner</Text>
                    <Text style={styles.infoDescription}>
                        - Only considers skills that are compatible with the Front Runner style. For example, skills like "Escape Artist" will be included, while "Outer Swell" (Late Surger) will be
                        ignored.
                    </Text>
                </View>
            </InfoCallout>
        </>
    )
}

export default React.memo(StyleSection)
