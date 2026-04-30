package com.steve1316.uma_android_automation.bot.solver

import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.RaceGrade

/**
 * Pure scoring helpers consumed by the heuristic. Each function takes the minimum required
 * inputs and returns an additive contribution to the beam's objective; the heuristic sums them.
 *
 * Race value is grade-and-fans-driven; epithet contribution is reward-magnitude-driven; penalties
 * (summer block, 3-consecutive-race conditioning) subtract. Aptitude eligibility is a hard filter
 * applied upstream — see [isEligible].
 */
object ScoringFunctions {
    /**
     * Base stat reward per race grade, mirroring the reference Trackblazer solver's
     * `BASE_REWARD` table. Grades not in the table (Maiden, Debut, Finale, EX) score zero
     * gross reward, leaving them well below cost so the solver always trains over them.
     */
    private fun baseStat(grade: RaceGrade): Double =
        when (grade) {
            RaceGrade.G1 -> 10.0
            RaceGrade.G2, RaceGrade.G3 -> 8.0
            RaceGrade.OP -> 5.0
            RaceGrade.PRE_OP -> 5.0
            else -> 0.0
        }

    /** Base skill-point reward per race grade. See [baseStat]. */
    private fun baseSp(grade: RaceGrade): Double =
        when (grade) {
            RaceGrade.G1 -> 35.0
            RaceGrade.G2, RaceGrade.G3 -> 25.0
            RaceGrade.OP -> 15.0
            RaceGrade.PRE_OP -> 10.0
            else -> 0.0
        }

    /**
     * Baseline used to scale [Weights.raceCostPct] into a concrete subtracted cost. Graded races
     * (G1/G2/G3) compare against the G2 baseline — matching the reference solver's `g2g3Baseline`
     * — so G1 races net positive and G2/G3 net zero. OP/Pre-OP races compare against their own
     * grade's baseline so they net zero by default instead of strongly negative; without this,
     * weak presets like Haru Urara whose only eligible races are OP/Pre-OP would never schedule
     * any race even with [Weights.includeOpAndPreOp] enabled, since cost-vs-G2 dominates the
     * tiny OP reward. Epithet contributions can still tip OP races positive.
     *
     * With default weights:
     *  - G2 baseline (used by G1/G2/G3): `1*12 + 1*37 = 49`
     *  - OP baseline:    `1*7 + 1*22 = 29`
     *  - Pre-OP baseline:`1*7 + 1*15 = 22`
     */
    private fun costBaseline(grade: RaceGrade, weights: Weights): Double {
        val rb = weights.raceBonusPct.coerceAtLeast(0.0) / 100.0
        val baselineGrade =
            when (grade) {
                RaceGrade.OP -> RaceGrade.OP
                RaceGrade.PRE_OP -> RaceGrade.PRE_OP
                else -> RaceGrade.G2
            }
        val stat = Math.floor(baseStat(baselineGrade) * (1.0 + rb))
        val sp = Math.floor(baseSp(baselineGrade) * (1.0 + rb))
        return weights.statWeight * stat + weights.spWeight * sp
    }

    /** Sub-unit tiebreaker so equal-value races prefer the larger event without ever matching or
     *  exceeding the smallest meaningful score increment of 1.0. With max fans ≈ 30k and ε = 1e-6
     *  the contribution caps at ~0.03 — well below the 1.0 anti-race bias on Train. */
    private const val FANS_EPSILON: Double = 1e-6

    /**
     * Returns the value of running a single race, ignoring epithet contributions.
     *
     * Direct port of the reference Trackblazer solver's `weightedRaceValue`:
     *   gross = statWeight * floor(baseStat * (1 + raceBonus)) + spWeight * floor(baseSp * (1 + raceBonus))
     *   cost  = (raceCostPct / 100) * costBaseline(grade)
     *   value = (gross - cost) * raceValue + fans * FANS_EPSILON
     *
     * With defaults (raceBonus 50, raceCost 100) G2/G3 net to zero gross-cost — they tie with Train
     * (which carries a tiny positive anti-race bias in [trainValue]) and are skipped unless an
     * epithet pushes them positive. Maiden/Debut/Finale/EX have zero gross reward so they always
     * score well below Train. Fans participate only as a microscopic tiebreaker between two races
     * of identical grade on the same turn.
     */
    fun raceValue(race: RaceCandidate, weights: Weights): Double {
        val rb = weights.raceBonusPct.coerceAtLeast(0.0) / 100.0
        val stat = Math.floor(baseStat(race.grade) * (1.0 + rb))
        val sp = Math.floor(baseSp(race.grade) * (1.0 + rb))
        val gross = weights.statWeight * stat + weights.spWeight * sp
        val cost = weights.raceCostPct / 100.0 * costBaseline(race.grade, weights)
        return (gross - cost) * weights.raceValue + race.fans * FANS_EPSILON
    }

