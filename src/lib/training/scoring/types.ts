// src/lib/training/scoring/types.ts

export enum StatName {
    SPEED = "SPEED",
    STAMINA = "STAMINA",
    POWER = "POWER",
    GUTS = "GUTS",
    WIT = "WIT",
}

export const ALL_STAT_NAMES: readonly StatName[] = [
    StatName.SPEED,
    StatName.STAMINA,
    StatName.POWER,
    StatName.GUTS,
    StatName.WIT,
]

export enum DateYear {
    PRE_DEBUT = "PRE_DEBUT",
    JUNIOR = "JUNIOR",
    CLASSIC = "CLASSIC",
    SENIOR = "SENIOR",
}

export interface GameDate {
    year: DateYear
    day: number
    bIsPreDebut: boolean
    isSummer: boolean
}

export interface BarFillResult {
    dominantColor: string
    fillPercent: number
    isTrainerSupport: boolean
}

export interface TrainingOption {
    name: StatName
    statGains: Partial<Record<StatName, number>>
    failureChance: number
    relationshipBars: BarFillResult[]
    numRainbow: number
    numSkillHints: number
    trainingLevel: number | null
}

export interface TrainingScoringConstants {
    ratioBreakpoints: number[]
    ratioValues: number[]
    priorityCoefficient: number
    levelBoostRank1Factor: number
    levelBoostRank2Factor: number
    levelBoostRank3Factor: number
    mainStatThresholds: Record<StatName, number>
    mainStatBonusMagnitude: number
    relationshipOrangeValue: number
    relationshipGreenValue: number
    relationshipBlueValue: number
    relationshipDiminishingFactor: number
    relationshipEarlyGameBonus: number
    relationshipTrainerSupportBonus: number
    skillHintPerHintScore: number
    skillHintOverrideScore: number
    statWeightWithBars: number
    statWeightWithoutBars: number
    relationshipWeightWithBars: number
    miscWeight: number
    juniorEarlyGameFlatBonus: number
    relationshipScale: number
    rainbowMultiplierEnabled: number
    rainbowMultiplierDisabled: number
    rainbowPerInstanceBase: number
    rainbowPerInstanceDecay: number
    anticipatoryMinFillPercent: number
    anticipatoryCoefficient: number
    anticipatoryCap: number
}

export const DEFAULT_TRAINING_SCORING_CONSTANTS: TrainingScoringConstants = {
    ratioBreakpoints: [30, 50, 70, 90, 110, 130],
    ratioValues: [5, 4, 3, 2, 1, 0.5, 0.3],
    priorityCoefficient: 0.5,
    levelBoostRank1Factor: 0.75,
    levelBoostRank2Factor: 0.25,
    levelBoostRank3Factor: 0.1,
    mainStatThresholds: {
        [StatName.SPEED]: 30,
        [StatName.STAMINA]: 30,
        [StatName.POWER]: 30,
        [StatName.GUTS]: 30,
        [StatName.WIT]: 15,
    },
    mainStatBonusMagnitude: 2,
    relationshipOrangeValue: 0,
    relationshipGreenValue: 1,
    relationshipBlueValue: 2.5,
    relationshipDiminishingFactor: 0.5,
    relationshipEarlyGameBonus: 1.3,
    relationshipTrainerSupportBonus: 1.15,
    skillHintPerHintScore: 10,
    skillHintOverrideScore: 10000,
    statWeightWithBars: 0.6,
    statWeightWithoutBars: 0.7,
    relationshipWeightWithBars: 0.1,
    miscWeight: 0.3,
    juniorEarlyGameFlatBonus: 200,
    relationshipScale: 1.5,
    rainbowMultiplierEnabled: 2,
    rainbowMultiplierDisabled: 1.5,
    rainbowPerInstanceBase: 200,
    rainbowPerInstanceDecay: 0.5,
    anticipatoryMinFillPercent: 10,
    anticipatoryCoefficient: 0.2,
    anticipatoryCap: 0.6,
}

export interface TrainingConfig {
    currentStats: Partial<Record<StatName, number>>
    statPrioritization: StatName[]
    summerTrainingStatPriority: StatName[]
    statTargets: Partial<Record<StatName, number>>
    currentDate: GameDate
    scenario: string
    enableRainbowTrainingBonus: boolean
    blacklist: (StatName | null)[]
    disableTrainingOnMaxedStat: boolean
    trainingOptions: TrainingOption[]
    skillHintsPerLocation: Partial<Record<StatName, number>>
    enablePrioritizeSkillHints: boolean
    enableTrainingLevelWeighting: boolean
    disableStatTargets: boolean
    enablePrioritizeNearMaxFriendship: boolean
    statsTrainedOverBuffer: Set<StatName>
    scoring: TrainingScoringConstants
}
