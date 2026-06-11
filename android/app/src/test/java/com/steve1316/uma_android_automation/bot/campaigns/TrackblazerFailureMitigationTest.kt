package com.steve1316.uma_android_automation.bot.campaigns

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trackblazer surplus Kale Juice rest recovery")
class TrackblazerFailureMitigationTest {
    @Test
    @DisplayName("Surplus Kale Juice rest recovery outside summer")
    fun testSurplusKaleJuiceRestRecovery() {
        assertTrue(Trackblazer.kaleJuiceRestRecoveryEligible(isSummer = false, kaleJuiceCount = 2, energy = 45))
        assertTrue(Trackblazer.kaleJuiceRestRecoveryEligible(isSummer = false, kaleJuiceCount = 3, energy = 50))
        assertFalse(Trackblazer.kaleJuiceRestRecoveryEligible(isSummer = false, kaleJuiceCount = 1, energy = 45))
        assertFalse(Trackblazer.kaleJuiceRestRecoveryEligible(isSummer = true, kaleJuiceCount = 2, energy = 45))
        assertFalse(Trackblazer.kaleJuiceRestRecoveryEligible(isSummer = false, kaleJuiceCount = 2, energy = 55))
    }

    @Test
    @DisplayName("Low energy opens energy-item recovery when Vita or Kale is in stock")
    fun testShouldTryEnergyRecoveryItems() {
        val lowEnergyInventory = mapOf("Vita 65" to 1)
        val kaleOnlyInventory = mapOf("Royal Kale Juice" to 1)
        val emptyInventory = emptyMap<String, Int>()

        assertTrue(Trackblazer.shouldTryEnergyRecoveryItems(energy = 0, energyThreshold = 50, inventory = lowEnergyInventory))
        assertTrue(Trackblazer.shouldTryEnergyRecoveryItems(energy = 30, energyThreshold = 50, inventory = kaleOnlyInventory))
        assertFalse(Trackblazer.shouldTryEnergyRecoveryItems(energy = 60, energyThreshold = 50, inventory = lowEnergyInventory))
        assertFalse(Trackblazer.shouldTryEnergyRecoveryItems(energy = 0, energyThreshold = 50, inventory = emptyInventory))
    }
}
