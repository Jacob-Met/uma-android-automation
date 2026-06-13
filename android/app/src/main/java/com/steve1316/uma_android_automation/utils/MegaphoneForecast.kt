package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.types.RaceGrade

/**
 * Race-forecast megaphone window simulation: counts effective race days in a megaphone duration
 * window using agenda calendar data, irregular viability, and simulated energy/charm inventory (over reserves only).
 */
object MegaphoneForecast {
    const val COACHING_DURATION = 4
    const val MOTIVATING_DURATION = 3
    const val EMPOWERING_DURATION = 2

    data class Config(
        val irregularAware: Boolean,
        val enableIrregularForecastEnergy: Boolean,
        val enableWitIrregularTraining: Boolean,
        val todayFailureBlockThreshold: Int,
        val summerCharmOverrideMinStatGain: Int,
        val charmUsedOnToday: Boolean,
        val energyUsedOnToday: Boolean,
        val todayFailurePercent: Int,
        val todayTrainingIsWit: Boolean,
        val classicPlus: Boolean,
        val irregularEnabled: Boolean,
        val irregularAgendaReady: Boolean,
    )

    data class TurnContext(
        val turnNumber: Int,
        val hasAgendaRace: Boolean,
        val raceGrade: RaceGrade?,
        val irregularMinGain: Int,
        val estimatedFailurePercent: Int,
        val estimatedMainGain: Int,
        val witTurn: Boolean,
    )

    /** Mutable energy/charm pool for forward simulation (over-reserve units only). */
    class SimulatedInventory(
        var charmAboveReserve: Int,
        var kaleAboveReserve: Int,
        var vita65AboveReserve: Int,
        var vita40AboveReserve: Int,
        var vita20AboveReserve: Int,
        val charmAtReserveOnly: Int,
        val charmTotal: Int,
    ) {
        fun copyPool(): SimulatedInventory =
            SimulatedInventory(
                charmAboveReserve = charmAboveReserve,
                kaleAboveReserve = kaleAboveReserve,
                vita65AboveReserve = vita65AboveReserve,
                vita40AboveReserve = vita40AboveReserve,
                vita20AboveReserve = vita20AboveReserve,
                charmAtReserveOnly = charmAtReserveOnly,
                charmTotal = charmTotal,
            )

        fun hasPlus100Equivalent(): Boolean = kaleAboveReserve > 0 || vita65AboveReserve > 0 || canSpendPlus65Combo()

        fun hasPlus40Equivalent(): Boolean = vita40AboveReserve > 0 || vita20AboveReserve >= 2 || hasPlus100Equivalent()

        fun canSpendPlus65Combo(): Boolean =
            vita65AboveReserve > 0 ||
                (vita40AboveReserve > 0 && vita20AboveReserve > 0) ||
                vita20AboveReserve >= 3

        fun spendPlus100Equivalent(): Boolean {
            if (kaleAboveReserve > 0) {
                kaleAboveReserve--
                return true
            }
            if (vita65AboveReserve > 0) {
                vita65AboveReserve--
                return true
            }
            return spendPlus65Combo()
        }

        fun spendPlus40Equivalent(): Boolean {
            if (hasPlus100Equivalent() && spendPlus100Equivalent()) return true
            if (vita40AboveReserve > 0) {
                vita40AboveReserve--
                return true
            }
            if (vita20AboveReserve >= 2) {
                vita20AboveReserve -= 2
                return true
            }
            return false
        }

        private fun spendPlus65Combo(): Boolean {
            if (vita65AboveReserve > 0) {
                vita65AboveReserve--
                return true
            }
            if (vita40AboveReserve > 0 && vita20AboveReserve > 0) {
                vita40AboveReserve--
                vita20AboveReserve--
                return true
            }
            if (vita20AboveReserve >= 3) {
                vita20AboveReserve -= 3
                return true
            }
            return false
        }
    }

    fun megaphoneDuration(itemName: String): Int =
        when (itemName) {
            "Coaching Megaphone" -> COACHING_DURATION
            "Motivating Megaphone" -> MOTIVATING_DURATION
            "Empowering Megaphone" -> EMPOWERING_DURATION
            else -> 0
        }

