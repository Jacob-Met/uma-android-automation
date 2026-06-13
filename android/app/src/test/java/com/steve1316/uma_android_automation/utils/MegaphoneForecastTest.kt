package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.types.RaceGrade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Megaphone forecast window simulation")
class MegaphoneForecastTest {
    private val maxFail = 30

    private fun baseConfig() =
        MegaphoneForecast.Config(
            irregularAware = true,
            enableIrregularForecastEnergy = true,
            enableWitIrregularTraining = false,
            todayFailureBlockThreshold = 10,
            summerCharmOverrideMinStatGain = 30,
            charmUsedOnToday = false,
            energyUsedOnToday = false,
            todayFailurePercent = 5,
            todayTrainingIsWit = false,
            classicPlus = true,
            irregularEnabled = true,
            irregularAgendaReady = true,
        )

    private fun turn(
        turn: Int,
        hasRace: Boolean,
        failure: Int = 15,
        gain: Int = 35,
        minGain: Int = 30,
        wit: Boolean = false,
    ) = turn to
        MegaphoneForecast.TurnContext(
            turnNumber = turn,
            hasAgendaRace = hasRace,
            raceGrade = if (hasRace) RaceGrade.G2 else null,
            irregularMinGain = minGain,
            estimatedFailurePercent = failure,
            estimatedMainGain = gain,
            witTurn = wit,
        )

    @Test
    @DisplayName("Empty window counts zero races")
    fun emptyWindow() {
        val contexts = mapOf(10 to turn(10, false).second, 11 to turn(11, false).second, 12 to turn(12, false).second)
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.MOTIVATING_DURATION,
                turnContexts = contexts,
                config = baseConfig(),
                inventory = pool(charmAbove = 1),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(0, count)
    }

    @Test
    @DisplayName("Race tomorrow with charm available counts as train day")
    fun raceTomorrowCharmAboveReserve() {
        val contexts = mapOf(10 to turn(10, false).second, 11 to turn(11, true).second, 12 to turn(12, false).second)
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.MOTIVATING_DURATION,
                turnContexts = contexts,
                config = baseConfig(),
                inventory = pool(charmAbove = 1),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(0, count)
    }

    @Test
    @DisplayName("No charms at all counts race tomorrow")
    fun noCharmsRaceTomorrow() {
        val contexts = mapOf(10 to turn(10, false).second, 11 to turn(11, true, failure = 50).second)
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.EMPOWERING_DURATION,
                turnContexts = contexts,
                config = baseConfig(),
                inventory = pool(charmAbove = 0, charmTotal = 0),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(1, count)
    }

    @Test
    @DisplayName("Charm used today forces tomorrow as race day")
    fun charmTodayForcesRaceTomorrow() {
        val contexts = mapOf(10 to turn(10, false).second, 11 to turn(11, true).second)
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.EMPOWERING_DURATION,
                turnContexts = contexts,
                config = baseConfig().copy(charmUsedOnToday = true),
                inventory = pool(charmAbove = 5),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(1, count)
    }

    @Test
    @DisplayName("Wit irregular tomorrow counts as train when enabled")
    fun witIrregularTomorrow() {
        val contexts = mapOf(10 to turn(10, false).second, 11 to turn(11, true, wit = true).second)
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.EMPOWERING_DURATION,
                turnContexts = contexts,
                config = baseConfig().copy(enableWitIrregularTraining = true),
                inventory = pool(),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(0, count)
    }

    @Test
    @DisplayName("High failure today without energy blocks irregular tomorrow")
    fun todayFailureBlocksTomorrow() {
        val contexts = mapOf(10 to turn(10, false).second, 11 to turn(11, true, failure = 50).second)
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.EMPOWERING_DURATION,
                turnContexts = contexts,
                config = baseConfig().copy(todayFailurePercent = 15),
                inventory = pool(vita65 = 1),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(1, count)
    }

    @Test
    @DisplayName("Energy combo over reserve enables irregular chain")
    fun energyComboEnablesChain() {
        val contexts =
            mapOf(
                10 to turn(10, false).second,
                11 to turn(11, true, failure = 50).second,
                12 to turn(12, true, failure = 50).second,
            )
        val count =
            MegaphoneForecast.effectiveRaceCountInWindow(
                currentTurn = 10,
                duration = MegaphoneForecast.MOTIVATING_DURATION,
                turnContexts = contexts,
                config = baseConfig(),
                inventory = pool(vita65 = 1, vita20 = 3),
                maxTrainingFailureThreshold = maxFail,
            )
        assertEquals(0, count)
    }

    private fun pool(
        charmAbove: Int = 0,
        charmTotal: Int = charmAbove,
        kale: Int = 0,
        vita65: Int = 0,
        vita40: Int = 0,
        vita20: Int = 0,
    ): MegaphoneForecast.SimulatedInventory =
        MegaphoneForecast.SimulatedInventory(
            charmAboveReserve = charmAbove,
            kaleAboveReserve = kale,
            vita65AboveReserve = vita65,
            vita40AboveReserve = vita40,
            vita20AboveReserve = vita20,
            charmAtReserveOnly = (charmTotal - charmAbove).coerceAtLeast(0),
            charmTotal = charmTotal,
        )
}
