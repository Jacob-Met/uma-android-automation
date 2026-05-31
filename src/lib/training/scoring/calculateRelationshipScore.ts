import { DateYear, TrainingConfig, TrainingOption } from "./types"

/**
 * Score relationship bars with diminishing returns by fill level, an early-game bonus, and a trainer-support bonus. Ports `calculateRelationshipScore` in `Training.kt`.
 *
 * @param config Global scoring inputs.
 * @param training The training option to score.
 * @returns Normalized score in [0, 100].
 */
export function calculateRelationshipScore(config: TrainingConfig, training: TrainingOption): number {
    if (training.relationshipBars.length === 0) return 0

    let score = 0
    let maxScore = 0

    for (const bar of training.relationshipBars) {
        let baseValue = 0
        if (bar.dominantColor === "orange") baseValue = config.scoring.relationshipOrangeValue
        else if (bar.dominantColor === "green") baseValue = config.scoring.relationshipGreenValue
        else if (bar.dominantColor === "blue") baseValue = config.scoring.relationshipBlueValue

        if (baseValue > 0) {
            const fillLevel = bar.fillPercent / 100.0
            const diminishingFactor = 1.0 - fillLevel * config.scoring.relationshipDiminishingFactor
            const earlyGameBonus = config.currentDate.year === DateYear.JUNIOR || config.currentDate.bIsPreDebut ? config.scoring.relationshipEarlyGameBonus : 1.0
            const trainerSupportBonus = bar.isTrainerSupport ? config.scoring.relationshipTrainerSupportBonus : 1.0
            score += baseValue * diminishingFactor * earlyGameBonus * trainerSupportBonus
            maxScore += config.scoring.relationshipBlueValue * config.scoring.relationshipEarlyGameBonus
        }
    }

    return maxScore > 0 ? (score / maxScore) * 100.0 : 0
}
