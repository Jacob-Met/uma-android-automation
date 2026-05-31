import React, { useMemo } from "react"
import { Pressable, StyleSheet, View } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import { ALL_STAT_NAMES, StatName } from "../../lib/training/scoring"
import { SPACING } from "../../lib/spacing"
import { TYPE } from "../../lib/type"
import { Text } from "../ui/text"
import { SandboxScenario, ScenarioAction } from "./scenarioState"

const STAT_LABELS: Record<StatName, string> = {
    [StatName.SPEED]: "Speed",
    [StatName.STAMINA]: "Stamina",
    [StatName.POWER]: "Power",
    [StatName.GUTS]: "Guts",
    [StatName.WIT]: "Wit",
}

const TIER_COLORS: Record<"blue" | "green" | "orange", string> = {
    blue: "#1d4ed8",
    green: "#15803d",
    orange: "#c2410c",
}

const AMBER = "#f59e0b"

/** Props for `TrainingCircleRow`. */
export interface TrainingCircleRowProps {
    /** Current sandbox scenario state. */
    scenario: SandboxScenario
    /** Map of computed raw scores keyed by training stat. */
    scoresByTraining: Record<StatName, number>
    /** Training currently winning (highest score). Gets the amber border + WIN tag. */
    winnerTraining: StatName
    /** Reducer dispatch used to mutate the scenario. */
    dispatch: React.Dispatch<ScenarioAction>
}

/**
 * Horizontal row of 5 training "circles" mirroring the in-game training picker. Pressing a circle selects that training in the editor
 * strip. The selected circle grows and gains an amber border; the winning training is tagged WIN with amber text. Friendship-bar tier
 * dots and a rainbow indicator surface enough of the underlying scenario to spot why a training is winning at a glance.
 *
 * @param props See `TrainingCircleRowProps`.
 * @returns A flex row of 5 pressable training circles plus their score readouts.
 */
export function TrainingCircleRow({ scenario, scoresByTraining, winnerTraining, dispatch }: TrainingCircleRowProps): React.ReactElement {
    const { colors } = useTheme()
    const styles = useMemo(
        () =>
            StyleSheet.create({
                root: {
                    flexDirection: "row",
                    justifyContent: "space-between",
                    alignItems: "flex-start",
                    paddingVertical: SPACING.sm,
                    gap: 6,
                },
                col: {
                    flex: 1,
                    alignItems: "center",
                    gap: 4,
                },
                circle: {
                    width: 60,
                    height: 60,
                    borderRadius: 999,
                    backgroundColor: colors.surfaceRaised,
                    borderWidth: 1.5,
                    borderColor: colors.borderStrong,
                    alignItems: "center",
                    justifyContent: "center",
                    position: "relative",
                },
                circleSelected: {
                    width: 72,
                    height: 72,
                    borderColor: AMBER,
                    borderWidth: 2,
                },
                circleLabel: {
                    ...TYPE.caption,
                    color: colors.text,
                    fontWeight: "700",
                    fontSize: 11,
                },
                circleLv: {
                    ...TYPE.caption,
                    color: colors.textMuted,
                    fontSize: 10,
                },
                rainbowDot: {
                    position: "absolute",
                    top: 4,
                    left: 4,
                    width: 8,
                    height: 8,
                    borderRadius: 999,
                    backgroundColor: AMBER,
                },
                tierRow: {
                    position: "absolute",
                    bottom: 4,
                    flexDirection: "row",
                    gap: 3,
                },
                tierDot: {
                    width: 6,
                    height: 6,
                    borderRadius: 999,
                },
                scoreText: {
                    ...TYPE.caption,
                    color: colors.text,
                    fontWeight: "700",
                },
                scoreWin: {
                    color: AMBER,
                },
                winTag: {
                    ...TYPE.caption,
                    fontSize: 10,
                    fontWeight: "700",
                    color: AMBER,
                },
            }),
        [colors]
    )

    return (
        <View style={styles.root}>
            {ALL_STAT_NAMES.map((stat) => {
                const t = scenario.trainings[stat]
                const isSelected = stat === scenario.selectedTraining
                const isWinner = stat === winnerTraining
                const score = scoresByTraining[stat] ?? 0
                return (
                    <View key={stat} style={styles.col}>
                        <Pressable onPress={() => dispatch({ type: "select-training", training: stat })} style={[styles.circle, isSelected && styles.circleSelected]}>
                            {t.rainbow ? <View style={styles.rainbowDot} /> : null}
                            <Text style={styles.circleLabel}>{STAT_LABELS[stat]}</Text>
                            <Text style={styles.circleLv}>Lv {t.trainingLevel}</Text>
                            <View style={styles.tierRow}>
                                {(["blue", "green", "orange"] as const).map((tier) =>
                                    t.friendBars[tier] > 0 ? <View key={tier} style={[styles.tierDot, { backgroundColor: TIER_COLORS[tier] }]} /> : null
                                )}
                            </View>
                        </Pressable>
                        <Text style={[styles.scoreText, isWinner && styles.scoreWin]}>{score.toFixed(1)}</Text>
                        {isWinner ? <Text style={styles.winTag}>WIN</Text> : null}
                    </View>
                )
            })}
        </View>
    )
}

export default TrainingCircleRow
