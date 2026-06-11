package com.steve1316.uma_android_automation.bot.campaigns

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trackblazer megaphone surplus burn")
class TrackblazerMegaphoneTest {
    private val reserveOrder = listOf("Empowering Megaphone", "Motivating Megaphone", "Coaching Megaphone")
    private val reserveCount = 2

    @Test
    @DisplayName("Reserve breakdown allocates best tier first")
    fun testReservedMegaphoneBreakdown() {
        val inventory =
            mapOf(
                "Empowering Megaphone" to 1,
                "Motivating Megaphone" to 1,
                "Coaching Megaphone" to 5,
            )
        val breakdown = Trackblazer.reservedMegaphoneBreakdown(inventory, reserveCount, reserveOrder)
        assertEquals(1, breakdown["Empowering Megaphone"])
        assertEquals(1, breakdown["Motivating Megaphone"])
        assertNull(breakdown["Coaching Megaphone"])
    }

    @Test
    @DisplayName("1×60% + 1×40% reserve prefers surplus Motivating before Coaching")
    fun testOneEmpoweringOneMotivatingReservePrefersMotivating() {
        val inventory =
            mapOf(
                "Empowering Megaphone" to 1,
                "Motivating Megaphone" to 2,
                "Coaching Megaphone" to 5,
            )
        assertEquals(
            "Motivating Megaphone",
            Trackblazer.pickSurplusBurnMegaphone(
                inventory = inventory,
                reserveCount = reserveCount,
                reserveOrder = reserveOrder,
                activeMegaphoneType = null,
                megaphoneTurnCounter = 0,
                motivatingUpgradeAllowed = true,
            ),
        )
    }

    @Test
    @DisplayName("2×60% reserve uses Coaching when fewer than 2 surplus Motivating exist")
    fun testTwoEmpoweringReserveRequiresTwoMotivatingForFortyPercent() {
        val inventory =
            mapOf(
                "Empowering Megaphone" to 2,
                "Motivating Megaphone" to 1,
                "Coaching Megaphone" to 5,
            )
        assertEquals(
            "Coaching Megaphone",
            Trackblazer.pickSurplusBurnMegaphone(
                inventory = inventory,
                reserveCount = reserveCount,
                reserveOrder = reserveOrder,
                activeMegaphoneType = null,
                megaphoneTurnCounter = 0,
                motivatingUpgradeAllowed = true,
            ),
        )
    }

    @Test
    @DisplayName("2×60% reserve uses Motivating when at least 2 surplus 40% exist")
    fun testTwoEmpoweringReserveUsesMotivatingWithTwoSurplus() {
        val inventory =
            mapOf(
                "Empowering Megaphone" to 2,
                "Motivating Megaphone" to 3,
                "Coaching Megaphone" to 5,
            )
        assertEquals(
            "Motivating Megaphone",
            Trackblazer.pickSurplusBurnMegaphone(
                inventory = inventory,
                reserveCount = reserveCount,
                reserveOrder = reserveOrder,
                activeMegaphoneType = null,
                megaphoneTurnCounter = 0,
                motivatingUpgradeAllowed = true,
            ),
        )
    }

    @Test
    @DisplayName("Default reserve shape still prefers Coaching before Motivating")
    fun testDefaultReserveShapePrefersCoaching() {
        val inventory =
            mapOf(
                "Empowering Megaphone" to 2,
                "Motivating Megaphone" to 0,
                "Coaching Megaphone" to 5,
            )
        assertEquals(
            "Coaching Megaphone",
            Trackblazer.pickSurplusBurnMegaphone(
                inventory = inventory,
                reserveCount = reserveCount,
                reserveOrder = reserveOrder,
                activeMegaphoneType = null,
                megaphoneTurnCounter = 0,
                motivatingUpgradeAllowed = true,
            ),
        )
    }
}
