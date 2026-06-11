package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Training charm min-gain bypass at zero energy")
class TrainingCharmBypassTest {
    @Test
    @DisplayName("Low-gain charm skip bypassed when allowLowGainCharmAtZeroEnergy is true")
    fun testAllowLowGainCharmAtZeroEnergyBypassesSkip() {
        assertFalse(
            Training.wouldSkipForLowGainCharm(
                allowCharmForStat = true,
                climaxForceCharmTraining = false,
                allowLowGainCharmAtZeroEnergy = true,
                failureChanceExceedsThreshold = true,
                mainStatGainBelowMin = true,
            ),
        )
    }

    @Test
    @DisplayName("Low-gain charm skip applies even when failure is below threshold (GUTS parity)")
    fun testLowGainCharmSkipAppliesBelowFailureThreshold() {
        assertTrue(
            Training.wouldSkipForLowGainCharm(
                allowCharmForStat = true,
                climaxForceCharmTraining = false,
                allowLowGainCharmAtZeroEnergy = false,
                failureChanceExceedsThreshold = false,
                mainStatGainBelowMin = true,
            ),
        )
    }

    @Test
    @DisplayName("Low-gain charm skip still applies without zero-energy bypass")
    fun testLowGainCharmSkipWithoutBypass() {
        assertTrue(
            Training.wouldSkipForLowGainCharm(
                allowCharmForStat = true,
                climaxForceCharmTraining = false,
                allowLowGainCharmAtZeroEnergy = false,
                failureChanceExceedsThreshold = true,
                mainStatGainBelowMin = true,
            ),
        )
    }

    @Test
    @DisplayName("Climax force-charm training never skips for low gain")
    fun testClimaxForceCharmNeverSkipsForLowGain() {
        assertFalse(
            Training.wouldSkipForLowGainCharm(
                allowCharmForStat = true,
                climaxForceCharmTraining = true,
                allowLowGainCharmAtZeroEnergy = false,
                failureChanceExceedsThreshold = true,
                mainStatGainBelowMin = true,
            ),
        )
    }

    @Test
    @DisplayName("Failure threshold uses max fail unless risky training qualifies")
    fun testEffectiveFailureThreshold() {
        assertEquals(
            25,
            Training.effectiveFailureThresholdForMainGain(
                mainStatGain = 20,
                maximumFailureChance = 25,
                enableRiskyTraining = true,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
        assertEquals(
            30,
            Training.effectiveFailureThresholdForMainGain(
                mainStatGain = 45,
                maximumFailureChance = 25,
                enableRiskyTraining = true,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
        assertFalse(
            Training.exceedsFailureThreshold(
                failureChance = 25,
                mainStatGain = 20,
                maximumFailureChance = 25,
                enableRiskyTraining = false,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
        assertTrue(
            Training.exceedsFailureThreshold(
                failureChance = 26,
                mainStatGain = 20,
                maximumFailureChance = 25,
                enableRiskyTraining = false,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
    }

    @Test
    @DisplayName("Requires mitigation when failure exceeds threshold and charm was not used")
    fun testRequiresFailureMitigationBeforeExecute() {
        assertTrue(
            Training.requiresFailureMitigationBeforeExecute(
                failureChance = 40,
                mainStatGain = 20,
                charmUsed = false,
                climaxForceCharm = false,
                maximumFailureChance = 25,
                enableRiskyTraining = false,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
        assertFalse(
            Training.requiresFailureMitigationBeforeExecute(
                failureChance = 40,
                mainStatGain = 20,
                charmUsed = true,
                climaxForceCharm = false,
                maximumFailureChance = 25,
                enableRiskyTraining = false,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
        assertFalse(
            Training.requiresFailureMitigationBeforeExecute(
                failureChance = 40,
                mainStatGain = 20,
                charmUsed = false,
                climaxForceCharm = true,
                maximumFailureChance = 25,
                enableRiskyTraining = false,
                riskyTrainingMinStatGain = 40,
                riskyTrainingMaxFailureChance = 30,
            ),
        )
    }
}
