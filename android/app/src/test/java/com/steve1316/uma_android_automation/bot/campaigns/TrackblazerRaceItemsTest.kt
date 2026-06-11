package com.steve1316.uma_android_automation.bot.campaigns

import com.steve1316.uma_android_automation.types.RaceGrade
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trackblazer race item conservation")
class TrackblazerRaceItemsTest {
    private val reserve = 2

    @Test
    @DisplayName("Pre-finale only spends master hammers above the finale reserve")
    fun testPreFinaleMasterHammerReserve() {
        assertEquals(0, Trackblazer.spareMasterCleatHammers(2, reserve))
        assertEquals(1, Trackblazer.spareMasterCleatHammers(3, reserve))

        assertFalse(Trackblazer.canUseMasterCleatHammer(40, 2, RaceGrade.G1, reserve))
        assertTrue(Trackblazer.canUseMasterCleatHammer(40, 3, RaceGrade.G1, reserve))
        assertTrue(Trackblazer.canUseMasterCleatHammer(40, 3, RaceGrade.G2, reserve))
        assertFalse(Trackblazer.canUseMasterCleatHammer(40, 3, RaceGrade.G3, reserve))
    }

    @Test
    @DisplayName("Finale days 73-74 require two master hammers before spending one")
    fun testFinaleMasterHammerTwoCopyRule() {
        assertFalse(Trackblazer.canUseMasterCleatHammer(73, 1, RaceGrade.G1, reserve))
        assertTrue(Trackblazer.canUseMasterCleatHammer(73, 2, RaceGrade.G1, reserve))
        assertFalse(Trackblazer.canUseMasterCleatHammer(74, 1, RaceGrade.G1, reserve))
        assertTrue(Trackblazer.canUseMasterCleatHammer(74, 2, RaceGrade.G1, reserve))
    }

    @Test
    @DisplayName("Day 75 uses any remaining master hammer on G1")
    fun testFinalDayUsesLastMasterHammer() {
        assertTrue(Trackblazer.canUseMasterCleatHammer(75, 1, RaceGrade.G1, reserve))
        assertFalse(Trackblazer.canUseMasterCleatHammer(75, 1, RaceGrade.G2, reserve))
    }
}
