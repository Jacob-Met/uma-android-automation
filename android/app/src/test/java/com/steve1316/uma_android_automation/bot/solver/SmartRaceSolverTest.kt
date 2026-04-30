package com.steve1316.uma_android_automation.bot.solver

import com.steve1316.uma_android_automation.bot.solver.TestFixtures.epithet
import com.steve1316.uma_android_automation.bot.solver.TestFixtures.race
import com.steve1316.uma_android_automation.bot.solver.TestFixtures.state
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SmartRaceSolver end-to-end")
class SmartRaceSolverTest {
    @Test
    fun solverProducesScheduleForCurrentTurnForward() {
        val r = race("Senior Stakes", 60)
        val st = state(currentTurn = 55, races = listOf(r))

        val schedule = SmartRaceSolver.solve(st)
        assertTrue(schedule.decisions.keys.all { it in 55..72 })
    }

    @Test
    fun deadEpithetIsNotPursued() {
        // Two reachable epithets; one is dead. Solver should ignore the dead one.
        val keepKey = race("Keep Stakes", 60)
        val dropKey = race("Drop Stakes", 60)
        val keep = epithet("Keep", listOf(EpithetMatcher.WinRace("Keep Stakes")))
        val drop = epithet("Drop", listOf(EpithetMatcher.WinRace("Drop Stakes")))

        val st =
            state(
                currentTurn = 60,
                races = listOf(keepKey, dropKey),
                epithets = listOf(keep, drop),
                deadEpithets = setOf("Drop"),
            )

        val schedule = SmartRaceSolver.solve(st)
        assertTrue("Keep" in schedule.projectedEpithets)
        assertFalse("Drop" in schedule.projectedEpithets)
    }

    @Test
    fun forcedEpithetSchedulesItsRaces() {
        val unicorn = race("Unicorn Stakes", 30)
        val leopard = race("Leopard Stakes", 38)
        val derby = race("Japan Dirt Derby", 40)
        val target =
            epithet(
                "Kicking Up Dust",
                listOf(
                    EpithetMatcher.WinRace("Unicorn Stakes"),
                    EpithetMatcher.WinRace("Leopard Stakes"),
                    EpithetMatcher.WinRace("Japan Dirt Derby"),
                ),
            )

        val st =
            state(
                currentTurn = 28,
                races = listOf(unicorn, leopard, derby),
                epithets = listOf(target),
                forcedEpithets = setOf("Kicking Up Dust"),
            )

        val schedule = SmartRaceSolver.solve(st)
        assertTrue("Kicking Up Dust" in schedule.projectedEpithets)
        val races = schedule.raceTurns().map { it.second }.toSet()
        assertTrue(races.containsAll(listOf(unicorn.key, leopard.key, derby.key)))
    }

    @Test
    fun reSolveAfterLossDropsBlockedEpithet() {
        // Initial state: two reachable epithets, both feasible.
        val r1 = race("Race A", 30)
        val r2 = race("Race B", 32)
        val epA = epithet("Win A", listOf(EpithetMatcher.WinRace("Race A")))
        val epB = epithet("Win B", listOf(EpithetMatcher.WinRace("Race B")))

        val initial = state(currentTurn = 28, races = listOf(r1, r2), epithets = listOf(epA, epB))
        val firstRun = SmartRaceSolver.solve(initial)
        assertTrue("Win A" in firstRun.projectedEpithets)

        // Simulate a race-loss: epA becomes dead; turn advances; r1 is no longer in candidate
        // window for the next solve. Solver should still produce a viable schedule for epB.
        val afterLoss =
            initial.copy(
                currentTurn = 31,
                deadEpithets = setOf("Win A"),
            )
        val reRun = SmartRaceSolver.solve(afterLoss)
        assertFalse("Win A" in reRun.projectedEpithets)
        assertTrue("Win B" in reRun.projectedEpithets)
    }

    @Test
    fun deterministicAcrossRepeatedCalls() {
        val r1 = race("R1", 50, fans = 8000)
        val r2 = race("R2", 50, fans = 12000)
        val ep = epithet("E", listOf(EpithetMatcher.WinRace("R2")))
        val st = state(currentTurn = 50, races = listOf(r1, r2), epithets = listOf(ep))

        val first = SmartRaceSolver.solve(st)
        repeat(9) {
            val again = SmartRaceSolver.solve(st)
            assertEquals(first.decisions, again.decisions)
            assertEquals(first.totalScore, again.totalScore, 1e-9)
        }
    }
}