    fun effectiveRaceCountInWindow(
        currentTurn: Int,
        duration: Int,
        turnContexts: Map<Int, TurnContext>,
        config: Config,
        inventory: SimulatedInventory,
        maxTrainingFailureThreshold: Int,
    ): Int {
        val simInventory = inventory.copyPool()
        var count = 0

        for (offset in 1 until duration) {
            val turn = currentTurn + offset
            val ctx = turnContexts[turn] ?: continue
            if (!ctx.hasAgendaRace) {
                continue
            }

            val weight =
                effectiveRaceWeightForTurn(
                    ctx = ctx,
                    config = config,
                    inventory = simInventory,
                    isTomorrow = turn == currentTurn + 1,
                    todayFailureBlocksChain =
                        !config.energyUsedOnToday &&
                            todayFailureBlocksNextTrain(config, maxTrainingFailureThreshold),
                    maxTrainingFailureThreshold = maxTrainingFailureThreshold,
                )

            if (weight >= 1) {
                count++
            } else if (ctx.estimatedFailurePercent > maxTrainingFailureThreshold && config.enableIrregularForecastEnergy) {
                if (ctx.estimatedFailurePercent > maxTrainingFailureThreshold + 10) {
                    simInventory.spendPlus100Equivalent() || simInventory.spendPlus40Equivalent()
                } else {
                    simInventory.spendPlus40Equivalent() || simInventory.spendPlus100Equivalent()
                }
            }
        }
        return count
    }

    private fun todayFailureBlocksNextTrain(config: Config, maxTrainingFailureThreshold: Int): Boolean {
        val threshold =
            if (config.todayTrainingIsWit && config.enableWitIrregularTraining) {
                0
            } else {
                config.todayFailureBlockThreshold
            }
        return config.todayFailurePercent > threshold
    }

    internal fun effectiveRaceWeightForTurn(
        ctx: TurnContext,
        config: Config,
        inventory: SimulatedInventory,
        isTomorrow: Boolean,
        todayFailureBlocksChain: Boolean,
        maxTrainingFailureThreshold: Int,
    ): Int {
        if (!ctx.hasAgendaRace) {
            return 0
        }
        if (config.charmUsedOnToday && isTomorrow) {
            return 1
        }
        if (!config.irregularAware || !config.irregularEnabled || !config.irregularAgendaReady || !config.classicPlus) {
            return 1
        }
        if (todayFailureBlocksChain && isTomorrow) {
            return 1
        }
        if (config.enableWitIrregularTraining && ctx.witTurn && isTomorrow) {
            return 0
        }
        if (ctx.estimatedMainGain < ctx.irregularMinGain) {
            return 1
        }
        if (ctx.estimatedFailurePercent <= maxTrainingFailureThreshold) {
            return 0
        }
        if (charmMitigationAvailable(ctx, config, inventory)) {
            return 0
        }
        if (config.enableIrregularForecastEnergy) {
            if (inventory.hasPlus40Equivalent() || inventory.hasPlus100Equivalent()) {
                return 0
            }
        }
        return 1
    }

    /**
     * Charms above reserve allow irregular mitigation. No charms at all → race day (1).
     * Only reserved charms (none above reserve) → still 0 when gain meets pool-override threshold.
     */
    private fun charmMitigationAvailable(
        ctx: TurnContext,
        config: Config,
        inventory: SimulatedInventory,
    ): Boolean {
        if (inventory.charmTotal <= 0) {
            return false
        }
        if (inventory.charmAboveReserve > 0) {
            return true
        }
        return config.summerCharmOverrideMinStatGain > 0 &&
            ctx.estimatedMainGain >= config.summerCharmOverrideMinStatGain &&
            inventory.charmAtReserveOnly > 0
    }

    fun buildTurnContextsFromSchedule(
        currentTurn: Int,
        duration: Int,
        scheduledTurns: Map<Int, String>,
        gradeForTurn: (Int, String) -> RaceGrade?,
        minGainForTurn: (Int) -> Int,
        failureForTurn: (Int) -> Int,
        mainGainForTurn: (Int) -> Int,
        witForTurn: (Int) -> Boolean,
    ): Map<Int, TurnContext> {
        val result = mutableMapOf<Int, TurnContext>()
        for (offset in 0 until duration) {
            val turn = currentTurn + offset
            val raceKey = scheduledTurns[turn]
            result[turn] =
                TurnContext(
                    turnNumber = turn,
                    hasAgendaRace = raceKey != null,
                    raceGrade = raceKey?.let { gradeForTurn(turn, it) },
                    irregularMinGain = minGainForTurn(turn),
                    estimatedFailurePercent = failureForTurn(turn),
                    estimatedMainGain = mainGainForTurn(turn),
                    witTurn = witForTurn(turn),
                )
        }
        return result
    }
}
