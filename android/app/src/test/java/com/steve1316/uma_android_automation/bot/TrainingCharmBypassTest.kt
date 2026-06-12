package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Training charm min-gain rules")
class TrainingCharmBypassTest {
    @Test
    @DisplayName("Low-gain charm skip does not apply when failure is within threshold (e.g. 0% without charm)")
    fun testLowGainCharmSkipDoesNotApplyBelowFailureThreshold() {
        assertFalse(
            Training.wouldSkipForLowGainCharm(
                allowCharmForStat = true,
                climaxForceCharmTraining = false,
                failureChanceExceedsThreshold = false,
                mainStatGainBelowMin = true,
            ),
        )
    }

    @Test
    @DisplayName("Low-gain charm skip applies when failure exceeds threshold and gain is below min")
    fun testLowGainCharmSkipWhenRiskyAndLowGain() {
        assertTrue(
            Training.wouldSkipForLowGainCharm(
                allowCharmForStat = true,
                climaxForceCharmTraining = false,
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
    @DisplayName("Charm bypass requires failure above threshold and min stat gain")
    fun testWouldAllowCharmBypassForStat() {
        assertFalse(
            Training.wouldAllowCharmBypassForStat(
                failureChance = 0,
                mainStatGain = 10,
                effectiveFailureChance = 25,
                climaxForceCharmTraining = false,
                minStatGainForCharm = 25,
            ),
        )
        assertTrue(
            Training.wouldAllowCharmBypassForStat(
                failureChance = 40,
                mainStatGain = 30,
                effectiveFailureChance = 25,
                climaxForceCharmTraining = false,
                minStatGainForCharm = 25,
            ),
        )
        assertFalse(
            Training.wouldAllowCharmBypassForStat(
                failureChance = 40,
                mainStatGain = 10,
                effectiveFailureChance = 25,
                climaxForceCharmTraining = false,
                minStatGainForCharm = 25,
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
