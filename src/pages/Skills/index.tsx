import React, { useState, useCallback, useMemo } from "react"
import { View, Text, ScrollView, StyleSheet } from "react-native"
import PageHeader from "../../components/PageHeader"
import { SectionLabel } from "../../components/ui/section-label"
import InfoCallout from "../../components/ui/info-callout"
import TabStrip, { TabStripItem } from "../../components/ui/tab-strip"
import { useTheme } from "../../context/ThemeContext"
import { TYPE } from "../../lib/type"
import { SPACING } from "../../lib/spacing"
import { skillPlanSettingsPages } from "../SkillPlanSettings/config"
import PlanTab from "./PlanTab"
import StyleSection from "./StyleSection"

/** Ordered list of plan tabs. Keys match `skillPlanSettingsPages` plan keys. */
const TAB_ITEMS: TabStripItem[] = [
    { key: "skillPointCheck", label: skillPlanSettingsPages.skillPointCheck.title },
    { key: "preFinals", label: skillPlanSettingsPages.preFinals.title },
    { key: "careerComplete", label: skillPlanSettingsPages.careerComplete.title },
]

/** Optional route params for deep-linking to a specific plan tab. */
interface SkillsRouteParams {
    /** Initial plan tab key. Falls back to `skillPointCheck` if missing or invalid. */
    tab?: string
}

/**
 * Consolidated Skills page. Hosts global Style settings at the top, a tab strip for the three skill plans, and the active plan's content below.
 * @param route Optional navigation route carrying initial tab params.
 * @returns A scrollable Skills page with three tabs.
 */
const Skills: React.FC<{ route?: { params?: SkillsRouteParams } }> = ({ route }) => {
    const { colors } = useTheme()
    const initialTab = route?.params?.tab && TAB_ITEMS.some((t) => t.key === route.params!.tab) ? route.params!.tab! : "skillPointCheck"
    const [activeKey, setActiveKey] = useState<string>(initialTab)
    const onChange = useCallback((key: string) => setActiveKey(key), [])

    const styles = useMemo(
        () =>
            StyleSheet.create({
                container: { flex: 1, backgroundColor: colors.background },
                scroll: { padding: SPACING.md, gap: SPACING.sm },
                intro: { ...TYPE.body, color: colors.text, marginBottom: SPACING.sm },
            }),
        [colors]
    )

    return (
        <View style={styles.container}>
            <PageHeader title="Skills" />
            <ScrollView contentContainerStyle={styles.scroll}>
                <InfoCallout title="How skill spending works">
                    <Text style={styles.intro}>Allows configuration of automated skill point spending.</Text>
                    <Text style={[styles.intro, { marginBottom: 0 }]}>
                        This feature is not made of magic. If you wish to train an uma up for TT or CM, then you should buy your skills manually. The main purpose of this feature is to make the
                        process of farming rank in events less of a hassle.
                    </Text>
                </InfoCallout>
                <StyleSection />
                <SectionLabel label="Skill Plans" />
                <TabStrip items={TAB_ITEMS} activeKey={activeKey} onChange={onChange} />
                <PlanTab planKey={activeKey} />
            </ScrollView>
        </View>
    )
}

export default Skills
