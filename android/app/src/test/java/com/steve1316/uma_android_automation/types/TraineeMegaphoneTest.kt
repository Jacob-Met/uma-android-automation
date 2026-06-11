package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trainee megaphone bonus")
class TraineeMegaphoneTest {
    @Test
    fun `returns base gain when no megaphone is active`() {
        val trainee = Trainee()
        trainee.megaphoneTurnCounter = 0
        assertEquals(25, trainee.mainStatGainWithActiveMegaphoneBonus(25))
    }

    @Test
    fun `applies empowering megaphone bonus`() {
        val trainee = Trainee()
        trainee.megaphoneTurnCounter = 2
        trainee.activeMegaphoneType = "Empowering Megaphone"
        assertEquals(40, trainee.mainStatGainWithActiveMegaphoneBonus(25))
    }

    @Test
    fun `applies motivating megaphone bonus`() {
        val trainee = Trainee()
        trainee.megaphoneTurnCounter = 2
        trainee.activeMegaphoneType = "Motivating Megaphone"
        assertEquals(35, trainee.mainStatGainWithActiveMegaphoneBonus(25))
    }

    @Test
    fun `applies coaching megaphone bonus`() {
        val trainee = Trainee()
        trainee.megaphoneTurnCounter = 2
        trainee.activeMegaphoneType = "Coaching Megaphone"
        assertEquals(30, trainee.mainStatGainWithActiveMegaphoneBonus(25))
    }
}