    /**
     * Training is the default action. Returns a tiny positive value (`1.0`, larger than the largest
     * possible [FANS_EPSILON] contribution from the most popular race) so that whenever a race's
     * gross reward equals its cost, Train wins the tie. The reference solver achieves the same
     * effect via GLPK's MILP picking `NO_RACE` on ties.
     */
    fun trainValue(
        @Suppress("UNUSED_PARAMETER") weights: Weights,
    ): Double = 1.0

    /** Resting yields no scoring contribution; energy is not modelled in the static preview. */
    fun restValue(
        @Suppress("UNUSED_PARAMETER") weights: Weights,
    ): Double = 0.0

    /**
     * Reward magnitude of completing [epithet]. Stat rewards return [Epithet.amount]; hint
     * rewards return [Weights.hintWeight]; unknown rewards return zero.
     */
    fun epithetContribution(epithet: Epithet, weights: Weights): Double {
        val base =
            when (epithet.rewardKind) {
                "stat" -> epithet.amount.toDouble()
                "hint" -> weights.hintWeight
                else -> 0.0
            }
        return base * weights.epithetValue
    }

    /**
     * Turns where landing the third+ consecutive race incurs zero conditioning penalty. These
     * are the Late-December halves at the end of each class year — Junior Dec-2 (turn 23),
     * Classic Dec-2 (47), and Senior Dec-2 (71). Mirrors the reference solver's `LATE_DEC_WINDOWS`.
     */
    private val LATE_DEC_FREE_TURNS: Set<TurnNumber> = setOf(23, 47, 71)

    /**
     * Penalty applied when scheduling a third (or later) consecutive race. The reference
     * solver penalises the *start* of a 3-race chain; we apply it on every additional race
     * past the second to keep beams deterministic and incremental. Returns zero on Late-Dec
     * windows (turns 23, 47, 71) to match the reference's end-of-year exemption.
     *
     * @param consecutiveRaceCount Number of consecutive races including the current one.
     * @param turn Turn the third+ race lands on; checked against [LATE_DEC_FREE_TURNS].
     */
    fun consecutiveRacePenalty(
        consecutiveRaceCount: Int,
        turn: TurnNumber,
        weights: Weights,
    ): Double {
        if (consecutiveRaceCount < 3) return 0.0
        if (turn in LATE_DEC_FREE_TURNS) return 0.0
        return weights.consecutiveRacePenalty
    }

    /** Penalty for racing on a turn flagged as a summer training block. */
    fun summerBlockPenalty(turn: TurnNumber, state: SolverState): Double =
        if (turn in state.summerBlockTurns) state.weights.summerPenalty else 0.0

    /** Grades excluded by default unless [Weights.includeOpAndPreOp] is true. Mirrors the
     *  reference Trackblazer site's `OP_GRADES` set + `include_op` toggle (default false). */
    private val OP_GRADES: Set<RaceGrade> = setOf(RaceGrade.OP, RaceGrade.PRE_OP)

    /**
     * Hard eligibility check: a race is eligible only if both the matching distance aptitude
     * and surface aptitude meet [Weights.aptitudeThreshold]. Below threshold, the race is
     * dropped from the candidate set entirely.
     */
    fun isEligible(race: RaceCandidate, state: SolverState): Boolean {
        if (race.grade in OP_GRADES && !state.weights.includeOpAndPreOp) return false
        val distApt = state.aptitudes.forDistance(race.distanceType)
        val surfApt = state.aptitudes.forSurface(race.terrain)
        val threshold = state.weights.aptitudeThreshold
        return distApt.atLeast(threshold) && surfApt.atLeast(threshold)
    }

    /** True if [this] aptitude is at least as good as [other]. Higher ordinal = better grade. */
    private fun Aptitude.atLeast(other: Aptitude): Boolean = this.ordinal >= other.ordinal
}
