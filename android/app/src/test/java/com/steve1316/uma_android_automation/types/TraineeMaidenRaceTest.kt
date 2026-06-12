package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trainee maiden race completion")
class TraineeMaidenRaceTest {
    @Test
    fun `requires beginner fan class when no races have been completed`() {
        val trainee = Trainee()
        trainee.fanCountClass = FanCountClass.MAIDEN

        assertFalse(trainee.bHasCompletedMaidenRace)
    }

    @Test
    fun `treats completed non-maiden race as maiden done even when fan OCR is stale`() {
        val trainee = Trainee()
        trainee.fanCountClass = FanCountClass.MAIDEN
        trainee.noteCompletedRaceGrade(RaceGrade.OP)

        assertTrue(trainee.bHasCompletedMaidenRace)
    }

    @Test
    fun `does not mark maiden complete after debut or maiden grade races`() {
        val trainee = Trainee()
        trainee.fanCountClass = FanCountClass.MAIDEN
        trainee.noteCompletedRaceGrade(RaceGrade.DEBUT)
        trainee.noteCompletedRaceGrade(RaceGrade.MAIDEN)

        assertFalse(trainee.bHasCompletedMaidenRace)
    }
}
