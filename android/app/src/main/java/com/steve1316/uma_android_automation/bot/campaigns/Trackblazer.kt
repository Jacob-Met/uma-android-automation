package com.steve1316.uma_android_automation.bot.campaigns

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.DialogHandlerResult
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.MainScreenAction
import com.steve1316.uma_android_automation.bot.Racing
import com.steve1316.uma_android_automation.bot.SelectionSource
import com.steve1316.uma_android_automation.bot.solver.SmartRaceSolverIntegration
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonConfirmUse
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRaceDayRace
import com.steve1316.uma_android_automation.components.ButtonRaceListFullStats
import com.steve1316.uma_android_automation.components.ButtonRaces
import com.steve1316.uma_android_automation.components.ButtonShopTrackblazer
import com.steve1316.uma_android_automation.components.ButtonSkillUp
import com.steve1316.uma_android_automation.components.ButtonTraining
import com.steve1316.uma_android_automation.components.ButtonTrainingItems
import com.steve1316.uma_android_automation.components.ButtonUseTrainingItems
import com.steve1316.uma_android_automation.components.DialogConfirmUse
import com.steve1316.uma_android_automation.components.DialogExchangeComplete
import com.steve1316.uma_android_automation.components.DialogInterface
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconGoalRibbon
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
import com.steve1316.uma_android_automation.components.IconRaceListPredictionDoubleStar
import com.steve1316.uma_android_automation.components.IconTrainingEventHorseshoe
import com.steve1316.uma_android_automation.components.IconUnityCupTutorialHeader
import com.steve1316.uma_android_automation.components.LabelRivalRacer
import com.steve1316.uma_android_automation.components.LabelScheduledRace
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.NegativeStatus
import com.steve1316.uma_android_automation.types.PositiveStatus
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.types.ScannedItem
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import com.steve1316.uma_android_automation.types.TrackblazerShopList
import com.steve1316.uma_android_automation.types.Trainee
import com.steve1316.uma_android_automation.utils.ScrollList
import com.steve1316.uma_android_automation.utils.ScrollListEntry
import org.json.JSONArray
import org.opencv.core.Point

/**
 * Handles the Trackblazer scenario with scenario-specific logic and handling.
 *
 * @property game The [Game] instance for interacting with the game state.
 */
class Trackblazer(game: Game) : Campaign(game) {
    /** Flag indicating if the tutorial has been disabled. */
    private var tutorialDisabled = false

    /** Representation of the item shop list along with the mapping of items to their price and effect. */
    private val shopList: TrackblazerShopList = TrackblazerShopList(game)

    init {
        shopList.getInventorySummaryCallback = { getInventorySummary() }
    }

    /** Current number of coins available to spend in the shop. */
    var shopCoins: Int = 0

    /** Map representing the current inventory of items. */
    var currentInventory: Map<String, Int> = mapOf()

    /** Map representing the mapping of bad condition items to their enums. */
    val badConditionMap =
        mapOf(
            "Fluffy Pillow" to NegativeStatus.NIGHT_OWL.statusName,
            "Pocket Planner" to NegativeStatus.SLACKER.statusName,
            "Rich Hand Cream" to NegativeStatus.SKIN_OUTBREAK.statusName,
            "Smart Scale" to NegativeStatus.SLOW_METABOLISM.statusName,
            "Aroma Diffuser" to NegativeStatus.MIGRAINE.statusName,
            "Practice Drills DVD" to NegativeStatus.PRACTICE_POOR.statusName,
        )

    /** Map representing the mapping of good condition items to their enums. */
    val goodConditionMap =
        mapOf(
            "Pretty Mirror" to PositiveStatus.CHARMING.statusName,
            "Reporter's Binoculars" to PositiveStatus.HOT_TOPIC.statusName,
            "Master Practice Guide" to PositiveStatus.PRACTICE_PERFECT.statusName,
            "Scholar's Hat" to PositiveStatus.FAST_LEARNER.statusName,
        )

    /** The limit for consecutive races before the bot should stop and recover. */
    private val consecutiveRacesLimit: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerConsecutiveRacesLimit", 5)

    /** List of race grades that trigger a shop check afterward. */
    private val shopCheckGrades: List<RaceGrade> =
        try {
            val gradesString = SettingsHelper.getStringSetting("scenarioOverrides", "trackblazerShopCheckGrades", "[\"G1\",\"G2\",\"G3\"]")
            val jsonArray = JSONArray(gradesString)
            val grades = mutableListOf<RaceGrade>()
            for (i in 0 until jsonArray.length()) {
                val gradeName = jsonArray.getString(i)
                val grade = RaceGrade.fromName(gradeName)
                if (grade != null) {
                    grades.add(grade)
                }
            }
            grades
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] shopCheckGrades:: Failed to parse shopCheckGrades setting: ${e.message}")
            listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3)
        }

    /** List of preferred track distances for race selection prioritization. */
    private val preferredDistances: List<TrackDistance> =
        try {
            val distancesString = SettingsHelper.getStringSetting("scenarioOverrides", "trackblazerPreferredDistances", "[]")
            val jsonArray = JSONArray(distancesString)
            val distances = mutableListOf<TrackDistance>()
            for (i in 0 until jsonArray.length()) {
                val distanceName = jsonArray.getString(i)
                val distance = TrackDistance.fromName(distanceName)
                if (distance != null) {
                    distances.add(distance)
                }
            }
            distances
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] preferredDistances:: Failed to parse setting: ${e.message}")
            emptyList()
        }

    /** List of preferred track surfaces for race selection prioritization. */
    private val preferredSurfaces: List<TrackSurface> =
        try {
            val surfacesString = SettingsHelper.getStringSetting("scenarioOverrides", "trackblazerPreferredSurfaces", "[]")
            val jsonArray = JSONArray(surfacesString)
            val surfaces = mutableListOf<TrackSurface>()
            for (i in 0 until jsonArray.length()) {
                val surfaceName = jsonArray.getString(i)
                val surface = TrackSurface.fromName(surfaceName)
                if (surface != null) {
                    surfaces.add(surface)
                }
            }
            surfaces
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] preferredSurfaces:: Failed to parse setting: ${e.message}")
            emptyList()
        }

    /** Tracks the number of consecutive races performed. */
    private var consecutiveRaceCount: Int = 0

    /** Flag to prevent double incrementing the counter when OCR already updated it. */
    private var counterUpdatedByOCR: Boolean = false

    /** Whether the Reset Whistle has been used this turn. */
    private var bUsedWhistleToday: Boolean = false

    /** Whether the Good-Luck Charm has been used this turn. */
    private var bUsedCharmToday: Boolean = false

    /** Prevents double megaphone decrement when a race completes via both handleRaceEvents and executeAction. */
    private var megaphoneDecrementedThisTurn: Boolean = false

    /** Stat that megaphone/ankle weights were queued for this training-item pass (null = none or global-only items). */
    private var statSpecificTrainingItemsQueuedFor: StatName? = null

    /** When true, [manageInventoryItems] only scans megaphone + ankle weights for [trainingSelected]. */
    private var bStatSpecificItemsOnlyPass: Boolean = false

    /** Whether an energy-restoring item was queued during the current training-item pass (reset each [manageInventoryItems] call). */
    private var bUsedEnergyItemThisPass: Boolean = false

    /** Energy vs Good-Luck Charm choice for failure mitigation on the current training-item pass. */
    private enum class FailureMitigationChoice {
        NONE,
        ENERGY,
        CHARM,
    }

    private var failureMitigationChoiceForPass: FailureMitigationChoice = FailureMitigationChoice.NONE

    /** Ordered energy items to queue for high-failure mitigation this pass (may repeat, e.g. 2× Vita 20). */
    private var failureMitigationEnergyPlanForPass: List<String>? = null

    /** Count of each energy item already queued toward [failureMitigationEnergyPlanForPass] this pass. */
    private val failureMitigationEnergyQueuedCounts = mutableMapOf<String, Int>()

    /** Target tier for high-failure energy mitigation based on failure/main-gain qualification. */
    private enum class FailureMitigationEnergyTier {
        KALE_100,
        VITA_65,
        VITA_40,
        VITA_20,
    }

    /** Whether Royal Kale Juice was queued during the current inventory management pass. Reset at the start of each pass. Used to fire a cupcake in the same pass to offset the -1 mood penalty. */
    private var bKaleJuiceQueuedThisPass: Boolean = false

    /** Whether a race hammer has been used this turn. */
    private var bUsedHammerToday: Boolean = false

    /** Flag indicating that the bot decided to train instead of running extra races due to high stat gains. */
    private var bIsIrregularTraining: Boolean = false

    /** Tracks whether the inventory has been synced at least once during this session. */
    private var bInventorySynced: Boolean = false

    /** Flag to track when a shop check should be performed after a race. */
    private var bShouldCheckShop: Boolean = false

    /** Flag to track if the first-time Shop check for the session has been performed. */
    private var bInitialShopCheckPerformed: Boolean = false

    /** Flag indicating if the bot has checked for Irregular Training during the current turn. */
    private var bHasCheckedIrregularTrainingThisTurn: Boolean = false

    /** Mapping of energy-restoring items to their gain values. */
    private val energyGains =
        mapOf(
            "Royal Kale Juice" to 100,
            "Vita 65" to 65,
            "Vita 40" to 40,
            "Vita 20" to 20,
            "Energy Drink MAX" to 5,
        )

    /** Threshold for energy level to use energy items. */
    private var energyThresholdToUseEnergyItems: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerEnergyThreshold", 40)

    /**
     * When enabled, use an energy item on a high-failure training turn when failure exceeds maximum + margin and main gain meets minimum
     * (similar to Good-Luck Charm), then re-analyze before executing training.
     */
    private val enableEnergyItemForHighFailureTraining: Boolean =
        SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerEnableEnergyItemForHighFailureTraining", true)

    /** Per-item failure margin above [Training.getMaximumFailureChance] for the high-failure energy-item train path. */
    private val vita20FailureAboveMinimum: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerVita20FailureAboveMinimum", 10)

    private val vita40FailureAboveMinimum: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerVita40FailureAboveMinimum", 20)

    private val vita65FailureAboveMinimum: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerVita65FailureAboveMinimum", 50)

    /** Minimum main stat gain on the selected training before the high-failure energy-item train path may fire. */
    private val energyItemMinMainStatGain: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerEnergyItemMinMainStatGain", 30)

    /** Number of energy items (lowest-tier first across `energyItemConservationOrder`) held back as the emergency-race-recovery reserve. 0 = no reserve. */
    private val energyItemReserveCount: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerEnergyItemReserve", 1)

    /** Number of cupcakes (Plain preferred over Berry Sweet) held back so Royal Kale Juice's mood penalty can be offset. 0 = no reserve. */
    private val cupcakeReserveCount: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerCupcakeReserve", 1)

    /** Number of Master Cleat Hammers held back for the Finale days (73-75). 0 = no reserve, spend freely on G1/G2 races. */
    private val masterHammerFinaleReserve: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerMasterHammerFinaleReserve", 2)

    /** Minimum Artisan Cleat Hammer stock required before the bot will spend one on a G3 race. 0 = always allowed when stock > 0. */
    private val artisanMinStockForG3: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerArtisanHammerMinStockForG3", 3)

    /** Minimum Artisan Cleat Hammer stock required before the bot will spend one on a G2 race. 0 = always allowed when stock > 0. G1 is always allowed. */
    private val artisanMinStockForG2: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerArtisanHammerMinStockForG2", 2)

    /** Number of Glow Sticks held back for Day 75 (the Final). 0 = no reserve. */
    private val glowStickFinalReserve: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerGlowStickFinalReserve", 1)

    /** Minimum projected fan gain on a race before a Glow Stick is used. 0 = use on any race. */
    private val glowStickMinFans: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerGlowStickMinFans", 20000)

    /** Whether the Reset Whistle forces training. */
    private val whistleForcesTraining: Boolean = SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerWhistleForcesTraining", false)

    /** When enabled, Reset Whistles are only used during Summer training (not on other senior turns). */
    private val saveResetWhistlesForSummer: Boolean = SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerSaveResetWhistlesForSummer", true)

    /**
     * When enabled, Reset Whistles are not used after Senior Summer (turn 65) until the Finale (turns 73–75),
     * so stock can be spent during Climax training instead of on late Senior turns.
     */
    private val saveResetWhistlesForFinale: Boolean =
        SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerSaveResetWhistlesForFinale", true)

    /**
     * During Summer and Finale, skip Reset Whistle when a top-3 priority training has at least this many rainbows
     * (only after qualifying orange friendship bars are on screen). 0 disables. Falls back to legacy main-gain key.
     */
    private val whistlePriorityMinRainbow: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerWhistlePriorityMinRainbow", 1)

    /** After a Reset Whistle reshuffle, recover instead of training when failure is at/above this and main gain is below [whistlePostShuffleMinMainGain]. 0 disables. */
    private val whistlePostShuffleMinFailure: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerWhistlePostShuffleMinFailure", 0)

    /** Paired with [whistlePostShuffleMinFailure] for post-whistle recovery. 0 disables the post-whistle recovery check. */
    private val whistlePostShuffleMinMainGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerWhistlePostShuffleMinMainGain", 0)

    /** Whether to enable Irregular Training in between races during Trackblazer. */
    private val enableIrregularTraining: Boolean = SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerEnableIrregularTraining", false)

    /**
     * When enabled during Climax (turns 73–75), forces charm-backed training on highest non-maxed stat instead of rest/recovery.
     * When disabled, Climax uses normal charm rules (min gain floor, failure thresholds, mood conservation).
     */
    private val enableClimaxCharmTraining: Boolean =
        SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerEnableClimaxCharmTraining", true)

    /** The minimum stat gain required for using a Good-Luck Charm to bypass failure chance. */
    private val minCharmGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerMinStatGainForCharm", 25)

    /** The minimum stat gain threshold for irregular training evaluation. */
    private val minIrregularGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerIrregularTrainingMinStatGain", 30)

    /**
     * When enabled, conserve a pooled stock of Good-Luck Charm + Vita 65 + Royal Kale Juice during Senior pre-Finale (65–72)
     * for Finale use (see [failureMitigationPoolReserve]) unless the pool override applies. Summer is excluded — spend freely during Summer.
     */
    private val saveGoodLuckCharmForSummer: Boolean =
        SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerSaveGoodLuckCharmForSummer", true)

    /**
     * Combined reserve across Good-Luck Charm, Vita 65 (+65), and Royal Kale Juice (+100). Any mix totalling this count is held
     * during [isFailureMitigationPoolReservationPeriod] unless [isFailureMitigationPoolOverride] applies. 0 disables the pool.
     */
    private val failureMitigationPoolReserve: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerFailureMitigationPoolReserve", 4)

    /**
     * During the pool reservation window, allow spending a reserved item when main gain is at least this on a risky training that
     * still needs charm or +65/+100 and has no other mitigation path. 0 disables the override.
     */
    private val summerCharmOverrideMinStatGain: Int =
        SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerSummerCharmOverrideMinStatGain", 30)

    /** Ordered list of energy items from lowest to highest gain, used for conservation priority. */
    private val energyItemConservationOrder = listOf("Energy Drink MAX", "Vita 20", "Vita 40", "Vita 65")

    /** +65 / +100 items that may fire on the high-failure train path without a failure-above-max margin (when no charm is used). */
    private val highTierFailureMitigationEnergyItems = setOf("Royal Kale Juice", "Vita 65")

    /** Pooled high-tier failure-mitigation items conserved together during Senior pre-Finale (65–72). */
    private val failureMitigationPoolItems = listOf("Good-Luck Charm", "Vita 65", "Royal Kale Juice")

    /** Flag to bypass conservation and force-use the reserved energy item. */
    private var bForceUseReservedItem: Boolean = false

    /**
     * When mood is below NORMAL (BAD or AWFUL), training resources (Reset Whistle reshuffle, Good-Luck Charm, and Megaphones) refuse to fire if main-stat gain is below this floor.
     * Prevents wasting items on structurally low-return turns where the mood multiplier caps the gain.
     */
    private val lowMainStatGainItemFloor: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerLowMainStatGainItemFloor", 15)

    /** Minimum main stat gain required before using Coaching Megaphone (0 = disabled). Ignored during summer. */
    private val coachingMegaphoneMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerCoachingMegaphoneMinStatGain", 15)

    /** Minimum main stat gain required before using Motivating Megaphone (0 = disabled). Ignored during summer. */
    private val motivatingMegaphoneMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerMotivatingMegaphoneMinStatGain", 25)

    /** Minimum main stat gain required before using Empowering Megaphone (0 = disabled). Ignored during summer. */
    private val empoweringMegaphoneMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerEmpoweringMegaphoneMinStatGain", 30)

    /** Minimum main stat gain required before using Speed Ankle Weights (0 = disabled). */
    private val speedAnkleWeightMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerSpeedAnkleWeightMinStatGain", 25)

    /** Minimum main stat gain required before using Stamina Ankle Weights (0 = disabled). */
    private val staminaAnkleWeightMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerStaminaAnkleWeightMinStatGain", 0)

    /** Minimum main stat gain required before using Power Ankle Weights (0 = disabled). */
    private val powerAnkleWeightMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerPowerAnkleWeightMinStatGain", 20)

    /** Minimum main stat gain required before using Guts Ankle Weights (0 = disabled). */
    private val gutsAnkleWeightMinStatGain: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerGutsAnkleWeightMinStatGain", 0)

    /** Number of each ankle weight type to hold back outside summer training (0 = no reserve). */
    private val ankleWeightSummerReserve: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerAnkleWeightSummerReserve", 2)

    /** Number of each megaphone type to hold back outside summer training (0 = no reserve). Lower-tier reserves are waived once a higher tier meets this target. */
    private val megaphoneSummerReserveCount: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerMegaphoneSummerReserve", 2)

    /** Megaphone types ordered best-to-worst for summer reserve allocation (highest tier reserved first). */
    private val megaphoneSummerReserveOrder = listOf("Empowering Megaphone", "Motivating Megaphone", "Coaching Megaphone")

    /** The frequency to check the shop after a race. */
    private val shopCheckFrequency: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerShopCheckFrequency", 3)

    /**
     * Turn at which Artisan/Glow Stick conservation rules start applying. Before this turn Artisan hammers are used freely on every race they qualify for.
     * Master Cleat Hammer finale reserve is always honored from turn 13 onward. The Glow Stick min-fans floor still applies pre-conservation.
     */
    private val raceItemConservationStartDay: Int = 65

    /** Tracks the number of days since the last race for shop check frequency. */
    private var shopCheckCounter: Int = 0

    /** Returns true during Trackblazer Climax (Finale turns 73-75). */
    private fun isClimaxPhase(): Boolean = date.bIsFinaleSeason && date.day >= 73

    /** Returns true when a Good-Luck Charm is available in inventory and has not been used today. */
    private fun hasRemainingGoodLuckCharm(): Boolean = date.day >= 13 && !bUsedCharmToday && (currentInventory["Good-Luck Charm"] ?: 0) > 0

    /** Returns true when full Climax charm training should override rest/recovery paths. */
    private fun isClimaxCharmTrainingActive(): Boolean =
        enableClimaxCharmTraining && isClimaxPhase() && hasRemainingGoodLuckCharm()

    /** Returns true when >= 20% failure floor may be ignored for Good-Luck Charm use during Climax. */
    private fun canBypassClimaxCharmFailureFloor(): Boolean =
        enableClimaxCharmTraining && isClimaxPhase() && hasRemainingGoodLuckCharm()

    /** Energy at or below which the bot backs out for rest/recovery when no viable training remains. */
    private val restRecoveryEnergyThreshold: Int = 50

    /** Whether analysis may globally ignore failure thresholds (Climax force-charm only). Per-stat charm uses [assumesGoodLuckCharmMitigation]. */
    private fun charmAssumedForAnalysis(): Boolean = isClimaxCharmTrainingActive()

    override fun assumesGoodLuckCharmMitigation(stat: StatName, failureChance: Int, mainStatGain: Int): Boolean =
        hasRemainingGoodLuckCharm() &&
            passesGoodLuckCharmTrainingChecks(stat, trainee, failureChance, mainStatGain) &&
            canUseFailureMitigationPoolItem("Good-Luck Charm", currentInventory, stat, trainee, failureChance)

    override fun hasTrainingMitigationItemsAvailable(): Boolean =
        hasGoodLuckCharmMitigationPotential() ||
            hasUsableEnergyRecoveryItems() ||
            hasFailureMitigationEnergyItemsPotential()

    /**
     * Whether a Good-Luck Charm may still help this turn (including when the failure-mitigation pool is at the
     * reserve floor but [summerCharmOverrideMinStatGain] override can spend from reserve after a full stat scan).
     */
    private fun hasGoodLuckCharmMitigationPotential(): Boolean {
        if (!hasRemainingGoodLuckCharm()) {
            return false
        }
        if (!isFailureMitigationPoolReservationPeriod()) {
            return true
        }
        if (failureMitigationPoolTotal(currentInventory) > failureMitigationPoolReserve) {
            return true
        }
        return summerCharmOverrideMinStatGain > 0
    }

    /**
     * Whether high-failure energy mitigation items (Vita / Kale on the train path) may still help this turn.
     * Vita 20/40 are outside the pool; pooled Vita 65 / Kale follow the same override rule as charms.
     */
    private fun hasFailureMitigationEnergyItemsPotential(): Boolean {
        if (!enableEnergyItemForHighFailureTraining) {
            return false
        }
        if (listOf("Vita 20", "Vita 40").any { (currentInventory[it] ?: 0) > 0 }) {
            return true
        }
        val pooledEnergyItems = failureMitigationPoolItems.filter { it != "Good-Luck Charm" }
        if (pooledEnergyItems.none { (currentInventory[it] ?: 0) > 0 }) {
            return false
        }
        if (!isFailureMitigationPoolReservationPeriod()) {
            return true
        }
        if (failureMitigationPoolTotal(currentInventory) > failureMitigationPoolReserve) {
            return true
        }
        return summerCharmOverrideMinStatGain > 0
    }

    /** Failure chance and main gain for [stat] from training maps or cached OCR. */
    private fun trainingFailureAndMainGain(stat: StatName): Pair<Int, Int> {
        val cached = training.cachedAnalysisResults?.firstOrNull { it.name == stat }
        val option = training.trainingMap[stat] ?: training.skippedTrainingMap[stat]
        val failure = cached?.failureChance ?: option?.failureChance ?: 0
        val gain = cached?.statGains?.get(stat) ?: option?.statGains?.get(stat) ?: 0
        return failure to gain
    }

    /** Whether Good-Luck Charm would actually queue for [stat] (pool, mood, min-gain, Wit rules). */
    private fun wouldGoodLuckCharmMitigateTraining(
        stat: StatName,
        trainee: Trainee,
        inventory: Map<String, Int> = currentInventory,
    ): Boolean {
        if (isClimaxCharmTrainingActive()) {
            return true
        }
        val (failureChance, _) = trainingFailureAndMainGain(stat)
        return charmEligibleForFailureMitigation(stat, trainee, failureChance, inventory)
    }

    /** Whether a force-picked training can execute with charm or acceptable failure. */
    private fun isViableForcedTrainingPick(stat: StatName): Boolean {
        if (shouldRejectWhistlePostShuffleResult(stat)) {
            return false
        }
        val (failure, mainGain) = trainingFailureAndMainGain(stat)
        if (wouldGoodLuckCharmMitigateTraining(stat, trainee)) {
            return true
        }
        return !training.exceedsFailureThreshold(failure, mainGain)
    }

    /**
     * When the selected training will not receive failure mitigation, prefer a safe fallback from analysis
     * instead of queuing training items and resting.
     */
    private fun resolveExecutableTrainingSelection(
        trainingSelected: StatName?,
        climaxCharmTraining: Boolean,
    ): StatName? {
        if (trainingSelected == null) {
            return training.findBestSafeFallbackTraining()
        }
        if (climaxCharmTraining && isClimaxCharmTrainingActive()) {
            return trainingSelected
        }
        val preFail = training.capturePreItemFailureSnapshot()[trainingSelected] ?: 0
        val mainGain = preItemMainGainFor(trainingSelected)
        if (!training.requiresFailureMitigationBeforeExecute(preFail, mainGain, charmUsed = false, climaxForceCharm = false)) {
            return trainingSelected
        }
        if (wouldGoodLuckCharmMitigateTraining(trainingSelected, trainee)) {
            return trainingSelected
        }
        failureMitigationChoiceForPass = resolveFailureMitigationChoice(trainingSelected, trainee, currentInventory)
        if (failureMitigationChoiceForPass != FailureMitigationChoice.NONE) {
            return trainingSelected
        }
        val fallback = training.findBestSafeFallbackTraining()
        if (fallback != null) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] $trainingSelected needs mitigation that will not queue; switching to executable fallback: $fallback.",
            )
            return fallback
        }
        return null
    }

    /**
     * When every stat exceeds failure thresholds, pick the best option that charm or energy mitigation
     * can still cover (e.g. all tabs at 40% but Good-Luck Charm or Vita qualifies on a high-gain stat).
     */
    private fun bestMitigationBackedTrainingPick(): StatName? {
        if (!hasTrainingMitigationItemsAvailable()) {
            return null
        }
        val stats =
            training.cachedAnalysisResults?.map { it.name }
                ?: (training.trainingMap.keys + training.skippedTrainingMap.keys).distinct()
        if (stats.isEmpty()) {
            return null
        }
        return stats
            .filter { it !in training.blacklist }
            .filter { stat ->
                wouldGoodLuckCharmMitigateTraining(stat, trainee) ||
                    (
                        enableEnergyItemForHighFailureTraining &&
                            resolveFailureMitigationChoice(stat, trainee, currentInventory) != FailureMitigationChoice.NONE
                    )
            }
            .maxByOrNull { selectedTrainingMainGain(it) }
    }

    /** Builds training analysis arguments, applying aggressive charm bypass rules during Climax. */
    private fun buildTrainingAnalysisArgs(): Map<String, Any?> {
        val hasCharm = hasRemainingGoodLuckCharm()
        val climaxCharmTraining = isClimaxCharmTrainingActive()
        return mapOf(
            "ignoreFailureChance" to charmAssumedForAnalysis(),
            "minStatGainForCharm" to if (climaxCharmTraining) 0 else minCharmGain,
            "climaxForceCharmTraining" to climaxCharmTraining,
            "allowLowGainCharmAtZeroEnergy" to (hasCharm && trainee.energy <= 0 && !climaxCharmTraining),
        )
    }

    /** Whether any non-reserved energy-restoring item is available in [inventory]. */
    private fun hasUsableEnergyRecoveryItems(inventory: Map<String, Int> = currentInventory): Boolean {
        val vitaAvailable =
            shopList.energyItemNames.any { (inventory[it] ?: 0) - reservedEnergyUnitsFor(it, inventory) > 0 }
        val kaleAvailable = (inventory["Royal Kale Juice"] ?: 0) > 0
        return vitaAvailable || kaleAvailable
    }

    /** Whether energy items should be considered before training analysis or on the main screen. */
    private fun shouldTryEnergyRecoveryItems(energy: Int = trainee.energy): Boolean =
        energy <= energyThresholdToUseEnergyItems && hasUsableEnergyRecoveryItems()

    /** Scheduled Slow Metabolism cure item to queue (prefers Smart Scale over Miracle Cure). */
    private fun pendingPostEventCureItemToUse(): String? {
        val scheduled = pendingPostEventCureItem ?: return null
        val preferred = getPreferredCureItemForNegativeStatus(NegativeStatus.SLOW_METABOLISM.statusName)
        if (preferred != null && (currentInventory[preferred] ?: 0) > 0) {
            return preferred
        }
        return scheduled.takeIf { (currentInventory[it] ?: 0) > 0 }
    }

    private fun isPendingPostEventCureItem(itemName: String): Boolean = pendingPostEventCureItemToUse() == itemName

    /** Non-summer only: more than one Kale Juice in stock (one held back, surplus may cover rest-level energy). */
    private fun hasSurplusKaleJuiceForRest(inventory: Map<String, Int>): Boolean =
        !date.isSummer() && (inventory["Royal Kale Juice"] ?: 0) > 1

    /** Energy low enough that the bot would rest/recover instead of training. */
    private fun energyLowEnoughForRestRecovery(energy: Int): Boolean = energy <= restRecoveryEnergyThreshold

    /** Whether Royal Kale Juice may be used for rest-level energy recovery (non-summer surplus rule). */
    private fun kaleJuiceRestRecoveryEligible(inventory: Map<String, Int>, energy: Int): Boolean =
        hasSurplusKaleJuiceForRest(inventory) && energyLowEnoughForRestRecovery(energy)

    /** Failure margin above maximum failure chance required before [itemName] may fire on the high-failure train path (low-tier items only). */
    private fun energyItemFailureAboveMinimumFor(itemName: String): Int =
        when (itemName) {
            "Vita 20" -> vita20FailureAboveMinimum
            "Vita 40" -> vita40FailureAboveMinimum
            "Vita 65" -> vita65FailureAboveMinimum
            else -> 0
        }

    private fun isHighTierFailureMitigationEnergyItem(itemName: String): Boolean =
        itemName in highTierFailureMitigationEnergyItems

    private fun isFailureMitigationEnergyItem(itemName: String): Boolean =
        shopList.energyItemNames.contains(itemName) || isHighTierFailureMitigationEnergyItem(itemName)

    private fun kaleJuiceMoodGateMet(inventory: Map<String, Int>): Boolean {
        val hasMoodItems = inventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
        return trainee.energy <= 20 || hasMoodItems || trainee.mood == Mood.AWFUL
    }

    private fun failureMitigationPoolTotal(inventory: Map<String, Int>): Int =
        failureMitigationPoolItems.sumOf { inventory[it] ?: 0 }

    /** Senior pre-Finale (65–72): hold back [failureMitigationPoolReserve] pooled mitigation items for Finale. Not active during Summer. */
    private fun isFailureMitigationPoolReservationPeriod(): Boolean {
        if (!saveGoodLuckCharmForSummer || isClimaxCharmTrainingActive() || failureMitigationPoolReserve <= 0) {
            return false
        }
        return date.day in 65..72
    }

    /**
     * High-gain risky training that still needs a pooled mitigation item and has no other mitigation path.
     * Allows spending from the reserve when the pool is at or below the reserve floor.
     */
    private fun isFailureMitigationPoolOverride(
        trainingSelected: StatName,
        trainee: Trainee,
        failureChance: Int,
        inventory: Map<String, Int> = currentInventory,
    ): Boolean {
        if (summerCharmOverrideMinStatGain <= 0) {
            return false
        }
        val mainGain = selectedTrainingMainGain(trainingSelected, trainee)
        if (mainGain < summerCharmOverrideMinStatGain) {
            return false
        }
        if (hasAlternativeFailureMitigation(trainingSelected, inventory, forPoolOverrideCheck = true)) {
            return false
        }
        if (passesGoodLuckCharmTrainingChecks(trainingSelected, trainee, failureChance)) {
            return true
        }
        return buildFailureMitigationEnergyPlan(trainingSelected, trainee, inventory, failureChance, ignorePoolReserve = true) != null &&
            failureMitigationEnergyTargetTier(trainingSelected, inventory, failureChance, ignorePoolReserve = true).let {
                it == FailureMitigationEnergyTier.KALE_100 || it == FailureMitigationEnergyTier.VITA_65
            }
    }

    /**
     * Whether [itemName] may be spent from the pooled mitigation reserve this turn.
     * Royal Kale Juice at ≤20% energy bypasses the pool (last-resort recovery).
     */
    private fun canUseFailureMitigationPoolItem(
        itemName: String,
        inventory: Map<String, Int>,
        trainingSelected: StatName?,
        trainee: Trainee,
        failureChance: Int,
    ): Boolean {
        if (itemName !in failureMitigationPoolItems) {
            return true
        }
        if (itemName == "Royal Kale Juice" && trainee.energy <= 20) {
            return true
        }
        if (!isFailureMitigationPoolReservationPeriod()) {
            return true
        }
        val poolTotal = failureMitigationPoolTotal(inventory)
        if (poolTotal > failureMitigationPoolReserve) {
            return true
        }
        return trainingSelected != null &&
            isFailureMitigationPoolOverride(trainingSelected, trainee, failureChance, inventory) &&
            poolTotal > 0
    }

    /** True when [itemName] may be queued for high-failure training on [trainingSelected]. */
    private fun qualifiesForHighFailureEnergyItem(
        itemName: String,
        trainingSelected: StatName,
        inventory: Map<String, Int> = currentInventory,
        ignorePoolReserve: Boolean = false,
    ): Boolean {
        if (!enableEnergyItemForHighFailureTraining || bUsedCharmToday || !isFailureMitigationEnergyItem(itemName)) {
            return false
        }
        if (itemName == "Royal Kale Juice" && !kaleJuiceMoodGateMet(inventory)) {
            return false
        }
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        val mainGain = selectedTrainingMainGain(trainingSelected, trainee, includeActiveMegaphoneBonus = true)
        if (mainGain < energyItemMinMainStatGain) {
            return false
        }
        if (failureChance < 20 && !canBypassClimaxCharmFailureFloor()) {
            return false
        }
        if (!isHighTierFailureMitigationEnergyItem(itemName)) {
            val margin = energyItemFailureAboveMinimumFor(itemName)
            if (failureChance <= training.getMaximumFailureChance() + margin) {
                return false
            }
        }
        if (
            !ignorePoolReserve &&
                isHighTierFailureMitigationEnergyItem(itemName) &&
                !canUseFailureMitigationPoolItem(itemName, inventory, trainingSelected, trainee, failureChance)
        ) {
            return false
        }
        val available = (inventory[itemName] ?: 0) - reservedEnergyUnitsFor(itemName, inventory)
        return available > 0
    }

    /** Usable inventory units for an energy item after emergency reserve and failure-mitigation pool gates. */
    private fun usableFailureMitigationEnergyUnits(
        itemName: String,
        inventory: Map<String, Int>,
        trainingSelected: StatName,
        trainee: Trainee,
        failureChance: Int,
    ): Int {
        val available = (inventory[itemName] ?: 0) - reservedEnergyUnitsFor(itemName, inventory)
        if (available <= 0) return 0
        if (
            isHighTierFailureMitigationEnergyItem(itemName) &&
                !canUseFailureMitigationPoolItem(itemName, inventory, trainingSelected, trainee, failureChance)
        ) {
            return 0
        }
        return available
    }

    /**
     * Highest energy-mitigation tier this training qualifies for (ignores whether that exact item is in stock).
     * Kale (+100) and Vita 65 share the top tier; Vita 40 and Vita 20 follow.
     */
    private fun failureMitigationEnergyTargetTier(
        trainingSelected: StatName,
        inventory: Map<String, Int>,
        failureChance: Int,
        ignorePoolReserve: Boolean = false,
    ): FailureMitigationEnergyTier? {
        if (!enableEnergyItemForHighFailureTraining || bUsedCharmToday) return null
        if (qualifiesForHighFailureEnergyItem("Royal Kale Juice", trainingSelected, inventory, ignorePoolReserve)) {
            return FailureMitigationEnergyTier.KALE_100
        }
        if (qualifiesForHighFailureEnergyItem("Vita 65", trainingSelected, inventory, ignorePoolReserve)) {
            return FailureMitigationEnergyTier.VITA_65
        }
        if (qualifiesForHighFailureEnergyItem("Vita 40", trainingSelected, inventory, ignorePoolReserve)) {
            return FailureMitigationEnergyTier.VITA_40
        }
        if (qualifiesForHighFailureEnergyItem("Vita 20", trainingSelected, inventory, ignorePoolReserve)) {
            return FailureMitigationEnergyTier.VITA_20
        }
        return null
    }

    /**
     * Builds the energy items to queue for high-failure mitigation, substituting lower tiers when higher tiers are unavailable:
     * - Vita 40 slot → 2× Vita 20
     * - Vita 65 / +100 slot → Vita 65, else Vita 40 + Vita 20, else 3× Vita 20, else Royal Kale Juice (last resort)
     */
    private fun buildFailureMitigationEnergyPlan(
        trainingSelected: StatName,
        trainee: Trainee,
        inventory: Map<String, Int>,
        failureChance: Int,
        ignorePoolReserve: Boolean = false,
    ): List<String>? {
        val tier =
            failureMitigationEnergyTargetTier(trainingSelected, inventory, failureChance, ignorePoolReserve)
                ?: return null

        val stock =
            mutableMapOf(
                "Royal Kale Juice" to
                    usableFailureMitigationEnergyUnits("Royal Kale Juice", inventory, trainingSelected, trainee, failureChance),
                "Vita 65" to usableFailureMitigationEnergyUnits("Vita 65", inventory, trainingSelected, trainee, failureChance),
                "Vita 40" to usableFailureMitigationEnergyUnits("Vita 40", inventory, trainingSelected, trainee, failureChance),
                "Vita 20" to usableFailureMitigationEnergyUnits("Vita 20", inventory, trainingSelected, trainee, failureChance),
            )

        return when (tier) {
            FailureMitigationEnergyTier.KALE_100, FailureMitigationEnergyTier.VITA_65 ->
                buildFailureMitigationEnergyPlanForTopTier(stock)
            FailureMitigationEnergyTier.VITA_40 -> buildFailureMitigationEnergyPlanForVita40(stock)
            FailureMitigationEnergyTier.VITA_20 ->
                if (takeEnergyStock(stock, "Vita 20")) listOf("Vita 20") else null
        }
    }

    /** Vita 65 substitutes first; Royal Kale Juice (+100) only when no viable Vita combination remains. */
    private fun buildFailureMitigationEnergyPlanForTopTier(stock: MutableMap<String, Int>): List<String>? {
        buildFailureMitigationEnergyPlanForVita65(stock)?.let { return it }
        return if (takeEnergyStock(stock, "Royal Kale Juice")) listOf("Royal Kale Juice") else null
    }

    private fun takeEnergyStock(stock: MutableMap<String, Int>, name: String, count: Int = 1): Boolean {
        if ((stock[name] ?: 0) < count) return false
        stock[name] = (stock[name] ?: 0) - count
        return true
    }

    private fun buildFailureMitigationEnergyPlanForVita65(stock: MutableMap<String, Int>): List<String>? {
        if (takeEnergyStock(stock, "Vita 65")) return listOf("Vita 65")
        if ((stock["Vita 40"] ?: 0) >= 1 && (stock["Vita 20"] ?: 0) >= 1) {
            takeEnergyStock(stock, "Vita 40")
            takeEnergyStock(stock, "Vita 20")
            return listOf("Vita 40", "Vita 20")
        }
        if ((stock["Vita 65"] ?: 0) == 0 && (stock["Vita 40"] ?: 0) == 0 && (stock["Vita 20"] ?: 0) >= 3) {
            takeEnergyStock(stock, "Vita 20", 3)
            return List(3) { "Vita 20" }
        }
        return buildFailureMitigationEnergyPlanForVita40(stock)
    }

    private fun buildFailureMitigationEnergyPlanForVita40(stock: MutableMap<String, Int>): List<String>? {
        if (takeEnergyStock(stock, "Vita 40")) return listOf("Vita 40")
        if ((stock["Vita 20"] ?: 0) >= 2) {
            takeEnergyStock(stock, "Vita 20", 2)
            return List(2) { "Vita 20" }
        }
        return if (takeEnergyStock(stock, "Vita 20")) listOf("Vita 20") else null
    }

    private fun getFailureMitigationEnergyPlanForPass(
        trainingSelected: StatName,
        trainee: Trainee,
        inventory: Map<String, Int>,
        failureChance: Int,
    ): List<String>? {
        if (failureMitigationEnergyPlanForPass == null) {
            failureMitigationEnergyPlanForPass =
                buildFailureMitigationEnergyPlan(trainingSelected, trainee, inventory, failureChance)
            failureMitigationEnergyPlanForPass?.let { plan ->
                val label = plan.groupingBy { it }.eachCount().entries.joinToString(", ") { (name, count) -> "${count}× $name" }
                val tier = failureMitigationEnergyTargetTier(trainingSelected, inventory, failureChance)
                MessageLog.i(TAG, "[TRACKBLAZER] Failure mitigation energy plan ($tier): $label.")
            }
        }
        return failureMitigationEnergyPlanForPass
    }

    private fun failureMitigationEnergyPlanReason(
        plan: List<String>,
        trainingSelected: StatName,
        trainee: Trainee,
        failureChance: Int,
    ): String {
        val selectedMainGain = selectedTrainingMainGain(trainingSelected, trainee, includeActiveMegaphoneBonus = true)
        val tier = failureMitigationEnergyTargetTier(trainingSelected, currentInventory, failureChance)
        val totalGain = plan.sumOf { energyGains[it] ?: 0 }
        val substitute =
            when (tier) {
                FailureMitigationEnergyTier.VITA_40 ->
                    if (plan.all { it == "Vita 20" } && plan.size == 2) " (2× Vita 20 as Vita 40 substitute)" else ""
                FailureMitigationEnergyTier.VITA_65 ->
                    when {
                        plan.contains("Vita 65") -> ""
                        plan.contains("Vita 40") && plan.contains("Vita 20") -> " (Vita 40 + Vita 20 as Vita 65 substitute)"
                        plan.all { it == "Vita 20" } && plan.size == 3 -> " (3× Vita 20 as Vita 65 substitute)"
                        plan.contains("Royal Kale Juice") -> " (Royal Kale Juice last resort)"
                        else -> ""
                    }
                FailureMitigationEnergyTier.KALE_100 ->
                    when {
                        plan.contains("Royal Kale Juice") -> " (Royal Kale Juice last resort; no Vita combination available)"
                        plan.contains("Vita 40") && plan.contains("Vita 20") -> " (Vita 40 + Vita 20 preferred over Kale)"
                        plan.all { it == "Vita 20" } && plan.size == 3 -> " (3× Vita 20 preferred over Kale)"
                        else -> ""
                    }
                else -> ""
            }
        return "High-failure train: failure ($failureChance%) with main gain ($selectedMainGain) >= $energyItemMinMainStatGain; plan +$totalGain energy$substitute."
    }

    private fun highTierFailureMitigationEnergyQualifies(
        trainingSelected: StatName,
        inventory: Map<String, Int> = currentInventory,
    ): Boolean {
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        val tier = failureMitigationEnergyTargetTier(trainingSelected, inventory, failureChance) ?: return false
        if (tier != FailureMitigationEnergyTier.KALE_100 && tier != FailureMitigationEnergyTier.VITA_65) {
            return false
        }
        return buildFailureMitigationEnergyPlan(trainingSelected, trainee, inventory, failureChance) != null
    }

    private fun lowTierFailureMitigationEnergyQualifies(
        trainingSelected: StatName,
        inventory: Map<String, Int> = currentInventory,
    ): Boolean {
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        val tier = failureMitigationEnergyTargetTier(trainingSelected, inventory, failureChance) ?: return false
        if (tier != FailureMitigationEnergyTier.VITA_40 && tier != FailureMitigationEnergyTier.VITA_20) {
            return false
        }
        return buildFailureMitigationEnergyPlan(trainingSelected, trainee, inventory, failureChance) != null
    }

    private fun hasAlternativeFailureMitigation(
        trainingSelected: StatName,
        inventory: Map<String, Int> = currentInventory,
        forPoolOverrideCheck: Boolean = false,
    ): Boolean {
        if (!enableEnergyItemForHighFailureTraining) {
            return false
        }
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        if (forPoolOverrideCheck && isFailureMitigationPoolReservationPeriod()) {
            if (buildFailureMitigationEnergyPlan(trainingSelected, trainee, inventory, failureChance, ignorePoolReserve = true) != null) {
                return true
            }
            if (buildFailureMitigationEnergyPlan(trainingSelected, trainee, inventory, failureChance) != null) {
                return true
            }
            return false
        }
        if (lowTierFailureMitigationEnergyQualifies(trainingSelected, inventory)) {
            return true
        }
        return highTierFailureMitigationEnergyQualifies(trainingSelected, inventory)
    }

    /** Whether Good-Luck Charm may be used for failure mitigation (respects pooled mitigation reserve). */
    private fun charmEligibleForFailureMitigation(
        trainingSelected: StatName,
        trainee: Trainee,
        failureChance: Int,
        inventory: Map<String, Int>,
    ): Boolean {
        if ((inventory["Good-Luck Charm"] ?: 0) <= 0) {
            return false
        }
        if (!passesGoodLuckCharmTrainingChecks(trainingSelected, trainee, failureChance)) {
            return false
        }
        return canUseFailureMitigationPoolItem("Good-Luck Charm", inventory, trainingSelected, trainee, failureChance)
    }

    /** Returns true when high-failure energy mitigation is the active choice for [trainingSelected] this pass. */
    private fun shouldConsiderEnergyItemForHighFailureTrain(trainingSelected: StatName?): Boolean {
        if (trainingSelected == null || failureMitigationChoiceForPass != FailureMitigationChoice.ENERGY) {
            return false
        }
        return highTierFailureMitigationEnergyQualifies(trainingSelected) ||
            lowTierFailureMitigationEnergyQualifies(trainingSelected)
    }

    /** Ankle weight item name for [stat], or empty when Wit / null. */
    private fun ankleWeightItemForStat(stat: StatName?): String =
        when (stat) {
            StatName.SPEED -> "Speed Ankle Weights"
            StatName.STAMINA -> "Stamina Ankle Weights"
            StatName.POWER -> "Power Ankle Weights"
            StatName.GUTS -> "Guts Ankle Weights"
            else -> ""
        }

    private fun markStatSpecificTrainingItemQueued(stat: StatName) {
        statSpecificTrainingItemsQueuedFor = stat
    }

    /**
     * Opens the Training Items dialog after a post-recheck stat switch to queue megaphone/ankle weights for [stat] only.
     */
    private fun queueStatSpecificTrainingEffectItems(stat: StatName) {
        if (date.day < 13) {
            return
        }
        val neededWeight = ankleWeightItemForStat(stat)
        val hasMegaphones = hasMegaphoneAvailableForTraining(stat, trainee, currentInventory)
        val hasAnkleWeights =
            neededWeight.isNotEmpty() &&
                (currentInventory[neededWeight] ?: 0) > 0 &&
                isAnkleWeightEligibleForUse(neededWeight, stat, currentInventory)
        if (!hasMegaphones && !hasAnkleWeights) {
            return
        }
        MessageLog.i(
            TAG,
            "[TRACKBLAZER] Post-recheck stat switch to $stat: queuing megaphone/ankle weights for the new selection.",
        )
        bStatSpecificItemsOnlyPass = true
        try {
            if (shopList.openTrainingItemsDialog()) {
                manageInventoryItems(trainee, stat)
            }
        } finally {
            bStatSpecificItemsOnlyPass = false
        }
    }

    /** Highest failure margin among available low-tier energy items (excluding reserves). High-tier items ignore margins. */
    private fun maxAvailableLowTierEnergyFailureMargin(inventory: Map<String, Int>): Int =
        shopList.energyItemNames
            .filter { !isHighTierFailureMitigationEnergyItem(it) && ((inventory[it] ?: 0) - reservedEnergyUnitsFor(it, inventory)) > 0 }
            .maxOfOrNull { energyItemFailureAboveMinimumFor(it) } ?: 0

    /**
     * Main stat gain for [trainingSelected] from cached analysis (raw OCR by default).
     * When [includeActiveMegaphoneBonus] is true and a megaphone is active, includes the in-game training bonus
     * for energy-item failure-mitigation thresholds only — Good-Luck Charm thresholds always use raw gain.
     */
    private fun selectedTrainingMainGain(
        trainingSelected: StatName,
        trainee: Trainee? = null,
        includeActiveMegaphoneBonus: Boolean = false,
    ): Int {
        val baseGain =
            training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
        val traineeSnapshot = trainee ?: this.trainee
        if (!includeActiveMegaphoneBonus || traineeSnapshot.megaphoneTurnCounter <= 0) {
            return baseGain
        }
        return traineeSnapshot.mainStatGainWithActiveMegaphoneBonus(baseGain)
    }

    /** Re-resolves charm vs energy failure mitigation after a megaphone activates mid inventory pass. */
    private fun refreshFailureMitigationChoiceAfterMegaphone(
        trainingSelected: StatName,
        trainee: Trainee,
        inventory: Map<String, Int>,
    ) {
        if (!date.isSummer() || trainee.megaphoneTurnCounter <= 0 || !enableEnergyItemForHighFailureTraining) {
            return
        }
        failureMitigationChoiceForPass = resolveFailureMitigationChoice(trainingSelected, trainee, inventory)
        failureMitigationEnergyPlanForPass = null
        failureMitigationEnergyQueuedCounts.clear()
    }

    /** Whether Good-Luck Charm meets gain/mood/wit rules for the selected training (excluding energy-vs-charm priority). */
    private fun passesGoodLuckCharmTrainingChecks(
        trainingSelected: StatName,
        trainee: Trainee,
        failureChance: Int,
        mainStatGainOverride: Int? = null,
    ): Boolean {
        if (!isClimaxCharmTrainingActive() && !training.isLuckyCharmAllowedForSelection(trainingSelected)) {
            return false
        }
        if (failureChance < 20 && !canBypassClimaxCharmFailureFloor()) {
            return false
        }
        if (shouldConserveTrainingEffectItems(trainingSelected, trainee)) {
            return false
        }
        val mainGain = mainStatGainOverride ?: selectedTrainingMainGain(trainingSelected, trainee)
        val maxFail = training.getMaximumFailureChance()
        if (!isClimaxCharmTrainingActive() && failureChance > maxFail && mainGain < minCharmGain) {
            return false
        }
        return true
    }

    /**
     * Picks at most one failure-mitigation path per training turn when [enableEnergyItemForHighFailureTraining] is on:
     * Good-Luck Charm over +65/+100 energy when both qualify; otherwise energy (high-tier ignores failure margin) or charm fallback.
     */
    private fun resolveFailureMitigationChoice(
        trainingSelected: StatName,
        trainee: Trainee,
        inventory: Map<String, Int>,
    ): FailureMitigationChoice {
        if (!enableEnergyItemForHighFailureTraining || date.day < 13 || bUsedCharmToday) {
            return FailureMitigationChoice.NONE
        }

        val highTierQualifies = highTierFailureMitigationEnergyQualifies(trainingSelected, inventory)
        val lowTierQualifies = lowTierFailureMitigationEnergyQualifies(trainingSelected, inventory)
        val energyQualifies = highTierQualifies || lowTierQualifies
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        val charmMitigationQualifies = charmEligibleForFailureMitigation(trainingSelected, trainee, failureChance, inventory)

        if (!energyQualifies && !charmMitigationQualifies) {
            return FailureMitigationChoice.NONE
        }

        if (charmMitigationQualifies && highTierQualifies) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Failure mitigation: Good-Luck Charm preferred over +65/+100 energy (failure $failureChance%, both available).",
            )
            return FailureMitigationChoice.CHARM
        }

        if (highTierQualifies && !charmMitigationQualifies) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Failure mitigation: +65/+100 energy over Good-Luck Charm (charm unavailable or conserved; failure $failureChance%).",
            )
            return FailureMitigationChoice.ENERGY
        }

        if (energyQualifies) {
            val energyLabel =
                when {
                    highTierQualifies && lowTierQualifies -> "high-tier and low-tier energy items"
                    highTierQualifies -> "+65/+100 energy item"
                    else -> "energy item"
                }
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Failure mitigation: $energyLabel over Good-Luck Charm (failure $failureChance%).",
            )
            return FailureMitigationChoice.ENERGY
        }

        val maxMargin = maxAvailableLowTierEnergyFailureMargin(inventory)
        val maxFail = training.getMaximumFailureChance()
        val failureTooHighForLowTierEnergy = failureChance > maxFail + maxMargin
        if (failureTooHighForLowTierEnergy && charmMitigationQualifies) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Failure mitigation: Good-Luck Charm over energy (failure $failureChance% exceeds max+$maxMargin% for available low-tier energy items).",
            )
            return FailureMitigationChoice.CHARM
        }

        return FailureMitigationChoice.NONE
    }

    /** Whether Good-Luck Charm may be queued on this training-item pass. */
    private fun shouldQueueGoodLuckCharmForTraining(
        trainingSelected: StatName,
        trainee: Trainee,
        failureChance: Int,
        skipTrainingEffectItems: Boolean,
    ): Boolean {
        if (skipTrainingEffectItems || bUsedCharmToday) {
            return false
        }
        // High-failure energy mitigation wins over charm on the same training turn; low-energy vita does not block charm.
        if (failureMitigationChoiceForPass == FailureMitigationChoice.ENERGY) {
            return false
        }
        if ((currentInventory["Good-Luck Charm"] ?: 0) <= 0) {
            return false
        }
        if (!canUseFailureMitigationPoolItem("Good-Luck Charm", currentInventory, trainingSelected, trainee, failureChance)) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Conserving Good-Luck Charm: failure-mitigation pool at reserve floor ($failureMitigationPoolReserve total across Charm/Vita 65/Kale; senior pre-Finale 65–72; override needs main gain >= $summerCharmOverrideMinStatGain with no other mitigation).",
            )
            return false
        }
        if (
            isFailureMitigationPoolReservationPeriod() &&
                isFailureMitigationPoolOverride(trainingSelected, trainee, failureChance)
        ) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Failure-mitigation pool override: using Good-Luck Charm on $trainingSelected (main gain >= $summerCharmOverrideMinStatGain, no other mitigation available).",
            )
        }
        return passesGoodLuckCharmTrainingChecks(trainingSelected, trainee, failureChance)
    }

    /**
     * When Good-Luck Charm was chosen for mitigation but could not be queued, fall back to energy if available.
     */
    private fun reconcileFailureMitigationAfterSkippedCharm(
        trainingSelected: StatName,
        inventory: Map<String, Int>,
    ) {
        if (failureMitigationChoiceForPass != FailureMitigationChoice.CHARM || bUsedCharmToday) {
            return
        }
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        val energyQualifies =
            highTierFailureMitigationEnergyQualifies(trainingSelected, inventory) ||
                lowTierFailureMitigationEnergyQualifies(trainingSelected, inventory)
        if (energyQualifies) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Good-Luck Charm was not queued; falling back to energy failure mitigation for $trainingSelected.",
            )
            failureMitigationChoiceForPass = FailureMitigationChoice.ENERGY
        } else {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Good-Luck Charm was not queued and no energy mitigation is available for $trainingSelected.",
            )
            failureMitigationChoiceForPass = FailureMitigationChoice.NONE
        }
        failureMitigationEnergyPlanForPass = null
        failureMitigationEnergyQueuedCounts.clear()
    }

    /**
     * When CHARM was chosen but charm cannot queue (conservation, pool, mood), reconcile to ENERGY before the item scan.
     */
    private fun preReconcileCharmMitigationIfBlocked(
        trainingSelected: StatName,
        trainee: Trainee,
    ) {
        if (failureMitigationChoiceForPass != FailureMitigationChoice.CHARM) {
            return
        }
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        val skipTrainingEffectItems = shouldConserveTrainingEffectItems(trainingSelected, trainee)
        if (shouldQueueGoodLuckCharmForTraining(trainingSelected, trainee, failureChance, skipTrainingEffectItems)) {
            return
        }
        reconcileFailureMitigationAfterSkippedCharm(trainingSelected, currentInventory)
    }

    /**
     * Re-analyzes trainings after Good-Luck Charm and/or energy items were queued, then re-selects training.
     * Keeps pre-item Wit failure in mind for low-priority Wit when charm zeroed on-screen failure.
     */
    private fun recheckTrainingAfterItems(
        trainingSelected: StatName?,
        climaxCharmTraining: Boolean,
        preItemFailure: Map<StatName, Int>,
    ): StatName? {
        MessageLog.i(TAG, "[TRACKBLAZER] Re-evaluating training selection after item pass (reusing cached board analysis; no stat tab navigation).")
        val recheckArgs = buildTrainingAnalysisArgs().toMutableMap()
        recheckArgs["postTrainingItemsRecheck"] = true
        recheckArgs["preItemFailureSnapshot"] = preItemFailure
        recheckArgs["charmUsedThisTurn"] = bUsedCharmToday
        if (bUsedCharmToday) {
            recheckArgs["ignoreFailureChance"] = true
            recheckArgs["allowLowGainCharmAtZeroEnergy"] = true
        } else if (trainee.energy <= 0) {
            recheckArgs["allowLowGainCharmAtZeroEnergy"] = true
        }
        training.analyzeTrainings(recheckArgs)

        val energyMitigationUsed = bUsedEnergyItemThisPass && failureMitigationEnergyQueuedCounts.isNotEmpty()

        if (
            bUsedCharmToday &&
                trainingSelected != null &&
                training.trainingMap.containsKey(trainingSelected) &&
                training.isTrainingSelectionAllowedAfterItems(
                    trainingSelected,
                    preItemFailure,
                    charmUsed = true,
                    energyMitigationUsed = false,
                )
        ) {
            MessageLog.i(TAG, "[TRACKBLAZER] Post-item recheck keeping charm-backed selection: $trainingSelected.")
            return trainingSelected
        }

        var selected =
            if (climaxCharmTraining) {
                training.selectHighestNonMaxedStatForClimax()
            } else {
                training.recommendTraining()
            }

        if (
            selected != null &&
                !training.isTrainingSelectionAllowedAfterItems(
                    selected,
                    preItemFailure,
                    bUsedCharmToday,
                    energyMitigationUsed,
                )
        ) {
            MessageLog.i(TAG, "[TRACKBLAZER] Post-item recheck rejected $selected.")
            selected = null
        } else if (selected != null && selected != trainingSelected) {
            val original = trainingSelected
            if (
                original != null &&
                    statSpecificTrainingItemsQueuedFor == original &&
                    selected != original
            ) {
                if (
                    training.trainingMap.containsKey(original) &&
                        training.isTrainingSelectionAllowedAfterItems(
                            original,
                            preItemFailure,
                            bUsedCharmToday,
                            energyMitigationUsed,
                        )
                ) {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Post-item recheck keeping $original instead of $selected (megaphone/ankle weights already queued for $original).",
                    )
                    return original
                }
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Post-item recheck rejected stat switch from $original to $selected (stat-specific items locked to $original).",
                )
                selected = null
            } else {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Post-item recheck changed training selection from $trainingSelected to $selected.",
                )
                failureMitigationChoiceForPass = resolveFailureMitigationChoice(selected, trainee, currentInventory)
                preReconcileCharmMitigationIfBlocked(selected, trainee)
                failureMitigationEnergyPlanForPass = null
                failureMitigationEnergyQueuedCounts.clear()
                queueStatSpecificTrainingEffectItems(selected)
            }
        } else if (selected != null) {
            MessageLog.i(TAG, "[TRACKBLAZER] Post-item recheck confirmed training selection: $selected.")
        }

        return selected
    }

    /** Returns true when the Reset Whistle may be considered this turn. */
    private fun isResetWhistleUsageWindow(): Boolean {
        if (saveResetWhistlesForFinale && date.day in 73..75) {
            return true
        }
        if (saveResetWhistlesForFinale && date.day in 65..72) {
            return false
        }
        return if (saveResetWhistlesForSummer) {
            date.isSummer()
        } else {
            date.day in 37..40 || date.day > 60
        }
    }

    /** Summer (37–40, 61–64) and Finale (73–75): whistle priority / post-whistle recovery windows. */
    private fun isWhistlePriorityWindow(): Boolean = date.isSummer() || isClimaxPhase()

    private fun whistlePriorityStatList(): List<StatName> =
        if (date.isSummer()) {
            training.summerTrainingStatPriority
        } else {
            training.statPrioritization
        }

    /** Returns true when a top-3 priority training already has enough rainbows to train without a Reset Whistle. */
    private fun shouldSkipWhistleForRainbowPriority(): Boolean =
        isWhistlePriorityWindow() &&
            whistlePriorityMinRainbow > 0 &&
            training.hasTrainableTopPriorityTrainingWithRainbows(whistlePriorityStatList(), whistlePriorityMinRainbow)

    /**
     * Returns true when a post-whistle pick should be abandoned for energy recovery.
     * Uses pre-charm failure values from cached analysis.
     */
    private fun shouldRejectWhistlePostShuffleResult(trainingSelected: StatName?): Boolean {
        if (
            trainingSelected == null ||
                whistlePostShuffleMinFailure <= 0 ||
                whistlePostShuffleMinMainGain <= 0 ||
                !isWhistlePriorityWindow()
        ) {
            return false
        }
        val candidate = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected } ?: return false
        val failure = candidate.failureChance
        val mainGain = candidate.statGains[trainingSelected] ?: 0
        if (failure >= whistlePostShuffleMinFailure && mainGain < whistlePostShuffleMinMainGain) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Rejecting post-whistle pick $trainingSelected: failure ($failure%) >= $whistlePostShuffleMinFailure% and main gain ($mainGain) < $whistlePostShuffleMinMainGain. Recovering energy instead (before Good-Luck Charm).",
            )
            return true
        }
        return false
    }

    /**
     * Attempts a Reset Whistle reshuffle when no training is selected. Item usage (including Good-Luck Charm) must run after this.
     *
     * @return Updated training selection, or null if still none.
     */
    private fun tryApplyResetWhistle(trainingSelected: StatName?, climaxCharmTraining: Boolean): StatName? {
        var selected = trainingSelected

        if (!isResetWhistleUsageWindow() || bUsedWhistleToday || selected != null || bIsIrregularTraining || training.needsEnergyRecovery) {
            if (training.needsEnergyRecovery && selected == null) {
                MessageLog.i(TAG, "[TRACKBLAZER] Skipping Reset Whistle as energy recovery is needed, not a training re-roll.")
            }
            return selected
        }

        if (shouldSkipWhistleForRainbowPriority()) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Skipping Reset Whistle: a top-3 priority training has >= $whistlePriorityMinRainbow rainbow(s) with qualifying orange friendship on screen.",
            )
            return selected
        }

        val hasWhistle = (currentInventory["Reset Whistle"] ?: 0) > 0

        val whistleGateBlocks =
            if (trainee.mood < Mood.NORMAL) {
                val blacklistSize = training.blacklist.filterNotNull().size
                val requiredLowGainCount = (3 - blacklistSize).coerceAtLeast(1)
                val results = training.cachedAnalysisResults ?: emptyList()
                val nonBlacklisted = results.filter { it.name !in training.blacklist }
                val lowGainCount = nonBlacklisted.count { (it.statGains[it.name] ?: 0) < lowMainStatGainItemFloor }
                val blocks = lowGainCount >= requiredLowGainCount
                if (blocks) {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Refusing Reset Whistle reshuffle: mood=${trainee.mood}, $lowGainCount of ${nonBlacklisted.size} non-blacklisted trainings have main gain below floor ($lowMainStatGainItemFloor). Reshuffling won't recover from the mood penalty.",
                    )
                }
                blocks
            } else {
                false
            }

        if (whistleGateBlocks) {
            return selected
        }

        if (!hasWhistle) {
            MessageLog.i(TAG, "[TRACKBLAZER] No suitable training found and no Reset Whistles in cached inventory or all are disabled.")
            return selected
        }

        if (whistleForcesTraining) {
            val preForced = training.recommendTraining(forceSelection = true)
            when {
                preForced != null && isViableForcedTrainingPick(preForced) -> {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Viable forced pick on current board ($preForced). Skipping Reset Whistle.",
                    )
                    return preForced
                }
                preForced != null -> {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping Reset Whistle: forced pick $preForced would fail mitigation on current board.",
                    )
                    return null
                }
            }
        }

        MessageLog.i(TAG, "[TRACKBLAZER] No suitable training found. Using Reset Whistle.")
        if (!shopList.openTrainingItemsDialog()) {
            return selected
        }

        if (shopList.useSpecificItems(listOf("Reset Whistle"), reason = "No suitable training found.").isEmpty()) {
            MessageLog.i(TAG, "[TRACKBLAZER] No Reset Whistles found in inventory.")
            ButtonClose.click(game.imageUtils)
            game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)
            return selected
        }

        confirmAndCloseItemDialog(1)
        useInventoryItem("Reset Whistle")
        bUsedWhistleToday = true

        MessageLog.i(TAG, "[TRACKBLAZER] Re-analyzing trainings after Reset Whistle (before Good-Luck Charm).")
        training.clearAnalysisCache()
        val postWhistleArgs = buildTrainingAnalysisArgs().toMutableMap()
        postWhistleArgs["forceFullStatNavigation"] = true
        training.analyzeTrainings(postWhistleArgs)
        selected =
            if (climaxCharmTraining) {
                training.selectHighestNonMaxedStatForClimax()
            } else {
                training.recommendTraining(forceSelection = whistleForcesTraining)
            }

        if (shouldRejectWhistlePostShuffleResult(selected)) {
            return null
        }

        when {
            selected == null ->
                MessageLog.i(TAG, "[TRACKBLAZER] Reset Whistle re-analysis returned no training; nothing to execute.")
            training.lastSelectionSource == SelectionSource.FORCED_FROM_SKIPPED -> {
                val (forcedFail, forcedBaseGain) = trainingFailureAndMainGain(selected)
                if (!isViableForcedTrainingPick(selected)) {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping Whistle force-pick: $selected at $forcedFail% fail without viable mitigation. Falling back to recovery.",
                    )
                    selected = null
                } else {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Reset Whistle re-analysis still rejected all trainings; Whistle Forces Training is enabled, " +
                            "so executing forced pick: $selected. Megaphone (if available) will be applied after Good-Luck Charm evaluation.",
                    )
                }
            }
            training.lastSelectionSource != SelectionSource.ANALYSIS ->
                MessageLog.i(TAG, "[TRACKBLAZER] Reset Whistle re-analysis used fallback (${training.lastSelectionSource}): $selected.")
            else ->
                MessageLog.i(TAG, "[TRACKBLAZER] Reset Whistle re-analysis selected: $selected.")
        }

        return selected
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Debug Tests

    /**
     * Starts debug tests for the Trackblazer campaign.
     *
     * @return True if any tests were run, false otherwise.
     */
    override fun startTests(): Boolean {
        var bDidAnyTestsRun = super.startTests()

        val fnMap: Map<String, () -> Unit> =
            mapOf(
                "debugMode_startTrackblazerRaceSelectionTest" to ::startTrackblazerRaceSelectionTest,
                "debugMode_startTrackblazerInventorySyncTest" to ::startTrackblazerInventorySyncTest,
                "debugMode_startTrackblazerBuyItemsTest" to ::startTrackblazerBuyItemsTest,
            )

        for ((settingName, fn) in fnMap) {
            if (SettingsHelper.getBooleanSetting("debug", settingName)) {
                fn()
                bDidAnyTestsRun = true
            }
        }

        return bDidAnyTestsRun
    }

    /**
     * Debug test for Trackblazer's race selection logic.
     */
    fun startTrackblazerRaceSelectionTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning Trackblazer race selection test.")

        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // If on Main Screen, navigate to the Race List screen first.
        if (checkMainScreen()) {
            MessageLog.i(TAG, "[TEST] Currently on Main Screen. Navigating to Race List...")
            if (!ButtonRaces.click(game.imageUtils, sourceBitmap = sourceBitmap) && !ButtonRaceDayRace.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                MessageLog.e(TAG, "[ERROR] startTrackblazerRaceSelectionTest:: Failed to click Races button.")
                return
            }
            game.wait(1.0)

            // Handle any consecutive race warning dialogs that might pop up.
            handleDialogs(args = mapOf("overrideIgnoreConsecutiveRaceWarning" to true))
        }

        // Now check if we are on the Race List screen.
        if (ButtonRaceListFullStats.check(game.imageUtils)) {
            // Update the date first for racing logic.
            updateDate(isOnMainScreen = false)

            MessageLog.i(TAG, "[TEST] Currently on Race List screen. Calling findSuitableRace($consecutiveRaceCount)...")
            val result = findSuitableRace(consecutiveRaceCount, preferredDistances, preferredSurfaces)

            if (result != null) {
                val (point, raceData) = result
                MessageLog.i(TAG, "[TEST] Selection Finalized: ${raceData.name} (${raceData.grade}) at (${point.x}, ${point.y}).")
            } else {
                MessageLog.i(TAG, "[TEST] findSuitableRace returned null. No suitable races found.")
            }
        } else {
            MessageLog.e(TAG, "[ERROR] startTrackblazerRaceSelectionTest:: Not on Main Screen or Race List screen. Ending test.")
        }
    }

    /**
     * Debug test for Trackblazer's inventory sync logic.
     */
    fun startTrackblazerInventorySyncTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning Trackblazer inventory sync test.")

        // If on Main Screen, open Training Items.
        if (checkMainScreen()) {
            MessageLog.i(TAG, "[TEST] Currently on Main Screen. Opening Training Items...")
            if (shopList.openTrainingItemsDialog()) {
                MessageLog.i(TAG, "[TEST] Training Items dialog opened. Calling manageInventoryItems with bDryRun = true and bQuickUseOnly = true...")
                manageInventoryItems(bQuickUseOnly = true, bDryRun = true)
            } else {
                MessageLog.e(TAG, "[ERROR] startTrackblazerInventorySyncTest:: Failed to open Training Items dialog.")
            }
        } else if (ButtonClose.check(game.imageUtils)) {
            // Assume we are already in some dialog, possibly training items.
            MessageLog.i(TAG, "[TEST] Close button detected. Assuming Training Items dialog is open. Calling manageInventoryItems...")
            manageInventoryItems(bQuickUseOnly = true, bDryRun = true)
        } else {
            MessageLog.e(TAG, "[ERROR] startTrackblazerInventorySyncTest:: Not on Main Screen or in a dialog. Ending test.")
        }
    }

    /**
     * Debug test for Trackblazer's buying process logic.
     */
    fun startTrackblazerBuyItemsTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning Trackblazer buy items test.")

        // If on Main Screen, open the Shop.
        if (checkMainScreen()) {
            MessageLog.i(TAG, "[TEST] Currently on Main Screen. Opening Shop...")
            openShop()
            game.wait(1.0)
        }

        // Check if we are in the Shop.
        if (ButtonTrainingItems.check(game.imageUtils)) {
            MessageLog.i(TAG, "[TEST] Shop detected. Calling buyItems with bDryRun = true...")
            buyItems(bDryRun = true)
        } else {
            MessageLog.e(TAG, "[ERROR] startTrackblazerBuyItemsTest:: Shop not detected. Ending test.")
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    override fun handleDialogs(dialog: DialogInterface?, args: Map<String, Any>): DialogHandlerResult {
        val result: DialogHandlerResult = super.handleDialogs(dialog, args)
        if (result !is DialogHandlerResult.Unhandled) {
            return result
        }

        when (result.dialog.name) {
            "exchange_complete" -> {
                val boughtItems = args["itemsBought"] as? List<String> ?: emptyList()
                val quickUseItemsOnly = boughtItems.filter { shopList.shopItems[it]?.isQuickUsage == true }

                if (quickUseItemsOnly.isNotEmpty()) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Quick-use items were purchased. Navigating and queuing for usage...")
                    val usedItems = shopList.useSpecificItems(quickUseItemsOnly, bUseAll = true, reason = "Quick-use after purchase.")
                    usedItems.forEach { useInventoryItem(it.first) }

                    // This clicks the "Confirm Use" button on the "Exchange Complete" dialog.
                    if (result.dialog.ok(game.imageUtils)) {
                        game.wait(0.5)
                        // This clicks the "Use Training Items" button on the "Confirm Use" dialog.
                        handleDialogs(DialogConfirmUse)
                        // This clicks the "Close" button on the "Exchange Complete" dialog after handling quick-use.
                        result.dialog.close(game.imageUtils)
                    } else {
                        // Fallback to closing the dialog if "Confirm Use" button was not found.
                        MessageLog.i(TAG, "[TRACKBLAZER] Quick-use items were identified but the \"Confirm Use\" button was not found. Closing dialog...")
                        result.dialog.close(game.imageUtils)
                    }
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] No quick-use items were purchased. Closing dialog...")
                    result.dialog.close(game.imageUtils)
                }
            }

            "confirm_use" -> {
                result.dialog.ok(game.imageUtils)
            }

            "shop" -> {
                // Once it gets to Junior Year Early July, the shop will be unlocked for use.
                // But the date update has not happened yet, so we need to check for the previous date instead.
                if (date.year == DateYear.JUNIOR && date.month == DateMonth.JUNE && date.phase == DatePhase.LATE) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop unlocked! Initiating the first time buying process.")
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop discount detected! Initiating buying process.")
                }

                if (result.dialog.ok(game.imageUtils)) {
                    game.wait(game.dialogWaitDelay)

                    // Clear the shop check flag and counter as the shop is already being handled.
                    bShouldCheckShop = false
                    shopCheckCounter = 0
                    bInitialShopCheckPerformed = true

                    game.wait(0.5)
                    buyItems()
                    return DialogHandlerResult.Handled(result.dialog)
                } else {
                    MessageLog.e(TAG, "[ERROR] handleDialogs:: Failed to click the OK button on the Shop dialog.")
                    return DialogHandlerResult.Unhandled(result.dialog)
                }
            }

            "training_items" -> {
                MessageLog.i(TAG, "[TRACKBLAZER] Training Items dialog detected. Closing it as it is not currently being handled by a specific process.")
                result.dialog.close(game.imageUtils)
            }

            else -> {
                Log.w(TAG, "[WARN] handleDialogs:: Unknown dialog \"${result.dialog.name}\" detected so it will not be handled.")
                return DialogHandlerResult.Unhandled(result.dialog)
            }
        }

        game.wait(0.5)
        return DialogHandlerResult.Handled(result.dialog)
    }

    override fun handleTrainingEvent() {
        if (!tutorialDisabled) {
            tutorialDisabled =
                if (IconUnityCupTutorialHeader.check(game.imageUtils)) {
                    // If the tutorial is detected, select the second option to close it.
                    MessageLog.i(TAG, "[TRACKBLAZER] Detected tutorial for Trackblazer. Closing it now.")
                    val trainingOptionLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
                    if (trainingOptionLocations.size >= 2) {
                        game.tap(trainingOptionLocations[1].x, trainingOptionLocations[1].y, IconTrainingEventHorseshoe.template.path)
                        true
                    } else {
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Could not find training options to dismiss tutorial.")
                        false
                    }
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Tutorial must have already been dismissed.")
                    super.handleTrainingEvent()
                    true
                }
        } else {
            super.handleTrainingEvent()
        }
    }

    override fun recoverEnergy(sourceBitmap: Bitmap?): Boolean {
        MessageLog.i(TAG, "[TRACKBLAZER] Resetting $consecutiveRaceCount consecutive race counts due to energy recovery.")
        consecutiveRaceCount = 0
        return super.recoverEnergy(sourceBitmap)
    }

    override fun recoverMood(sourceBitmap: Bitmap?, targetMood: Mood): Boolean {
        MessageLog.i(TAG, "[TRACKBLAZER] Resetting $consecutiveRaceCount consecutive race counts due to mood recovery.")
        consecutiveRaceCount = 0
        return super.recoverMood(sourceBitmap, targetMood)
    }

    override fun hasPostRacePopups(): Boolean = true

    override fun shouldBypassSmartRacing(): Boolean = true

    override fun getMaxRetriesPerRace(): Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerMaxRetriesPerRace", 1)

    override fun getMaxRaceRetries(): Int = 5

    override fun getRetryEligibleGrades(): List<RaceGrade> =
        try {
            val gradesString = SettingsHelper.getStringSetting("scenarioOverrides", "trackblazerRetryRacesBeforeFinalGrades", "[\"G1\",\"G2\",\"G3\"]")
            val jsonArray = JSONArray(gradesString)
            (0 until jsonArray.length()).mapNotNull { RaceGrade.fromName(jsonArray.getString(it)) }
        } catch (e: Exception) {
            listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3)
        }

    /**
     * Searches the race list for a suitable Trackblazer race based on double-star predictions and grade criteria.
     *
     * Junior Year: G1/G2/G3 with double predictions. Classic/Senior: Priority racing, but if consecutive race count >= 3, only G1/G2/G3.
     *
     * @param consecutiveRaceCount Current number of consecutive races performed.
     * @param preferredDistances Optional list of preferred track distances for prioritization.
     * @param preferredSurfaces Optional list of preferred track surfaces for prioritization.
     * @return Pair of the best suitable race's location and [Racing.RaceData], or null if none found.
     */
    private fun findSuitableRace(
        consecutiveRaceCount: Int,
        preferredDistances: List<TrackDistance> = emptyList(),
        preferredSurfaces: List<TrackSurface> = emptyList(),
    ): Pair<Point, Racing.RaceData>? {
        val sb = StringBuilder()
        sb.appendLine("\n========== Trackblazer Race Selection Analysis ==========")
        sb.appendLine("Current Date: $date")
        sb.appendLine("Consecutive Race Count: $consecutiveRaceCount")

        data class Candidate(val point: Point, val race: Racing.RaceData, val detectedName: String, val isRival: Boolean)

        val allSuitableRaces = mutableListOf<Candidate>()

        // Peek the Solver's planned race so the scrollList scan can short-circuit as soon as it surfaces.
        val solverPlannedKey =
            if (racing.enableSmartRaceSolver && racing.enableFarmingFans && !racing.enableForceRacing) {
                SmartRaceSolverIntegration.peekRaceKeyForTurn(currentTurn = date.day, scenario = game.scenario)
            } else {
                null
            }
        var solverMatchedCandidate: Candidate? = null

        val scrollList = ScrollList.create(game)
        if (scrollList != null) {
            MessageLog.i(TAG, "[RACE] Scanning the whole race list for suitable races...")
            val entryRaceNamesMap = mutableMapOf<Int, List<String>>()
            scrollList.process(
                keyExtractor = { entry ->
                    val doubleStarPredictions = IconRaceListPredictionDoubleStar.findAll(game.imageUtils, sourceBitmap = entry.bitmap, region = intArrayOf(0, 0, 0, 0))
                    val names =
                        doubleStarPredictions.map { predictionLocation ->
                            val screenPoint = Point(entry.bbox.x + predictionLocation.x, entry.bbox.y + predictionLocation.y)
                            game.imageUtils.extractRaceName(screenPoint)
                        }
                    if (names.isNotEmpty()) entryRaceNamesMap[entry.index] = names
                    if (names.isEmpty()) null else names.joinToString("|")
                },
            ) { _, entry ->
                val doubleStarPredictions = IconRaceListPredictionDoubleStar.findAll(game.imageUtils, sourceBitmap = entry.bitmap, region = intArrayOf(0, 0, 0, 0))
                val cachedNames = entryRaceNamesMap[entry.index] ?: emptyList()
                for ((idx, predictionLocation) in doubleStarPredictions.withIndex()) {
                    val rivalBitmap =
                        game.imageUtils.createSafeBitmap(
                            entry.bitmap,
                            game.imageUtils.relX(predictionLocation.x, -165),
                            game.imageUtils.relY(predictionLocation.y, -165),
                            game.imageUtils.relWidth(340),
                            game.imageUtils.relHeight(80),
                            "findSuitableRace rival scan",
                        )
                    val rivalFound =
                        rivalBitmap != null &&
                            LabelRivalRacer.check(game.imageUtils, region = intArrayOf(0, 0, 0, 0), sourceBitmap = rivalBitmap)

                    if (game.debugMode) {
                        game.imageUtils.saveBitmap(rivalBitmap, "rival_scan_${predictionLocation.x}_${predictionLocation.y}")
                    }

                    val screenPoint = Point(entry.bbox.x + predictionLocation.x, entry.bbox.y + predictionLocation.y)
                    val detectedName = if (idx < cachedNames.size) cachedNames[idx] else game.imageUtils.extractRaceName(screenPoint)
                    val matches = racing.lookupRaceInDatabase(date.day, detectedName)

                    for (race in matches) {
                        var isSuitable = false
                        val reasons = mutableListOf<String>()
                        race.isRival = rivalFound

                        if (date.year == DateYear.JUNIOR) {
                            if (listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3).contains(race.grade)) {
                                isSuitable = true
                            } else {
                                reasons.add("Junior Year: Grade ${race.grade} is not G1, G2, or G3")
                            }
                        } else {
                            if (consecutiveRaceCount >= 3) {
                                if (listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3).contains(race.grade)) {
                                    isSuitable = true
                                } else {
                                    reasons.add("Consecutive races >= 3: Grade ${race.grade} is not G1, G2, or G3")
                                }
                            } else {
                                isSuitable = true
                            }
                        }

                        if (isSuitable) {
                            val candidate = Candidate(screenPoint, race, detectedName, rivalFound)
                            allSuitableRaces.add(candidate)
                            sb.appendLine("\n- Found Suitable Race: \"${race.name}\" (${race.grade}) Rival: $rivalFound")
                            if (solverPlannedKey != null && SmartRaceSolverIntegration.isRaceKeyMatch(race, solverPlannedKey)) {
                                solverMatchedCandidate = candidate
                            }
                        } else {
                            sb.appendLine("\n- Ignored Race: \"${race.name}\" (${race.grade}). Reason: ${reasons.joinToString(", ")}")
                        }
                    }
                }
                solverMatchedCandidate != null
            }
        } else {
            MessageLog.w(TAG, "[WARN] findSuitableRace:: Failed to create ScrollList. Falling back to single-page detection.")
            val doubleStarPredictions = IconRaceListPredictionDoubleStar.findAll(game.imageUtils)
            val sourceBitmap = game.imageUtils.getSourceBitmap()
            for (location in doubleStarPredictions) {
                val rivalBitmap =
                    game.imageUtils.createSafeBitmap(
                        sourceBitmap,
                        game.imageUtils.relX(location.x, -165),
                        game.imageUtils.relY(location.y, -165),
                        game.imageUtils.relWidth(320),
                        game.imageUtils.relHeight(80),
                        "findSuitableRace rival fallback",
                    )
                val rivalFound =
                    rivalBitmap != null &&
                        LabelRivalRacer.check(game.imageUtils, region = intArrayOf(0, 0, 0, 0), sourceBitmap = rivalBitmap)

                if (game.debugMode) {
                    game.imageUtils.saveBitmap(rivalBitmap, "rival_fallback_${location.x}_${location.y}")
                }

                val detectedName = game.imageUtils.extractRaceName(location)
                val matches = racing.lookupRaceInDatabase(date.day, detectedName)

                for (race in matches) {
                    var isSuitable = false
                    val reasons = mutableListOf<String>()
                    race.isRival = rivalFound

                    if (date.year == DateYear.JUNIOR) {
                        if (listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3).contains(race.grade)) {
                            isSuitable = true
                        } else {
                            reasons.add("Junior Year: Grade ${race.grade} is not G1, G2, or G3")
                        }
                    } else {
                        if (consecutiveRaceCount >= 3) {
                            if (listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3).contains(race.grade)) {
                                isSuitable = true
                            } else {
                                reasons.add("Consecutive races >= 3: Grade ${race.grade} is not G1, G2, or G3")
                            }
                        } else {
                            isSuitable = true
                        }
                    }

                    if (isSuitable) {
                        allSuitableRaces.add(Candidate(location, race, detectedName, rivalFound))
                    }
                }
            }
        }

        if (allSuitableRaces.isEmpty()) {
            sb.appendLine("\nSummary: No suitable races found after analysis.")
            sb.appendLine("================================================")
            MessageLog.v(TAG, sb.toString())
            return null
        }

        // If the Solver's planned race surfaced during the scan, short-circuit straight to it — its on-screen point is still current because the scrollList stopped on that entry.
        if (solverMatchedCandidate != null) {
            val match = solverMatchedCandidate!!
            sb.appendLine("\nSelected Race: ${match.race.name} (${match.race.grade}) Rival: ${match.isRival} [Smart Race Solver pick]")
            sb.appendLine("================================================")
            MessageLog.v(TAG, sb.toString())
            MessageLog.i(TAG, "[RACE] Smart Race Solver match \"${match.race.name}\" found during scan. Skipping the rest of the scan.")
            SmartRaceSolverIntegration.markPendingRace(
                raceKey = match.race.name,
                raceName = match.race.name,
                classYear = date.year.name,
                turnNumber = date.day,
            )
            return match.point to match.race
        }

        val gradePriority =
            mapOf(
                RaceGrade.G1 to 1,
                RaceGrade.G2 to 2,
                RaceGrade.G3 to 3,
                RaceGrade.OP to 4,
                RaceGrade.PRE_OP to 5,
            )

        val sortedRaces =
            allSuitableRaces.sortedWith(
                compareByDescending<Candidate> { it.isRival }
                    .thenByDescending {
                        val distanceMatch = preferredDistances.isEmpty() || it.race.trackDistance in preferredDistances
                        val surfaceMatch = preferredSurfaces.isEmpty() || it.race.trackSurface in preferredSurfaces
                        distanceMatch && surfaceMatch
                    }
                    .thenBy { gradePriority[it.race.grade] ?: 99 },
            )
        val winner = sortedRaces.first()

        val winnerDistanceMatch = preferredDistances.isEmpty() || winner.race.trackDistance in preferredDistances
        val winnerSurfaceMatch = preferredSurfaces.isEmpty() || winner.race.trackSurface in preferredSurfaces
        sb.appendLine("\nSelected Race: ${winner.race.name} (${winner.race.grade}) Rival: ${winner.isRival}")
        sb.appendLine("Distance: ${winner.race.trackDistance}, Surface: ${winner.race.trackSurface}, Preference Match: ${winnerDistanceMatch && winnerSurfaceMatch}")
        sb.appendLine("================================================")
        MessageLog.v(TAG, sb.toString())

        return if (scrollList != null) {
            MessageLog.i(TAG, "[RACE] Scrolling to selected race: \"${winner.race.name}\"...")
            var finalWinnerPoint: Point? = null
            scrollList.process { _, entry ->
                val stars = IconRaceListPredictionDoubleStar.findAll(game.imageUtils, sourceBitmap = entry.bitmap, region = intArrayOf(0, 0, 0, 0))
                for (starLoc in stars) {
                    val screenPoint = Point(entry.bbox.x + starLoc.x, entry.bbox.y + starLoc.y)
                    val name = game.imageUtils.extractRaceName(screenPoint)
                    val matches = racing.lookupRaceInDatabase(date.day, name)

                    if (matches.any { it.name == winner.race.name }) {
                        if (game.debugMode) {
                            MessageLog.d(TAG, "[DEBUG] Found winner \"${winner.race.name}\" (Detected: \"$name\", Target: \"${winner.detectedName}\")")
                        }
                        finalWinnerPoint = screenPoint
                        return@process true
                    }
                }
                false
            }
            if (finalWinnerPoint != null) finalWinnerPoint to winner.race else null
        } else {
            winner.point to winner.race
        }
    }

    override fun onConsecutiveRaceWarningDetected(dialog: DialogInterface, args: Map<String, Any>) {
        val okButtonLocation: Point? = ButtonOk.find(game.imageUtils).first

        if (okButtonLocation != null) {
            val ocrText =
                game.imageUtils.performOCRFromReference(
                    okButtonLocation,
                    offsetX = -560,
                    offsetY = -525,
                    width = game.imageUtils.relWidth(690),
                    height = game.imageUtils.relHeight(50),
                    useThreshold = true,
                    useGrayscale = true,
                    scale = 2.0,
                    ocrEngine = "mlkit",
                    debugName = "TrackblazerConsecutiveRaceOCR",
                )

            Log.d(TAG, "[DEBUG] onConsecutiveRaceWarningDetected:: OCR text from consecutive warning: \"$ocrText\"")

            // Regex: This will put you at ([0-9]+) consecutive races.
            val match = Regex("""([0-9]+)""").find(ocrText)
            val ocrCount = match?.groups?.get(1)?.value?.toInt() ?: -1

            if (ocrCount != -1) {
                Log.d(TAG, "[DEBUG] onConsecutiveRaceWarningDetected:: OCR detected a count of $ocrCount consecutive races.")

                // Trust OCR as the primary source of truth if it successfully parses a number.
                consecutiveRaceCount = ocrCount
                counterUpdatedByOCR = true
            } else {
                MessageLog.w(TAG, "[WARN] onConsecutiveRaceWarningDetected:: Failed to parse consecutive race count from OCR. Counter will be incremented after race.")
            }
        } else {
            MessageLog.e(TAG, "[ERROR] onConsecutiveRaceWarningDetected:: Failed to find ButtonOk on consecutive race warning screen. Counter will be incremented after race.")
        }

        MessageLog.i(TAG, "[TRACKBLAZER] Current consecutive race count: $consecutiveRaceCount.")
    }

    override fun shouldAllowConsecutiveRace(args: Map<String, Any>): Boolean {
        // Block racing at 0-1 energy with 3+ consecutive races to avoid -30 stat penalty.
        if (trainee.energy <= 1 && consecutiveRaceCount >= 3) {
            if (racing.ignoreLowEnergyRacingBlock) {
                MessageLog.w(
                    TAG,
                    "[WARN] shouldAllowConsecutiveRace:: Energy critically low (${trainee.energy}%) with $consecutiveRaceCount consecutive races, but ignoreLowEnergyRacingBlock is enabled. Allowing race.",
                )
            } else {
                val conserveItem = energyItemConservationOrder.firstOrNull { (currentInventory[it] ?: 0) > 0 }
                if (conserveItem != null) {
                    MessageLog.w(
                        TAG,
                        "[WARN] shouldAllowConsecutiveRace:: Energy critically low but $conserveItem exists in inventory. This should have been used in decideNextAction(). Blocking race as safety net.",
                    )
                } else {
                    MessageLog.w(
                        TAG,
                        "[WARN] shouldAllowConsecutiveRace:: Energy is critically low (${trainee.energy}%) with $consecutiveRaceCount consecutive races. Blocking to avoid possible -30 stat penalty.",
                    )
                }
                racing.encounteredRacingPopup = false
                return false
            }
        }

        // A -30 stat penalty can apply starting from 3 consecutive races.
        if (consecutiveRaceCount >= 3) {
            MessageLog.w(TAG, "[WARN] shouldAllowConsecutiveRace:: Current consecutive race count is $consecutiveRaceCount. Note that a -30 stat penalty can apply starting from 3 consecutive races!")
        }

        // Edge case: if there is only 1 turn left before a mandatory race, we can safely race
        // even if it would exceed the limit.
        val turnsRemaining = game.imageUtils.determineTurnsRemainingBeforeNextGoal()
        val onlyOneTurnLeft = turnsRemaining == 1

        // Late December is the last racing opportunity before a mandatory goal race, so ignore the limit.
        val isLateDecember = date.month == DateMonth.DECEMBER && date.phase == DatePhase.LATE

        if (consecutiveRaceCount < (consecutiveRacesLimit + 1) || onlyOneTurnLeft || isLateDecember) {
            if (isLateDecember && consecutiveRaceCount >= (consecutiveRacesLimit + 1)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Consecutive race count $consecutiveRaceCount >= ${consecutiveRacesLimit + 1}, but it is Late December. Ignoring limit to maximize races before mandatory goal race.",
                )
            } else if (onlyOneTurnLeft && consecutiveRaceCount >= (consecutiveRacesLimit + 1)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Consecutive race count $consecutiveRaceCount >= ${consecutiveRacesLimit + 1}, but only 1 turn remains before mandatory race. Racing is safe. Continuing.",
                )
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] Consecutive race count $consecutiveRaceCount < ${consecutiveRacesLimit + 1}. Continuing.")
            }
            return true
        } else {
            MessageLog.w(TAG, "[WARN] shouldAllowConsecutiveRace:: Consecutive race count $consecutiveRaceCount >= ${consecutiveRacesLimit + 1}. Aborting racing.")
            racing.encounteredRacingPopup = false
            return false
        }
    }

    override fun shouldRetryRace(dialog: DialogInterface, args: Map<String, Any>): Boolean {
        if (racing.lastRaceGrade != null && racing.retryEligibleGrades.contains(racing.lastRaceGrade) && racing.raceRetries >= 0) {
            if (racing.lastRaceIsRival && !racing.bRetriedCurrentRace) {
                MessageLog.i(TAG, "[TRACKBLAZER] ${racing.lastRaceGrade} Rival Race retry button is available. Retrying once.")
                racing.bRetriedCurrentRace = true
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] ${racing.lastRaceGrade} race retry button is available. Retrying.")
            }

            racing.raceRetries--
            if (dialog.ok(game.imageUtils)) {
                game.wait(1.0)
            }
            return true
        }

        MessageLog.w(TAG, "[WARN] shouldRetryRace:: No retries remaining or G1/G2/G3/Rival race conditions not met.")
        return false
    }

    override fun shouldRecoverMoodFromItems(sourceBitmap: Bitmap): Boolean? {
        val hasMoodItems =
            currentInventory.any { (name, count) ->
                count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake")
            }

        if (trainee.energy >= 70) {
            // If energy is high, we prefer to rest/recover mood naturally to save items.
            MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood} and energy is ${trainee.energy}% (>= 70%). Attempting to recover mood via rest/recreation (saving items).")
            return true
        } else if (!hasMoodItems) {
            // If energy is low, we prefer to use items. If no items are available, we must rest/recover mood manually as a fallback.
            MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood} and energy is ${trainee.energy}% (< 70%). No mood items are available. Attempting to recover mood via rest/recreation...")
            return true
        }

        // Has mood items and energy is low — skip recovery, items will handle mood in useItems().
        return false
    }

    override fun handleRaceEventFallback(): Boolean {
        if (racing.detectedMandatoryRaceCheck) {
            return super.handleRaceEventFallback()
        }
        ButtonBack.click(game.imageUtils)
        ButtonCancel.click(game.imageUtils)
        ButtonClose.click(game.imageUtils)
        game.wait(1.0)
        handleTrackblazerTraining()
        return false
    }

    override fun handleRaceEvents(isScheduledRace: Boolean): Boolean {
        counterUpdatedByOCR = false

        // If it's not a scheduled race, we need to apply Trackblazer-specific filtering.
        if (!isScheduledRace) {
            val sourceBitmap = game.imageUtils.getSourceBitmap()

            // Check if we're at a mandatory race screen first (IconRaceDayRibbon or IconGoalRibbon).
            // If we are, we should treat it as a mandatory race and NOT an extra race.
            if (IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap) || IconGoalRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
                MessageLog.i(TAG, "[TRACKBLAZER] Mandatory race ribbon detected. Processing as mandatory race.")
                val mandatoryResult = super.handleRaceEvents(true)
                if (mandatoryResult) {
                    finishTurnMegaphoneDecrement()
                }
                return mandatoryResult
            }

            MessageLog.i(TAG, "[TRACKBLAZER] Checking for suitable races.")
            // We need to enter the race list to check for predictions and grades.
            // Try both standard Races button and the Race Day variant.
            if (!ButtonRaces.click(game.imageUtils, sourceBitmap = sourceBitmap) && !ButtonRaceDayRace.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                MessageLog.e(TAG, "[ERROR] handleRaceEvents:: Failed to click Races button.")
                return false
            }
            game.wait(1.0)

            // Handle any consecutive race warning dialogs that might pop up after clicking "Races".
            val dialogResult = handleDialogs()
            if (dialogResult is DialogHandlerResult.Handled && consecutiveRaceCount > consecutiveRacesLimit && game.imageUtils.determineTurnsRemainingBeforeNextGoal() != 1) {
                MessageLog.i(TAG, "[TRACKBLAZER] Consecutive race warning obeyed. Aborting racing.")
                return false
            }

            val suitableRaceResult = findSuitableRace(consecutiveRaceCount, preferredDistances, preferredSurfaces)
            if (suitableRaceResult != null) {
                val suitableRaceLocation = suitableRaceResult.first
                val raceData = suitableRaceResult.second
                MessageLog.i(TAG, "[TRACKBLAZER] Found suitable race: ${raceData.name} (${raceData.grade}). Processing items.")

                // Use race-related items (Hammers, Glow Sticks).
                // Skip OP, Pre-debut, and Maiden races as hammers provide no benefit for those grades.
                if (raceData.grade == RaceGrade.G1 || raceData.grade == RaceGrade.G2 || raceData.grade == RaceGrade.G3) {
                    useRaceItems(raceData.grade, raceData.fans)
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Non-G1/G2/G3 race detected (${raceData.grade}). Skipping race item usage.")
                }

                racing.lastRaceGrade = raceData.grade
                racing.lastRaceDistance = raceData.trackDistance
                racing.lastRaceIsRival = raceData.isRival
                game.tap(suitableRaceLocation.x, suitableRaceLocation.y, "race_list_prediction_double_star", ignoreWaiting = true)
                game.wait(0.5)
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] No suitable races found. Backing out and training.")
                ButtonBack.click(game.imageUtils)
                game.wait(0.5)
                return false
            }
        }

        val result = super.handleRaceEvents(isScheduledRace)
        if (result) {
            finishTurnMegaphoneDecrement()
            if (!counterUpdatedByOCR) {
                consecutiveRaceCount++
                MessageLog.i(TAG, "[TRACKBLAZER] Incremented consecutive race count to $consecutiveRaceCount.")
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] Consecutive race count was already updated by OCR: $consecutiveRaceCount.")
            }

            // Check if we should perform a shop check after this race.
            // Any graded race defined in the settings or any scheduled race should trigger a shop check.
            if (isScheduledRace || shopCheckGrades.contains(racing.lastRaceGrade)) {
                if (shopCheckFrequency <= 1) {
                    if (isScheduledRace) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Scheduled race completed. Shop check will be performed on main screen.")
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] Graded race detected (${racing.lastRaceGrade}). Shop check will be performed on main screen.")
                    }
                    bShouldCheckShop = true
                } else if (shopCheckCounter == 0) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Race completed. Starting shop check counter at 1. Frequency: $shopCheckFrequency.")
                    shopCheckCounter = 1
                }
            }
        }
        return result
    }

    /**
     * Uses a cure item scheduled at the end of the prior turn after accepting a Slow Metabolism event option.
     * Handled inside [useItems] / [manageInventoryItems] via [pendingPostEventCureItemToUse].
     */
    private fun usePendingPostEventSlowMetabolismCure() {
        val itemName = pendingPostEventCureItemToUse() ?: return
        MessageLog.i(TAG, "[TRACKBLAZER] Scheduled post-event cure pending: $itemName (will queue during item pass).")
    }

    override fun resetDailyFlags() {
        bUsedWhistleToday = false
        bUsedCharmToday = false
        bUsedHammerToday = false
        bIsIrregularTraining = false
        bHasCheckedIrregularTrainingThisTurn = false
        megaphoneDecrementedThisTurn = false
        statSpecificTrainingItemsQueuedFor = null
        training.clearAnalysisCache()
    }

    override fun onBeforeMainScreenUpdate() {
        // Buy items if a shop check is pending after a race.
        if (bShouldCheckShop) {
            MessageLog.i(TAG, "[TRACKBLAZER] Pending shop check detected! Checking Shop for new items...")
            game.wait(0.5)
            if (openShop()) {
                bShouldCheckShop = false
                buyItems(bAfterRacePurchase = true)
            } else {
                MessageLog.w(TAG, "[WARN] onBeforeMainScreenUpdate:: Failed to open the shop despite pending shop check.")
            }
        }
    }

    override fun hasCureForNegativeStatus(statusName: String): Boolean {
        if (statusName == NegativeStatus.SLOW_METABOLISM.statusName) {
            return (currentInventory["Smart Scale"] ?: 0) > 0 || (currentInventory["Miracle Cure"] ?: 0) > 0
        }
        return (currentInventory["Miracle Cure"] ?: 0) > 0
    }

    override fun getPreferredCureItemForNegativeStatus(statusName: String): String? =
        when {
            statusName == NegativeStatus.SLOW_METABOLISM.statusName && (currentInventory["Smart Scale"] ?: 0) > 0 -> "Smart Scale"
            (currentInventory["Miracle Cure"] ?: 0) > 0 -> "Miracle Cure"
            else -> null
        }

    override fun onMainScreenEntry() {
        // Before taking any action, check for items to use.
        // This handles Stats, Energy, Mood, and Bad Conditions.
        // Training items are only available starting Turn 13 (Junior Year Early July).
        if (date.day >= 13) {
            if (!bInitialShopCheckPerformed) {
                MessageLog.i(TAG, "[TRACKBLAZER] Performing first-time Shop check for the session...")
                if (openShop()) {
                    buyItems()
                    bInitialShopCheckPerformed = true
                }
            }

            useItems(trainee)
        }
    }

    override fun performMoodRecovery(sourceBitmap: Bitmap, targetMood: Mood): Boolean {
        // If we don't have Cupcakes, we fall back to the standard recovery method.
        return recoverMood(sourceBitmap, targetMood = targetMood)
    }

    override fun decideNextAction(): MainScreenAction {
        // Summer Training: Train during July and August in Classic/Senior.
        if (date.isSummer() && !(racing.skipSummerTrainingForAgenda && racing.enableUserInGameRaceAgenda)) {
            MessageLog.i(TAG, "[TRACKBLAZER] It is Summer. Prioritizing training.")
            return MainScreenAction.TRAIN
        }

        // Finale: Train during the final 3 turns (Qualifier, Semifinal, Finals).
        if (date.bIsFinaleSeason && date.day >= 73) {
            MessageLog.i(TAG, "[TRACKBLAZER] It is the Finale. Prioritizing training.")
            return MainScreenAction.TRAIN
        }

        // Avoid racing and training analysis at low energy with 3+ consecutive races to prevent
        // -30 stat penalty. Energy items may also be used from the main screen when energy is very low.
        // However, if a Good-Luck Charm is available, allow training analysis since the charm
        // can bypass high failure chances that come with low energy.
        val hasCharmAvailable = !bUsedCharmToday && (currentInventory["Good-Luck Charm"] ?: 0) > 0
        if (trainee.energy <= 10 && consecutiveRaceCount >= 3 && !hasCharmAvailable) {
            // Before resting, attempt to use a conserved energy item for emergency race recovery.
            val conserveItem = energyItemConservationOrder.firstOrNull { (currentInventory[it] ?: 0) > 0 }
            if (conserveItem != null) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Energy is low (${trainee.energy}%) with $consecutiveRaceCount consecutive races. Using conserved $conserveItem for emergency recovery.",
                )
                if (shopList.openTrainingItemsDialog()) {
                    bForceUseReservedItem = true
                    val itemsUsed = shopList.useSpecificItems(listOf(conserveItem), reason = "Emergency race recovery to avoid -30 stat penalty.")
                    bForceUseReservedItem = false
                    itemsUsed.forEach { (name, _) ->
                        val gain = energyGains[name] ?: 0
                        val oldEnergy = trainee.energy
                        trainee.energy = (trainee.energy + gain).coerceAtMost(100)
                        useInventoryItem(name)
                        MessageLog.i(TAG, "[TRACKBLAZER] Emergency recovery: $oldEnergy% -> ${trainee.energy}%.")
                    }
                    if (itemsUsed.isNotEmpty()) {
                        confirmAndCloseItemDialog(itemsUsed.size)
                    } else {
                        ButtonClose.click(game.imageUtils)
                        game.wait(game.dialogWaitDelay)
                    }
                }

                if (trainee.energy > 10) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Energy recovered to ${trainee.energy}%. Resuming normal decision flow.")
                    // Fall through to normal racing/training logic below.
                } else {
                    MessageLog.w(TAG, "[WARN] decideNextAction:: Energy still low (${trainee.energy}%) after emergency recovery. Resting.")
                    return MainScreenAction.REST
                }
            } else {
                MessageLog.w(
                    TAG,
                    "[WARN] decideNextAction:: Energy is low (${trainee.energy}%) with $consecutiveRaceCount consecutive races and no energy items available. Resting to avoid -30 stat penalty.",
                )
                return MainScreenAction.REST
            }
        }

        // Smart Race Solver pre-check: the solver's role is binary - either "race race-X today" or "no race today". When the solver picks
        // a race we defer to the racing flow. Otherwise we fall through to the legacy main-screen loop, which decides between training,
        // resting, fan-farming, etc. The solver no longer dictates Train vs Rest on no-race turns.
        if (racing.enableSmartRaceSolver && racing.enableFarmingFans && !racing.enableForceRacing) {
            val solverRaceKey = SmartRaceSolverIntegration.peekRaceKeyForTurn(currentTurn = date.day, scenario = game.scenario)
            if (solverRaceKey != null) {
                MessageLog.i(TAG, "[TRACKBLAZER] Smart Race Solver has \"$solverRaceKey\" planned for turn ${date.day}; deferring to racing flow.")
                return MainScreenAction.RACE
            }
        }

        if (enableIrregularTraining && date.year > DateYear.JUNIOR && !bHasCheckedIrregularTrainingThisTurn) {
            val isScheduledRace = LabelScheduledRace.check(game.imageUtils)
            val isMandatoryRace = IconRaceDayRibbon.check(game.imageUtils) || IconGoalRibbon.check(game.imageUtils)

            if (!isScheduledRace && !isMandatoryRace) {
                // Skip irregular training evaluation when energy is depleted and no charm can offset the failure chance.
                if (trainee.energy <= 0 && !hasCharmAvailable) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Skipping Irregular Training evaluation as energy is ${trainee.energy}% with no Good-Luck Charm available.")
                    bHasCheckedIrregularTrainingThisTurn = true
                } else if (ButtonTraining.click(game.imageUtils)) {
                    game.wait(game.dialogWaitDelay)

                    val isIrregularEvaluation = true
                    val hasCharm = !bUsedCharmToday && (currentInventory["Good-Luck Charm"] ?: 0) > 0
                    training.analyzeTrainings(
                        mapOf(
                            "ignoreFailureChance" to hasCharm,
                            "isIrregularEvaluation" to isIrregularEvaluation,
                            "minStatGainForCharm" to minCharmGain,
                            "irregularTrainingMinStatGain" to minIrregularGain,
                        ),
                    )

                    val bestTraining = training.recommendTraining(args = mapOf("isIrregularEvaluation" to true, "irregularTrainingMinStatGain" to minIrregularGain))
                    if (bestTraining != null && training.lastSelectionSource != SelectionSource.ANALYSIS) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Pre-screen evaluation used fallback (${training.lastSelectionSource}): $bestTraining.")
                    }

                    if (bestTraining != null) {
                        // Stay on the training screen in order to perform the training.
                        MessageLog.i(TAG, "[TRACKBLAZER] Valid Irregular Training found ($bestTraining). Hijacking turn.")

                        bIsIrregularTraining = true
                        return MainScreenAction.TRAIN
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] No valid Irregular Training found. Backing out to resume racing logic.")
                        ButtonBack.click(game.imageUtils)
                        game.wait(game.dialogWaitDelay)

                        // Mark that we've checked for Irregular Training this turn to avoid looping.
                        bHasCheckedIrregularTrainingThisTurn = true
                    }
                }
            }
        }

        // Otherwise, use base class decision logic.
        return super.decideNextAction()
    }

    override fun executeAction(action: MainScreenAction, bIsScheduledRaceDay: Boolean): Boolean {
        val result =
            when (action) {
                MainScreenAction.TRAIN -> {
                    if (bForcedWitTraining) {
                        super.executeAction(action, bIsScheduledRaceDay)
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] Decision made to train.")
                        handleTrackblazerTraining()
                        bHasCheckedDateThisTurn = false
                        true
                    }
                }

                else -> {
                    super.executeAction(action, bIsScheduledRaceDay)
                }
            }

        if (result && action != MainScreenAction.NONE) {
            finishTurnMegaphoneDecrement()

            // Increment the shop check counter if it is active.
            if (shopCheckCounter > 0) {
                shopCheckCounter++
                if (shopCheckCounter >= shopCheckFrequency) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop check frequency reached ($shopCheckCounter / $shopCheckFrequency). Shop check will be performed on main screen.")
                    bShouldCheckShop = true
                    shopCheckCounter = 0
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop check counter: $shopCheckCounter / $shopCheckFrequency. Next check in ${shopCheckFrequency - shopCheckCounter} day(s).")
                }
            }
        }

        return result
    }

    override fun onRaceWin() {
        MessageLog.i(TAG, "[TRACKBLAZER] Rival Race win detected via post-race popup.")
        if (shopCheckFrequency <= 1) {
            bShouldCheckShop = true
        } else if (shopCheckCounter == 0) {
            MessageLog.i(TAG, "[TRACKBLAZER] Rival Race win detected. Starting shop check counter at 1. Frequency: $shopCheckFrequency.")
            shopCheckCounter = 1
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Opens the Shop UI.
     *
     * @param tries The number of scan attempts to perform to find the shop button.
     * @return True if the shop was opened successfully, false otherwise.
     */
    fun openShop(tries: Int = 5): Boolean {
        if (ButtonShopTrackblazer.click(game.imageUtils, tries = tries)) {
            game.wait(game.dialogWaitDelay)
            return true
        }

        val detectedDialog = DialogUtils.getDialog(game.imageUtils)
        if (detectedDialog != null && detectedDialog.name == "shop") {
            MessageLog.i(TAG, "[TRACKBLAZER] Shop dialog detected while trying to open the shop. Entering via dialog...")
            if (detectedDialog.ok(game.imageUtils)) {
                game.wait(game.dialogWaitDelay)
                return ButtonTrainingItems.check(game.imageUtils)
            }
        }

        MessageLog.e(TAG, "[ERROR] openShop:: Unable to open the Shop due to failing to find its button.")
        return false
    }

    /**
     * Reads the Shop Coins amount via OCR and updates our internal count.
     *
     * @return True if the Shop Coins amount was updated successfully, false otherwise.
     */
    fun updateShopCoins(): Boolean {
        MessageLog.i(TAG, "[TRACKBLAZER] Updating current amount of Shop Coins...")
        game.wait(3.0)
        val (trainingItemsButtonLocation, sourceBitmap) = ButtonTrainingItems.find(game.imageUtils, tries = 30)
        if (trainingItemsButtonLocation == null) {
            MessageLog.e(TAG, "[ERROR] updateShopCoins:: Failed to find Training Items button.")
            return false
        }
        val coinText =
            game.imageUtils.performOCROnRegion(
                sourceBitmap,
                game.imageUtils.relX(trainingItemsButtonLocation.x, -35),
                game.imageUtils.relY(trainingItemsButtonLocation.y, 80),
                game.imageUtils.relWidth(180),
                game.imageUtils.relHeight(65),
                useThreshold = false,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = "mlkit",
                debugName = "ShopCoins",
            )

        try {
            // ML Kit sometimes misreads a lone digit "1" as the letter "L". Treat that whole-string case as 1 so we don't drop a valid count of one.
            val normalizedText = if (coinText.trim().equals("L", ignoreCase = true)) "1" else coinText
            val cleanedText = normalizedText.replace(Regex("[^0-9]"), "")
            if (cleanedText.isEmpty()) {
                MessageLog.w(TAG, "[WARN] updateShopCoins:: Parsed empty string for Shop Coins from raw text: \"$coinText\".")
            } else {
                shopCoins = cleanedText.toInt()
                MessageLog.i(TAG, "[INFO] Current Shop Coins: $shopCoins (Raw OCR text: \"$coinText\")")
            }
        } catch (_: NumberFormatException) {
            MessageLog.e(TAG, "[ERROR] updateShopCoins:: Failed to parse Shop Coins from OCR text: \"$coinText\".")
        }

        return true
    }

    /**
     * Starts the process to buy items from the Shop.
     *
     * @param priorityList An ordered list of item names to buy. Defaults to an empty list.
     * @param bDryRun If true, only logs intentions without performing any clicks.
     * @param bAfterRacePurchase If true, indicates this process was triggered by a post-race shop check.
     */
    fun buyItems(priorityList: List<String> = listOf(), bDryRun: Boolean = false, bAfterRacePurchase: Boolean = false) {
        val finalPriorityList = priorityList.ifEmpty { getPriorityList() }

        if (bAfterRacePurchase) {
            MessageLog.i(TAG, "[TRACKBLAZER] Buying extra items after participating in a race...")
        }
        MessageLog.i(TAG, "[TRACKBLAZER] Initiating buying process.")

        // Update current coins via OCR before buying.
        if (!updateShopCoins()) {
            MessageLog.w(TAG, "[TRACKBLAZER] Aborting buying process due to failed Shop Coins update.")
            return
        }
        MessageLog.i(TAG, "[TRACKBLAZER] Initial Shop Coins: $shopCoins")

        // If the shop coins are 0, it is possible that the OCR failed to read them correctly.
        // In this case, we will initiate a "Force Purchase" process to attempt to buy items until we can't anymore.
        val bForcePurchase = shopCoins == 0
        if (bForcePurchase) {
            MessageLog.i(TAG, "[TRACKBLAZER] Shop coins read as 0. This may be an OCR failure. Initiating Force Purchase mode.")
        }

        val inventoryLimits =
            finalPriorityList.associateWith { itemName ->
                val itemCount = currentInventory[itemName] ?: 0
                val isBadConditionItem = badConditionMap.containsKey(itemName) || itemName == "Miracle Cure"
                val isGoodConditionItem = goodConditionMap.containsKey(itemName)

                val maxLimit =
                    if (isBadConditionItem || isGoodConditionItem) {
                        // Check if we already have the item in inventory.
                        if (itemCount >= 1) {
                            0
                        } else {
                            // Check if the condition is active/inactive.
                            if (isBadConditionItem) {
                                val condition = badConditionMap[itemName]
                                if (itemName == "Miracle Cure" || itemName == "Rich Hand Cream") {
                                    // We want to buy as many of these when possible as we will be racing above the consecutive race limit often.
                                    5
                                } else if (condition != null && trainee.currentNegativeStatuses.contains(condition)) {
                                    1
                                } else {
                                    0
                                }
                            } else {
                                val condition = goodConditionMap[itemName]
                                if (condition != null && !trainee.currentPositiveStatuses.contains(condition)) {
                                    1
                                } else {
                                    0
                                }
                            }
                        }
                    } else {
                        5
                    }

                (maxLimit - itemCount).coerceAtLeast(0)
            }

        val filteredPriorityList = finalPriorityList.filter { (inventoryLimits[it] ?: 0) > 0 }

        if (filteredPriorityList.isEmpty()) {
            MessageLog.v(TAG, getInventorySummary(withDividers = true))
        } else if (bDryRun) {
            shopList.buyItems(filteredPriorityList, shopCoins, inventoryLimits, bDryRun = true, bForcePurchase = bForcePurchase)
            return
        }

        val itemsBought = shopList.buyItems(filteredPriorityList, shopCoins, inventoryLimits, bForcePurchase = bForcePurchase)
        if (itemsBought.isNotEmpty()) {
            // Update internal inventory.
            val nextInventory = currentInventory.toMutableMap()
            itemsBought.forEach { itemName ->
                nextInventory[itemName] = (nextInventory[itemName] ?: 0) + 1
            }
            currentInventory = nextInventory.toMap()

            // Handle "Exchange Complete" dialog.
            if (handleDialogs(DialogExchangeComplete, args = mapOf("itemsBought" to itemsBought)) is DialogHandlerResult.Handled) {
                MessageLog.i(TAG, "[TRACKBLAZER] Successfully handled \"Exchange Complete\" dialog.")

                // Update internal coins count via OCR after purchase.
                updateShopCoins()
                MessageLog.i(TAG, "[TRACKBLAZER] Remaining Shop Coins: $shopCoins")

                ButtonBack.click(game.imageUtils)
                game.wait(2.0)
            }
        }

        // Exit the Shop to return to the Main screen.
        MessageLog.i(TAG, "[TRACKBLAZER] Shop process complete. Returning up to the previous screen.")
        ButtonBack.click(game.imageUtils)
        game.wait(1.0)
    }

    /**
     * Generates a priority list of items to buy based on current state and rules.
     *
     * @return An ordered list of item names.
     */
    private fun getPriorityList(): List<String> {
        val topStats = training.statPrioritization.take(3)
        val priorityList = mutableListOf<String>()

        // 1. Top Tier Priorities (Good-Luck Charms, Hammers, Glow Sticks, Priority heals, Priority Energy/Bond).
        priorityList.add("Good-Luck Charm")
        priorityList.add("Master Cleat Hammer")
        priorityList.add("Artisan Cleat Hammer")
        priorityList.add("Glow Sticks")
        priorityList.add("Royal Kale Juice")
        priorityList.add("Grilled Carrots")
        priorityList.add("Rich Hand Cream")
        priorityList.add("Miracle Cure")

        // 2. Stats (Excluding Notepads).
        val statsOrdered = listOf("Scroll", "Manual")
        val statNamesOrdered = listOf("Speed", "Stamina", "Power", "Guts", "Wit")
        statsOrdered.forEach { type ->
            statNamesOrdered.forEach { name ->
                priorityList.add("$name $type")
            }
        }

        // 3. Energy + Mood.
        priorityList.add("Vita 65")
        priorityList.add("Vita 40")
        priorityList.add("Vita 20")
        priorityList.add("Berry Sweet Cupcake")
        priorityList.add("Plain Cupcake")

        // 4. Training Effects (Megaphones and specific Ankle Weights).
        priorityList.add("Empowering Megaphone")
        priorityList.add("Motivating Megaphone")
        topStats.forEach { stat ->
            val ankleWeight =
                when (stat) {
                    StatName.SPEED -> "Speed Ankle Weights"
                    StatName.STAMINA -> "Stamina Ankle Weights"
                    StatName.POWER -> "Power Ankle Weights"
                    StatName.GUTS -> "Guts Ankle Weights"
                    else -> null
                }
            if (ankleWeight != null) priorityList.add(ankleWeight)
        }
        priorityList.add("Coaching Megaphone")
        priorityList.add("Reset Whistle")

        // 5. Heal Bad Conditions (Non-priority ones, limit 1 logic is handled in buyItems()).
        priorityList.add("Fluffy Pillow")
        priorityList.add("Pocket Planner")
        priorityList.add("Smart Scale")
        priorityList.add("Aroma Diffuser")
        priorityList.add("Practice Drills DVD")

        // 6. Training Facilities (Top 3 stats only).
        topStats.forEach { stat ->
            val trainingApp =
                when (stat) {
                    StatName.SPEED -> "Speed Training Application"
                    StatName.STAMINA -> "Stamina Training Application"
                    StatName.POWER -> "Power Training Application"
                    StatName.GUTS -> "Guts Training Application"
                    StatName.WIT -> "Wit Training Application"
                }
            priorityList.add(trainingApp)
        }

        // 7. Other Energy Items.
        priorityList.add("Energy Drink MAX")
        priorityList.add("Energy Drink MAX EX")

        // 8. Good Condition Items
        priorityList.add("Pretty Mirror")
        priorityList.add("Reporter's Binoculars")
        priorityList.add("Master Practice Guide")
        priorityList.add("Scholar's Hat")

        return priorityList
    }

    /**
     * Decrements an item's count in the internal inventory.
     *
     * @param itemName The name of the item used.
     */
    private fun useInventoryItem(itemName: String) {
        val nextInventory = currentInventory.toMutableMap()
        val count = nextInventory[itemName] ?: 0
        if (count > 0) {
            nextInventory[itemName] = count - 1
            MessageLog.i(TAG, "[TRACKBLAZER] Decremented $itemName. Remaining: ${nextInventory[itemName]}.")
        }
        currentInventory = nextInventory.toMap()
    }

    /**
     * Confirms the usage of items and closes the Training Items dialog.
     *
     * @param itemsUsedCount The number of items used during this pass to determine the animation delay.
     */
    private fun confirmAndCloseItemDialog(itemsUsedCount: Int = 1) {
        MessageLog.i(TAG, "[TRACKBLAZER] Confirming usage of $itemsUsedCount items.")
        ButtonConfirmUse.click(game.imageUtils)
        game.wait(game.dialogWaitDelay)
        ButtonUseTrainingItems.click(game.imageUtils)

        // Lengthy delay here for the animation to finish.
        // We increase the delay by a second for each additional item to be used after 3 items.
        val animationDelay = if (itemsUsedCount > 3) 4.0 + (itemsUsedCount - 3) else 4.0
        MessageLog.i(TAG, "[TRACKBLAZER] Waiting for animation to finish (Delay: $animationDelay seconds).")
        game.wait(animationDelay)

        // Finalize by closing the dialog.
        MessageLog.i(TAG, "[TRACKBLAZER] Closing training items dialog.")
        val maxCloseAttempts = 3
        var closeAttempt = 0
        while (closeAttempt < maxCloseAttempts) {
            if (ButtonClose.check(game.imageUtils, tries = 50)) {
                game.wait(1.0)
                ButtonClose.click(game.imageUtils)
                game.wait(1.0)
            }
            if (!ButtonConfirmUse.check(game.imageUtils, tries = 5)) {
                break
            }
            closeAttempt++
            if (closeAttempt < maxCloseAttempts) {
                MessageLog.w(TAG, "[WARN] confirmAndCloseItemDialog:: Training Items dialog still visible after close attempt $closeAttempt/$maxCloseAttempts. Retrying.")
                game.wait(1.0)
            }
        }
        if (closeAttempt >= maxCloseAttempts) {
            MessageLog.e(TAG, "[ERROR] confirmAndCloseItemDialog:: Training Items dialog did not close after $maxCloseAttempts attempts. The next training click may misfire.")
        }
    }

    /**
     * Clicks the plus button for an item in the item list and updates inventory.
     *
     * @param itemName The name of the item.
     * @param entry The ScrollListEntry of the item.
     * @param logMessage The message to log when clicking.
     * @param nextInventory The current inventory map being updated during this pass.
     * @param recheck If true, captures a fresh crop of the entry to re-verify the button state.
     * @param reason Optional reason for using the item.
     * @return True if the button was clicked, false otherwise.
     */
    private fun clickItemPlusButton(itemName: String, entry: ScrollListEntry, logMessage: String, nextInventory: MutableMap<String, Int>, recheck: Boolean = false, reason: String? = null): Boolean {
        val bitmapToUse: Bitmap =
            if (recheck) {
                val source = game.imageUtils.getSourceBitmap()
                game.imageUtils.createSafeBitmap(source, entry.bbox.x, entry.bbox.y, entry.bbox.w, entry.bbox.h, "recheck item")
            } else {
                entry.bitmap
            } ?: return false

        if (ButtonSkillUp.checkDisabled(game.imageUtils, bitmapToUse) == true) return false

        val plusPoint = ButtonSkillUp.findImageWithBitmap(game.imageUtils, bitmapToUse)
        if (plusPoint != null) {
            MessageLog.i(TAG, logMessage)
            game.tap(entry.bbox.x + plusPoint.x, entry.bbox.y + plusPoint.y)

            // Update the provided inventory map.
            val count = nextInventory[itemName] ?: 0
            if (count > 0) {
                nextInventory[itemName] = count - 1
                MessageLog.i(TAG, "[TRACKBLAZER] Decremented $itemName. Remaining: ${nextInventory[itemName]}.")
            }

            return true
        }
        return false
    }

    /**
     * Queues training items (ankle weights, megaphones, charms, energy, etc.) for [trainingSelected], re-checks when
     * charm/energy was used or when a risky pick still lacks mitigation, then executes training when the pick is still valid.
     */
    private fun executeTrainingWithItems(
        trainingSelected: StatName,
        climaxCharmTraining: Boolean,
    ): StatName? {
        var selected: StatName? = trainingSelected
        if (date.day >= 13) {
            val preItemFailure = training.capturePreItemFailureSnapshot()
            val preItemMainGain = preItemMainGainFor(selected)
            useItems(trainee, selected)
            val needsRecheckAfterItems =
                bUsedCharmToday ||
                    bUsedEnergyItemThisPass ||
                    (
                        selected != null &&
                            training.requiresFailureMitigationBeforeExecute(
                                failureChance = preItemFailure[selected] ?: 0,
                                mainStatGain = preItemMainGain,
                                charmUsed = false,
                                climaxForceCharm = false,
                            )
                    )
            if (needsRecheckAfterItems) {
                selected = recheckTrainingAfterItems(selected, climaxCharmTraining, preItemFailure)
            }
        }
        if (selected != null && shouldBlockUnmitigatedHighFailureExecute(selected, climaxCharmTraining)) {
            return null
        }
        if (selected == null) {
            return null
        }
        if (!training.executeTraining(selected)) {
            return null
        }
        training.firstTrainingCheck = false
        return selected
    }

    /** Backs out of the Training screen and attempts mood or energy recovery on the main screen. */
    private fun backOutFromTrainingForRecovery(reason: String) {
        MessageLog.i(TAG, "[TRACKBLAZER] $reason Backing out for recovery.")
        training.firstTrainingCheck = false
        ButtonBack.click(game.imageUtils)
        game.wait(1.0)

        if (checkMainScreen()) {
            if (trainee.mood == Mood.AWFUL || (trainee.mood <= Mood.NORMAL && trainee.energy >= 20)) {
                MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood}. Attempting to recover mood.")
                recoverMood()
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] Energy is ${trainee.energy}%. Attempting to recover energy.")
                recoverEnergy()
            }
        }
    }

    /** Main stat gain for [stat] from the latest analysis snapshot. */
    private fun preItemMainGainFor(stat: StatName?): Int {
        if (stat == null) return 0
        return training.trainingMap[stat]?.statGains?.get(stat)
            ?: training.cachedAnalysisResults?.firstOrNull { it.name == stat }?.statGains?.get(stat)
            ?: 0
    }

    /**
     * Blocks executing a training whose failure chance still exceeds the configured threshold when Good-Luck Charm
     * was not applied. Catches analysis that assumed a charm would fire (charm in inventory) but item pass skipped it.
     */
    private fun shouldBlockUnmitigatedHighFailureExecute(selected: StatName, climaxCharmTraining: Boolean): Boolean {
        if (bUsedCharmToday) {
            return false
        }
        if (bUsedEnergyItemThisPass && failureMitigationEnergyQueuedCounts.isNotEmpty()) {
            return false
        }
        if (climaxCharmTraining && isClimaxCharmTrainingActive()) {
            val option = training.trainingMap[selected] ?: training.skippedTrainingMap[selected]
            if (option != null) {
                val mainGain = option.statGains[selected] ?: 0
                if (training.exceedsFailureThreshold(option.failureChance, mainGain)) {
                    MessageLog.w(
                        TAG,
                        "[TRACKBLAZER] Refusing Climax training on $selected (${option.failureChance}% failure): Good-Luck Charm was not applied this turn.",
                    )
                    return true
                }
            }
            return false
        }

        val option = training.trainingMap[selected] ?: training.skippedTrainingMap[selected] ?: return false
        val mainGain = option.statGains[selected] ?: 0
        if (!training.exceedsFailureThreshold(option.failureChance, mainGain)) {
            return false
        }

        MessageLog.w(
            TAG,
            "[TRACKBLAZER] Refusing to execute $selected (${option.failureChance}% failure, main gain $mainGain): exceeds threshold without Good-Luck Charm or acceptable mitigation.",
        )
        return true
    }

    /**
     * Handles the specialized training process for Trackblazer, including item usage.
     */
    private fun handleTrackblazerTraining() {
        MessageLog.i(TAG, "[TRACKBLAZER] Starting specialized Training process.")

        // Fast path: Already on the training screen from irregular training evaluation.
        if (bIsIrregularTraining) {
            MessageLog.i(TAG, "[TRACKBLAZER] Using existing irregular training analysis (already on Training screen).")
            var trainingSelected: StatName? = training.recommendTraining(args = mapOf("isIrregularEvaluation" to true, "irregularTrainingMinStatGain" to minIrregularGain))
            if (trainingSelected != null && training.lastSelectionSource != SelectionSource.ANALYSIS) {
                MessageLog.i(TAG, "[TRACKBLAZER] On-screen evaluation used fallback (${training.lastSelectionSource}): $trainingSelected.")
            }

            if (trainingSelected != null) {
                trainingSelected = resolveExecutableTrainingSelection(trainingSelected, climaxCharmTraining = false)
                val executed =
                    trainingSelected?.let { executeTrainingWithItems(it, climaxCharmTraining = false) }
                if (executed == null) {
                    MessageLog.w(TAG, "[WARN] handleTrackblazerTraining:: Irregular training execute blocked. Backing out.")
                    backOutFromTrainingForRecovery("Irregular training execute blocked.")
                } else {
                    trainingSelected = executed
                }
            } else {
                MessageLog.w(TAG, "[WARN] handleTrackblazerTraining:: Irregular training unexpectedly became null. Backing out.")
                ButtonBack.click(game.imageUtils)
                game.wait(game.dialogWaitDelay)
            }

            bIsIrregularTraining = false
            training.firstTrainingCheck = false
            return
        }

        // Enter the Training screen.
        if (!ButtonTraining.click(game.imageUtils)) {
            MessageLog.e(TAG, "[ERROR] handleTrackblazerTraining:: Failed to enter Training screen.")
            return
        }
        game.wait(0.5)

        if (shouldTryEnergyRecoveryItems()) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Low energy (${trainee.energy}%) before training analysis. Attempting energy-item recovery on Training screen.",
            )
            useItems(trainee, trainingSelected = null)
            training.clearAnalysisCache()
        }

        // Initial Training Analysis.
        val analysisArgs = buildTrainingAnalysisArgs()
        val climaxCharmTraining = analysisArgs["climaxForceCharmTraining"] as Boolean
        training.analyzeTrainings(analysisArgs)
        var trainingSelected: StatName? =
            if (climaxCharmTraining) {
                training.selectHighestNonMaxedStatForClimax()
            } else {
                training.recommendTraining()
            }
        if (trainingSelected != null && training.lastSelectionSource != SelectionSource.ANALYSIS) {
            MessageLog.i(TAG, "[TRACKBLAZER] Initial training selection used fallback (${training.lastSelectionSource}): $trainingSelected.")
        }

        // Prefer a top-3 priority rainbow training before Reset Whistle (Summer + Finale; still before Good-Luck Charm).
        if (trainingSelected == null && isWhistlePriorityWindow() && whistlePriorityMinRainbow > 0) {
            val rainbowPick =
                training.selectBestTrainableTopPriorityTrainingWithRainbows(
                    whistlePriorityStatList(),
                    whistlePriorityMinRainbow,
                )
            if (rainbowPick != null) {
                val rainbows = training.trainingMap[rainbowPick]?.numRainbow ?: 0
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Selected top-priority $rainbowPick ($rainbows rainbow(s) >= $whistlePriorityMinRainbow) before Reset Whistle / Good-Luck Charm.",
                )
                trainingSelected = rainbowPick
            }
        }

        // Reset Whistle must be evaluated before Good-Luck Charm (charm sets failure to 0%).
        trainingSelected = tryApplyResetWhistle(trainingSelected, climaxCharmTraining)

        // Final Training Execution (items — ankle weights, megaphones, charms, etc. — run after selection is final).
        if (trainingSelected != null) {
            trainingSelected = resolveExecutableTrainingSelection(trainingSelected, climaxCharmTraining)
            trainingSelected = trainingSelected?.let { executeTrainingWithItems(it, climaxCharmTraining) }
        }

        if (trainingSelected == null && isClimaxCharmTrainingActive()) {
            val climaxSelected = training.selectHighestNonMaxedStatForClimax()
            if (climaxSelected != null) {
                MessageLog.i(TAG, "[TRACKBLAZER] Climax phase with remaining Good-Luck Charm(s). Training $climaxSelected instead of resting.")
                trainingSelected = executeTrainingWithItems(climaxSelected, climaxCharmTraining = true)
            }
            if (trainingSelected == null) {
                MessageLog.w(TAG, "[WARN] handleTrackblazerTraining:: Climax charm training unavailable or blocked. Backing out for recovery.")
                backOutFromTrainingForRecovery("No viable Climax training after charm mitigation.")
            }
        } else if (trainingSelected == null) {
            val mitigationPick = bestMitigationBackedTrainingPick()
            if (mitigationPick != null) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] All trainings exceed failure thresholds, but $mitigationPick can use Good-Luck Charm or energy mitigation. Retrying.",
                )
                trainingSelected = resolveExecutableTrainingSelection(mitigationPick, climaxCharmTraining)
                trainingSelected = trainingSelected?.let { executeTrainingWithItems(it, climaxCharmTraining) }
            }
            if (trainingSelected == null) {
                backOutFromTrainingForRecovery("No suitable training found after analysis and item pass.")
            }
        }

        bIsIrregularTraining = false
    }

    /**
     * Executes the logic meant for the Race Prep screen of scheduled races,
     * specifically to use race items if appropriate.
     */
    override fun onScheduledRacePrepScreen() {
        var grade = racing.lastRaceGrade
        var fans = racing.lastRaceFans

        // For Finale races (turns 73, 74, 75), manually set the grade to G1 and appropriate fans.
        // This ensures the racing item logic is triggered for these mandatory races.
        if (date.bIsFinaleSeason && (date.day == 73 || date.day == 74 || date.day == 75)) {
            grade = RaceGrade.G1
            racing.lastRaceGrade = RaceGrade.FINALE
            fans = if (date.day == 75) 30000 else 20000
        }

        if (grade != null && (grade == RaceGrade.G1 || grade == RaceGrade.G2 || grade == RaceGrade.G3)) {
            MessageLog.i(TAG, "[TRACKBLAZER] Executing scheduled race item logic on Race Prep screen.")
            useRaceItems(grade, fans)
        }
    }

    /**
     * Uses race-related items (Hammers, Glow Sticks) based on the race grade and fan count.
     *
     * @param grade The grade of the detected race.
     * @param fans The number of fans awarded by the race.
     */
    private fun useRaceItems(grade: RaceGrade, fans: Int) {
        if (date.day < 13 || bUsedHammerToday) {
            if (bUsedHammerToday) {
                MessageLog.i(TAG, "[TRACKBLAZER] Already used a race item today.")
            }
            return
        }

        val masterHammerCount = currentInventory["Master Cleat Hammer"] ?: 0
        val artisanHammerCount = currentInventory["Artisan Cleat Hammer"] ?: 0
        val glowSticksCount = currentInventory["Glow Sticks"] ?: 0

        // Conservation thresholds activate at `raceItemConservationStartDay` (Turn 65). Before that, the bot uses race items freely.
        val conservationActive = date.day >= raceItemConservationStartDay

        // Master Hammer Logic — finale reserve is always honored before day 73; days 73-74 require 2+ copies so one remains for the Final.
        val canUseMasterHammer =
            Companion.canUseMasterCleatHammer(
                day = date.day,
                masterHammerCount = masterHammerCount,
                grade = grade,
                finaleReserve = masterHammerFinaleReserve,
            )

        // Artisan Hammer Logic. Per-grade stock floors apply only from Turn `raceItemConservationStartDay` onward.
        val canUseArtisanHammer =
            artisanHammerCount > 0 &&
                when (grade) {
                    RaceGrade.G1 -> true
                    RaceGrade.G2 -> !conservationActive || artisanHammerCount >= maxOf(1, artisanMinStockForG2)
                    RaceGrade.G3 -> !conservationActive || artisanHammerCount >= maxOf(1, artisanMinStockForG3)
                    else -> false
                }

        // Master takes priority at the finale since it provides a higher bonus (35% vs 20%).
        val hammerToUse =
            if (date.day < 73) {
                when {
                    canUseArtisanHammer -> "Artisan Cleat Hammer"
                    canUseMasterHammer -> "Master Cleat Hammer"
                    else -> null
                }
            } else {
                when {
                    canUseMasterHammer -> "Master Cleat Hammer"
                    canUseArtisanHammer -> "Artisan Cleat Hammer"
                    else -> null
                }
            }

        // Glow Sticks Logic. `glowStickMinFans` is the per-race fan floor at all times. `glowStickFinalReserve` is honored from conservation start onward.
        val effectiveReserve = if (conservationActive && date.day < 75) glowStickFinalReserve else 0
        val useGlowSticks = fans >= glowStickMinFans && glowSticksCount > effectiveReserve

        if (hammerToUse != null || useGlowSticks) {
            MessageLog.i(TAG, "[TRACKBLAZER] Suitable race items found in inventory (Hammer: $hammerToUse, Glow Sticks: $useGlowSticks). Opening Training Items dialog.")
            if (shopList.openTrainingItemsDialog()) {
                val itemsToUseList = mutableListOf<String>()
                if (hammerToUse != null) itemsToUseList.add(hammerToUse)
                if (useGlowSticks) itemsToUseList.add("Glow Sticks")

                // Pass the reasoning and trigger a single consolidated usage summary.
                val itemsUsed = shopList.useSpecificItems(itemsToUseList, bUseAll = false, reason = "Race bonus for $grade.")
                itemsUsed.forEach { (name, _) ->
                    useInventoryItem(name)
                }

                if (itemsUsed.isNotEmpty()) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Queued ${itemsUsed.size} race items for $grade ($fans fans). Confirming usage.")
                    confirmAndCloseItemDialog(itemsUsed.size)
                    bUsedHammerToday = true
                } else {
                    if (ButtonClose.click(game.imageUtils)) {
                        game.wait(game.dialogWaitDelay)
                    }
                }
            }
        } else {
            if (date.day == 73 && (masterHammerCount > 0 || glowSticksCount > 0)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Conserving race items for Semi-Final/Final (turns 74-75). " +
                        "Hammer: ${masterHammerCount + artisanHammerCount}, Glow Sticks: $glowSticksCount.",
                )
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] No relevant race items in cached inventory for $grade.")
            }
        }
    }

    /**
     * Orchestrates the usage of items based on dynamic conditions and updates internal inventory.
     * Consolidates synchronization and item usage into a single pass for efficiency.
     *
     * @param trainee Reference to the trainee's state. If provided, conditional items will be used.
     * @param trainingSelected The stat name of the selected training to help with item usage (e.g. Ankle Weights).
     * @param bQuickUseOnly If true, only items marked for quick use will be used.
     * @param bDryRun If true, only logs intentions without performing any clicks.
     */
    fun manageInventoryItems(trainee: Trainee? = null, trainingSelected: StatName? = null, bQuickUseOnly: Boolean = false, bDryRun: Boolean = false) {
        if (date.day < 13 && !bDryRun) return

        MessageLog.i(TAG, "[TRACKBLAZER] Starting inventory management pass.")
        bKaleJuiceQueuedThisPass = false
        bUsedEnergyItemThisPass = false
        if (!bStatSpecificItemsOnlyPass) {
            statSpecificTrainingItemsQueuedFor = null
            failureMitigationChoiceForPass = FailureMitigationChoice.NONE
        }
        failureMitigationEnergyPlanForPass = null
        failureMitigationEnergyQueuedCounts.clear()
        if (trainee != null && trainingSelected != null && !bDryRun) {
            if (!bStatSpecificItemsOnlyPass) {
                failureMitigationChoiceForPass = resolveFailureMitigationChoice(trainingSelected, trainee, currentInventory)
                preReconcileCharmMitigationIfBlocked(trainingSelected, trainee)
            }
        }
        val initialEnergy = trainee?.energy ?: 0
        val initialMood = trainee?.mood ?: Mood.NORMAL
        val initialMegaphoneTurnCounter = trainee?.megaphoneTurnCounter ?: 0
        val nextInventory = currentInventory.toMutableMap()
        val scannedItemsList = mutableListOf<ScannedItem>()
        var itemsUsedCount = 0
        var wasEarlyExit = false

        // To improve efficiency, we identify which items we are actually interested in based on our cached inventory.
        // If we have a cached inventory and have seen all items of interest, we can exit the scroll loop early.
        val remainingItemsOfInterest =
            if (currentInventory.isNotEmpty()) {
                val failureChance = if (trainingSelected != null) training.trainingMap[trainingSelected]?.failureChance ?: 0 else 0
                val skipCharmForConservation =
                    trainingSelected != null &&
                        trainee != null &&
                        shouldConserveTrainingEffectItems(trainingSelected, trainee)
                val neededWeight = ankleWeightItemForStat(trainingSelected)

                currentInventory
                    .filter { (name, count) ->
                        if (count <= 0) return@filter false

                        if (bStatSpecificItemsOnlyPass) {
                            if (trainee == null || trainingSelected == null) return@filter false
                            val isMegaphone =
                                name == "Empowering Megaphone" || name == "Motivating Megaphone" || name == "Coaching Megaphone"
                            val isAnkleWeight = name == neededWeight
                            return@filter (
                                isMegaphone && hasMegaphoneAvailableForTraining(trainingSelected, trainee, currentInventory)
                            ) ||
                                (isAnkleWeight && isAnkleWeightEligibleForUse(name, trainingSelected, currentInventory))
                        }

                        val info = shopList.shopItems[name]
                        val isStat = info?.category == "Stats"
                        val isBad = info?.category == "Heal Bad Conditions"
                        val isQuick = info?.isQuickUsage == true
                        val isEnergy = shopList.energyItemNames.contains(name) || name == "Royal Kale Juice"
                        val isMood = name == "Berry Sweet Cupcake" || name == "Plain Cupcake"
                        val isMegaphone = name == "Empowering Megaphone" || name == "Motivating Megaphone" || name == "Coaching Megaphone"
                        val isAnkleWeight = name == neededWeight
                        val isCharm =
                            name == "Good-Luck Charm" &&
                                trainee != null &&
                                trainingSelected != null &&
                                !skipCharmForConservation &&
                                shouldQueueGoodLuckCharmForTraining(trainingSelected, trainee, failureChance, skipCharmForConservation)

                        // Determine if this item is actually useful right now.
                        // isBad items are also isQuick, but they must clear the condition-match gate; let the isBad clause own them.
                        val isUseful =
                            isStat ||
                                (isBad && trainee != null && (canHealActiveNegativeStatus(name, trainee) || isPendingPostEventCureItem(name))) ||
                                (isQuick && !isBad) ||
                                (
                                    isEnergy &&
                                        trainee != null &&
                                        (
                                            (
                                                trainingSelected != null &&
                                                    (
                                                        trainee.energy <= energyThresholdToUseEnergyItems ||
                                                            shouldConsiderEnergyItemForHighFailureTrain(trainingSelected) ||
                                                            (name == "Royal Kale Juice" && kaleJuiceRestRecoveryEligible(currentInventory, trainee.energy))
                                                    )
                                            ) ||
                                                (
                                                    trainingSelected == null &&
                                                        (
                                                            trainee.energy <= energyThresholdToUseEnergyItems ||
                                                                (name == "Royal Kale Juice" && kaleJuiceRestRecoveryEligible(currentInventory, trainee.energy))
                                                        )
                                                )
                                        )
                                ) ||
                                // We might want any energy item if not full.
                                (isMood && trainee != null && trainee.mood < Mood.GREAT) ||
                                (isMegaphone && trainee != null && trainingSelected != null && hasMegaphoneAvailableForTraining(trainingSelected, trainee, currentInventory)) ||
                                (isAnkleWeight && trainee != null && trainingSelected != null && isAnkleWeightEligibleForUse(name, trainingSelected, currentInventory)) ||
                                isCharm

                        isUseful
                    }.keys
                    .toMutableSet()
            } else {
                mutableSetOf()
            }

        if (remainingItemsOfInterest.isEmpty() && bInventorySynced) {
            MessageLog.i(TAG, "[TRACKBLAZER] No items of interest found in cached inventory and already synced. Skipping scan.")
        } else if (remainingItemsOfInterest.isNotEmpty()) {
            MessageLog.i(TAG, "[TRACKBLAZER] Items of interest for this pass: ${remainingItemsOfInterest.joinToString(", ")}.")
        }

        val itemsUsedWithReasons = mutableListOf<Pair<String, String>>()
        val itemNameMapInManage = mutableMapOf<Int, String>()
        // Snapshot energy at the start of the pass so the energy-item threshold gate stays
        // open after earlier items in the same pass raise `trainee.energy`. The greedy
        // selection in `isBestEnergyItemToUse` still drives which specific items are queued.
        val passStartEnergy = trainee?.energy ?: 0
        shopList.processItemsWithFallback(
            keyExtractor = { entry ->
                val name = shopList.getShopItemName(entry, ButtonSkillUp.checkDisabled(game.imageUtils, entry.bitmap) == true)
                if (name != null) itemNameMapInManage[entry.index] = name
                name
            },
        ) { entry ->
            val isDisabled = ButtonSkillUp.checkDisabled(game.imageUtils, entry.bitmap) == true
            val itemName = itemNameMapInManage[entry.index] ?: shopList.getShopItemName(entry, isDisabled)

            if (itemName != null) {
                Log.d(TAG, "[DEBUG] buyItems:: Detected item \"$itemName\" (Disabled: $isDisabled) at index ${entry.index}.")
                scannedItemsList.add(ScannedItem(entry, itemName, isDisabled))

                // Sync Inventory.
                val amount = shopList.getItemAmount(entry, isDisabled)
                nextInventory[itemName] = amount

                // Inline usage logic.
                if (!bDryRun) {
                    val isStat = shopList.statItemNames.contains(itemName)
                    val isBad = shopList.badConditionHealItemNames.contains(itemName)
                    val itemInfo = shopList.shopItems[itemName]
                    val isQuick = itemInfo != null && itemInfo.isQuickUsage

                    if (bQuickUseOnly) {
                        if (isQuick && !isDisabled) {
                            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Using quick-use item: \"$itemName\".", nextInventory)) {
                                itemsUsedCount++
                                val reason =
                                    when {
                                        isStat -> "Marked as quick-use."
                                        itemInfo?.category == "Bond" -> "Marked as quick-use."
                                        itemInfo?.category == "Get Good Conditions" -> "Acquired good condition: ${getStatusEffectName(itemName)}."
                                        else -> "Marked as quick-use."
                                    }
                                itemsUsedWithReasons.add(itemName to reason)
                            }
                        }
                    } else {
                        if (isStat && !isDisabled) {
                            var clicks = 0
                            while (true) {
                                val reason = "Marked as quick-use."
                                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing stat item: \"$itemName\".", nextInventory, recheck = clicks > 0, reason = reason)) {
                                    itemsUsedCount++
                                    clicks++
                                    itemsUsedWithReasons.add(itemName to reason)
                                    if (clicks >= 5) break
                                    game.wait(0.2)
                                } else {
                                    break
                                }
                            }
                        } else if (
                            isBad &&
                                trainee != null &&
                                (
                                    isPendingPostEventCureItem(itemName) ||
                                        (!isDisabled && trainee.currentNegativeStatuses.isNotEmpty())
                                )
                        ) {
                            val reason =
                                if (isPendingPostEventCureItem(itemName)) {
                                    "Post-event Slow Metabolism cure scheduled from prior turn."
                                } else {
                                    "Healed status effect: ${trainee.currentNegativeStatuses.joinToString(", ")}."
                                }
                            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing bad condition item: \"$itemName\".", nextInventory, reason = reason)) {
                                itemsUsedCount++
                                itemsUsedWithReasons.add(itemName to reason)
                                if (isPendingPostEventCureItem(itemName)) {
                                    pendingPostEventCureItem = null
                                }
                            }
                        } else if (isQuick && !isDisabled) {
                            val reason =
                                when {
                                    itemInfo?.category == "Bond" -> "Marked as quick-use."
                                    itemInfo?.category == "Get Good Conditions" -> "Acquired status effect: ${getStatusEffectName(itemName)}."
                                    else -> "Marked as quick-use."
                                }
                            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing quick-use item: \"$itemName\".", nextInventory, reason = reason)) {
                                itemsUsedCount++
                                itemsUsedWithReasons.add(itemName to reason)
                                if (itemName == "Energy Drink MAX") {
                                    trainee?.energy = (trainee?.energy ?: 100) + 5
                                }
                            }
                        } else if (trainee != null) {
                            // Handle Energy, Mood, Ankle Weights, Charm, Megaphones, etc.
                            val reason = handleInlineUsage(trainee, itemName, entry, isDisabled, trainingSelected, nextInventory, remainingItemsOfInterest, passStartEnergy)
                            if (reason != null) {
                                itemsUsedCount++
                                itemsUsedWithReasons.add(itemName to reason)
                            }
                        }
                    }
                }

                if (remainingItemsOfInterest.contains(itemName)) {
                    remainingItemsOfInterest.remove(itemName)
                }
            } else {
                MessageLog.w(TAG, "[WARN] manageInventoryItems:: Failed to detect item name at index ${entry.index}.")
            }

            // Early exit if we've seen all items of interest.
            // We only allow early exit if the inventory has already been fully synced.
            if (remainingItemsOfInterest.isEmpty() && bInventorySynced) {
                MessageLog.i(TAG, "[TRACKBLAZER] All items of interest processed. Exiting scan early.")
                wasEarlyExit = true
                true
            } else {
                false
            }
        }

        // Finalize Sync.
        if (!wasEarlyExit) {
            val scannedItemNames = scannedItemsList.map { it.itemName }.toSet()
            nextInventory.keys.forEach { name ->
                if (!scannedItemNames.contains(name) && (nextInventory[name] ?: 0) > 0) {
                    nextInventory[name] = 0
                }
            }
        }
        currentInventory = nextInventory.toMap()
        bInventorySynced = true

        // Log reasoning for item usage decisions made during this pass, incorporating the inventory summary.
        if (trainee != null || bDryRun) {
            val stateContext =
                if (trainee != null) {
                    val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
                    buildString {
                        val stateList = listOf("Energy=$initialEnergy%", "Mood=$initialMood", "Megaphone Turn=$initialMegaphoneTurnCounter", "Coins=$shopCoins")
                        appendLine("Current State: ${stateList.joinToString(", ")}")
                        if (trainingSelected != null) {
                            val failureInfo = if (failureChance > 0) " (Fail: $failureChance%)" else ""
                            append("Selected Training: $trainingSelected$failureInfo")
                        }
                    }.trimEnd()
                } else {
                    null
                }
            shopList.printItemUsageSummary(itemsUsedWithReasons, stateContext)
        }

        if (itemsUsedCount > 0 && !bDryRun) {
            confirmAndCloseItemDialog(itemsUsedCount)
        } else if (!bDryRun) {
            if (ButtonClose.click(game.imageUtils, tries = 30)) {
                game.wait(game.dialogWaitDelay)
            }
        }

        if (!bDryRun && pendingPostEventCureItem != null && pendingPostEventCureItemToUse() != null) {
            MessageLog.w(
                TAG,
                "[WARN] manageInventoryItems:: Scheduled post-event cure (${pendingPostEventCureItem}) was not queued this pass.",
            )
        }

        if (!bDryRun && !bStatSpecificItemsOnlyPass && trainee != null && trainingSelected != null &&
            failureMitigationChoiceForPass == FailureMitigationChoice.CHARM && !bUsedCharmToday
        ) {
            reconcileFailureMitigationAfterSkippedCharm(trainingSelected, nextInventory)
        }
    }

    /**
     * Map item names to their specific good status effect names.
     *
     * @param itemName The name of the item.
     * @return The status effect name.
     */
    private fun getStatusEffectName(itemName: String): String {
        return when (itemName) {
            "Pretty Mirror" -> "Charming ○"
            "Reporter's Binoculars" -> "Hot Topic"
            "Master Practice Guide" -> "Practice Perfect ○"
            "Scholar's Hat" -> "Fast Learner"
            else -> "null"
        }
    }

    /**
     * Handles usage of a specific item discovered during the scan loop.
     *
     * @param trainee Reference to the trainee's state.
     * @param itemName The name of the item detected.
     * @param entry The ScrollListEntry of the item.
     * @param isDisabled Whether the item is disabled in the UI.
     * @param trainingSelected The stat name of the selected training.
     * @param nextInventory The updated inventory map reflecting changes in this pass.
     * @param remainingItemsOfInterest The set of items we are still looking for.
     * @param passStartEnergy Trainee energy snapshotted at the start of the pass; used by the
     *   energy-item threshold gate so it does not close mid-pass after earlier items raise energy.
     * @return The specific reason why the item was used, or null if not used.
     */
    private fun handleInlineUsage(
        trainee: Trainee,
        itemName: String,
        entry: ScrollListEntry,
        isDisabled: Boolean,
        trainingSelected: StatName?,
        nextInventory: MutableMap<String, Int>,
        remainingItemsOfInterest: Set<String>,
        passStartEnergy: Int,
    ): String? {
        // Cupcakes captured before Royal Kale Juice was queued will read as disabled (mood was still GREAT at scan time). Bypass the
        // early-return when the flag is set so the recheck=true bitmap can decide; the game's dialog enables them once Juice is queued.
        val isCupcake = itemName == "Berry Sweet Cupcake" || itemName == "Plain Cupcake"
        if (isDisabled && !(isCupcake && bKaleJuiceQueuedThisPass)) {
            MessageLog.v(TAG, "[TRACKBLAZER] Item \"$itemName\" read as disabled in dialog, so skipping its usage.")
            return null
        }

        if (bStatSpecificItemsOnlyPass) {
            val megaphoneNames = listOf("Empowering Megaphone", "Motivating Megaphone", "Coaching Megaphone")
            val neededWeight = ankleWeightItemForStat(trainingSelected)
            if (itemName != neededWeight && itemName !in megaphoneNames) {
                return null
            }
        }

        // Ankle Weights Check.
        if (date.day >= 13 && trainingSelected != null) {
            val neededWeight = ankleWeightItemForStat(trainingSelected)
            if (itemName == neededWeight) {
                if (shouldSkipAnkleWeightForSummerReserve(itemName, nextInventory)) {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping $itemName: conserving for summer training (reserve floor: $ankleWeightSummerReserve per type).",
                    )
                    return null
                }
                if (shouldSkipAnkleWeightForLowGain(itemName, trainingSelected)) {
                    val selectedMainGain = selectedTrainingMainGain(trainingSelected, trainee)
                    val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping $itemName: selected $trainingSelected main gain ($selectedMainGain) below Charm floor ($minCharmGain) with failure ($failureChance%) above max.",
                    )
                    return null
                }
                val reason = "Boosting $trainingSelected training gains."
                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName via inline pass.", nextInventory, reason = reason)) {
                    markStatSpecificTrainingItemQueued(trainingSelected)
                    return reason
                }
            }
        }

        val failureChance = if (trainingSelected != null) training.trainingMap[trainingSelected]?.failureChance ?: 0 else 0
        if (!bStatSpecificItemsOnlyPass) {
        val skipTrainingEffectItems = trainingSelected != null && shouldConserveTrainingEffectItems(trainingSelected, trainee)

        // Good-Luck Charm Check (mutually exclusive with high-failure energy mitigation on the same training turn).
        if (itemName == "Good-Luck Charm") {
            when {
                date.day < 13 -> return null
                trainingSelected == null -> return null
                failureMitigationChoiceForPass == FailureMitigationChoice.ENERGY -> {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping Good-Luck Charm: high-failure energy item preferred over charm this turn.",
                    )
                    return null
                }
                !shouldQueueGoodLuckCharmForTraining(trainingSelected, trainee, failureChance, skipTrainingEffectItems) -> {
                    if (!isClimaxCharmTrainingActive() && !training.isLuckyCharmAllowedForSelection(trainingSelected)) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Skipping Good-Luck Charm: low-priority Wit charm bypass is disabled and $trainingSelected is selected.")
                    } else if (shouldConserveTrainingEffectItems(trainingSelected, trainee)) {
                        val selectedMainGain = selectedTrainingMainGain(trainingSelected, trainee)
                        MessageLog.i(
                            TAG,
                            "[TRACKBLAZER] Skipping Good-Luck Charm: mood=${trainee.mood}, selected $trainingSelected main gain ($selectedMainGain) below floor ($lowMainStatGainItemFloor). Conserving Charm for a higher-gain turn.",
                        )
                    }
                    return null
                }
                else -> {
                    val reason = "Setting training failure chance to 0%."
                    if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing Good-Luck Charm via inline pass.", nextInventory, reason = reason)) {
                        bUsedCharmToday = true
                        return reason
                    }
                }
            }
            trainingSelected?.let { reconcileFailureMitigationAfterSkippedCharm(it, nextInventory) }
        }

        // Charm queued this pass blocks extra energy items (charm zeros failure; energy is applied after training).
        val charmBeingUsedThisTurn =
            bUsedCharmToday ||
                (
                    trainingSelected != null &&
                        shouldQueueGoodLuckCharmForTraining(trainingSelected, trainee, failureChance, skipTrainingEffectItems)
                )

        // High-failure energy train (mutually exclusive with Good-Luck Charm on the same training turn).
        if (
            enableEnergyItemForHighFailureTraining &&
                failureMitigationChoiceForPass == FailureMitigationChoice.ENERGY &&
                !charmBeingUsedThisTurn &&
                isFailureMitigationEnergyItem(itemName) &&
                trainingSelected != null &&
                date.day >= 13
        ) {
            val plan = getFailureMitigationEnergyPlanForPass(trainingSelected, trainee, nextInventory, failureChance)
            if (plan != null && itemName in plan) {
                val neededInPlan = plan.count { it == itemName }
                val queuedInPlan = failureMitigationEnergyQueuedCounts[itemName] ?: 0
                if (queuedInPlan < neededInPlan) {
                    val reservedHere = reservedEnergyUnitsFor(itemName, nextInventory)
                    if (reservedHere > 0 && (nextInventory[itemName] ?: 0) <= reservedHere) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Conserving $itemName for emergency race recovery (reserve floor: $energyItemReserveCount).")
                    } else {
                        val reason = failureMitigationEnergyPlanReason(plan, trainingSelected, trainee, failureChance)
                        var clicks = 0
                        while ((failureMitigationEnergyQueuedCounts[itemName] ?: 0) < neededInPlan) {
                            val gain = energyGains[itemName] ?: 0
                            if (
                                clickItemPlusButton(
                                    itemName,
                                    entry,
                                    "[TRACKBLAZER] Queuing $itemName for high-failure training (Energy: ${trainee.energy}%, Gain: +$gain).",
                                    nextInventory,
                                    recheck = clicks > 0,
                                    reason = reason,
                                )
                            ) {
                                val oldEnergy = trainee.energy
                                trainee.energy = (trainee.energy + gain).coerceAtMost(100)
                                if (itemName == "Royal Kale Juice") {
                                    trainee.mood = trainee.mood.decrement()
                                    bKaleJuiceQueuedThisPass = true
                                }
                                bUsedEnergyItemThisPass = true
                                failureMitigationEnergyQueuedCounts[itemName] = (failureMitigationEnergyQueuedCounts[itemName] ?: 0) + 1
                                clicks++
                                MessageLog.i(TAG, "[TRACKBLAZER] Trainee energy updated: $oldEnergy% -> ${trainee.energy}%.")
                                game.wait(0.2)
                            } else {
                                break
                            }
                        }
                        if (clicks > 0) {
                            return reason
                        }
                    }
                }
            }
        }

        // Energy Items Check (low-energy threshold — with or without a training pick).
        if (
            !charmBeingUsedThisTurn &&
                passStartEnergy <= energyThresholdToUseEnergyItems &&
                shopList.energyItemNames.contains(itemName)
        ) {
            if (
                trainingSelected != null &&
                    itemName == "Vita 65" &&
                    !canUseFailureMitigationPoolItem(itemName, nextInventory, trainingSelected, trainee, failureChance)
            ) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Conserving $itemName: failure-mitigation pool reserve ($failureMitigationPoolReserve across Charm/Vita 65/Kale).",
                )
                return null
            }
            // Conservation: hold back up to `energyItemReserveCount` units across the conservation order (lowest-tier first) for emergency race recovery.
            val reservedHere = reservedEnergyUnitsFor(itemName, nextInventory)
            if (reservedHere > 0 && (nextInventory[itemName] ?: 0) <= reservedHere) {
                MessageLog.i(TAG, "[TRACKBLAZER] Conserving $itemName for emergency race recovery (reserve floor: $energyItemReserveCount).")
                return null
            }

            if (isBestEnergyItemToUse(trainee, itemName, nextInventory, remainingItemsOfInterest)) {
                val gain = energyGains[itemName] ?: 0
                val reason = "Restored energy (current: ${trainee.energy}%, pass start: $passStartEnergy%) because it fell below the $energyThresholdToUseEnergyItems% threshold."
                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName for use (Energy: ${trainee.energy}%, Gain: +$gain).", nextInventory, reason = reason)) {
                    val oldEnergy = trainee.energy
                    trainee.energy = (trainee.energy + gain).coerceAtMost(100)
                    bUsedEnergyItemThisPass = true
                    MessageLog.i(TAG, "[TRACKBLAZER] Trainee energy updated: $oldEnergy% -> ${trainee.energy}%.")
                    return reason
                }
            }
        }

        // Royal Kale Juice Check (also skipped when Charm is being used or already queued for high-failure train).
        if (!charmBeingUsedThisTurn && !bUsedEnergyItemThisPass && itemName == "Royal Kale Juice") {
            val hasMoodItems = nextInventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
            val surplusKaleRest = kaleJuiceRestRecoveryEligible(nextInventory, passStartEnergy)
            val moodConditionMet = trainee.energy <= 20 || hasMoodItems || trainee.mood == Mood.AWFUL || surplusKaleRest
            if (
                trainingSelected != null &&
                    !canUseFailureMitigationPoolItem(itemName, nextInventory, trainingSelected, trainee, failureChance)
            ) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Conserving Royal Kale Juice: failure-mitigation pool reserve ($failureMitigationPoolReserve across Charm/Vita 65/Kale).",
                )
                return null
            }
            val shouldUse = isBestEnergyItemToUse(trainee, itemName, nextInventory, remainingItemsOfInterest) && moodConditionMet

            if (shouldUse) {
                val oldEnergy = trainee.energy
                val reason =
                    when {
                        surplusKaleRest ->
                            "Restored energy (current: $oldEnergy%, pass start: $passStartEnergy%) using surplus Royal Kale Juice (>1 in stock) to avoid rest/recovery."
                        oldEnergy <= 20 ->
                            "Restored energy (current: $oldEnergy%) as a last resort (below 20%)."
                        else ->
                            "Restored energy (current: $oldEnergy%) while having mood recovery items available to offset the Mood decrease."
                    }
                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName for use (Energy: ${trainee.energy}%, Mood: ${trainee.mood}).", nextInventory, reason = reason)) {
                    val oldMood = trainee.mood
                    trainee.energy = (trainee.energy + 100).coerceAtMost(100)
                    trainee.mood = trainee.mood.decrement()
                    bKaleJuiceQueuedThisPass = true
                    MessageLog.i(TAG, "[TRACKBLAZER] Trainee energy and mood updated: $oldEnergy% -> ${trainee.energy}%, $oldMood -> ${trainee.mood}.")
                    return reason
                }
            }
        }

        // Mood Items Check.
        // The Kale-Juice-queued clause fires a cupcake in the same pass to offset the -1 mood penalty. Without it the existing
        // `mood <= NORMAL && energy < 70` gate stays shut after Kale Juice (mood drops to GOOD, energy jumps to 100), wasting the reserve.
        val moodDroppedByKaleJuice = isCupcake && bKaleJuiceQueuedThisPass
        val shouldUseMoodItem = (trainee.mood <= Mood.NORMAL && trainee.energy < 70) || moodDroppedByKaleJuice
        if (shouldUseMoodItem && isCupcake) {
            // Conservation: hold back `cupcakeReserveCount` cupcakes (Plain preferred) so Royal Kale Juice's -1 mood penalty can be offset.
            // Bypass the reserve when Kale Juice was queued in this pass: the reserved-for event is happening right now, so spend it.
            if (cupcakeReserveCount > 0 && !bKaleJuiceQueuedThisPass) {
                val plainCount = nextInventory["Plain Cupcake"] ?: 0
                val berryCount = nextInventory["Berry Sweet Cupcake"] ?: 0
                val totalCupcakes = plainCount + berryCount
                val shouldConserve =
                    (itemName == "Plain Cupcake" && plainCount <= cupcakeReserveCount) ||
                        (itemName == "Berry Sweet Cupcake" && totalCupcakes <= cupcakeReserveCount && plainCount == 0)
                if (shouldConserve) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Conserving $itemName for potential Royal Kale Juice usage (reserve floor: $cupcakeReserveCount).")
                    return null
                }
            }

            val reason =
                if (moodDroppedByKaleJuice) {
                    "Offsetting Royal Kale Juice's -1 mood penalty (mood: ${trainee.mood} post-Juice)."
                } else {
                    "Recovering mood (current: ${trainee.mood}, energy: ${trainee.energy}% < 70%)."
                }
            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName for mood recovery.", nextInventory, recheck = moodDroppedByKaleJuice, reason = reason)) {
                val oldMood = trainee.mood
                trainee.mood = if (itemName == "Berry Sweet Cupcake") trainee.mood.increment().increment() else trainee.mood.increment()
                if (moodDroppedByKaleJuice) bKaleJuiceQueuedThisPass = false
                MessageLog.i(TAG, "[TRACKBLAZER] Trainee mood updated: $oldMood -> ${trainee.mood}.")
                return reason
            }
        }

        } // end !bStatSpecificItemsOnlyPass (charm/energy/mood/stat/bad-condition paths)

        // Megaphone Check.
        val megaphoneNames = listOf("Empowering Megaphone", "Motivating Megaphone", "Coaching Megaphone")
        if (trainingSelected != null && megaphoneNames.contains(itemName)) {
            val surplusBurn = isMegaphoneSurplusBurnMode(nextInventory)
            val preferredSurplusMegaphone =
                if (surplusBurn) preferredSurplusBurnMegaphone(nextInventory, trainingSelected, trainee) else null

            if (!canQueueMegaphone(itemName, trainee, trainingSelected, nextInventory)) {
                if (itemName == "Empowering Megaphone" &&
                    trainee.activeMegaphoneType == "Motivating Megaphone" &&
                    trainee.megaphoneTurnCounter > 0 &&
                    !date.isSummer()
                ) {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping Empowering Megaphone: Motivating Megaphone still active (${trainee.megaphoneTurnCounter} turns). Use 60% during summer or after 40% expires.",
                    )
                } else if (
                    surplusBurn &&
                        itemName == "Motivating Megaphone" &&
                        trainee.activeMegaphoneType == "Coaching Megaphone" &&
                        trainee.megaphoneTurnCounter > 0
                ) {
                    val selectedMainGain = selectedTrainingMainGain(trainingSelected)
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping Motivating Megaphone: Coaching Megaphone still active and main gain ($selectedMainGain) below Motivating floor ($motivatingMegaphoneMinStatGain).",
                    )
                }
                return null
            }
            // When mood is below NORMAL, the mood multiplier caps gain. Megaphones multiply gain across multiple
            // turns, so squandering one on a low-gain selected training is worse than conserving for a better turn.
            if (shouldConserveTrainingEffectItems(trainingSelected, trainee)) {
                val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Skipping $itemName: mood=${trainee.mood}, selected $trainingSelected main gain ($selectedMainGain) below floor ($lowMainStatGainItemFloor). Conserving Megaphone for a higher-gain turn.",
                )
                return null
            }

            if (surplusBurn) {
                if (itemName == "Empowering Megaphone") {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Skipping Empowering Megaphone: summer reserve quota met ($megaphoneSummerReserveCount total); surplus burn uses Coaching/Motivating only.",
                    )
                    return null
                }
                if (preferredSurplusMegaphone == null || itemName != preferredSurplusMegaphone) {
                    return null
                }
            } else {
                // Check if there is a better megaphone in inventory that we haven't seen yet OR that we know is disabled.
                val betterMegaphones =
                    when (itemName) {
                        "Motivating Megaphone" -> listOf("Empowering Megaphone")
                        "Coaching Megaphone" -> listOf("Empowering Megaphone", "Motivating Megaphone")
                        else -> emptyList()
                    }

                val hasBetterAvailable =
                    betterMegaphones.any { better ->
                        (nextInventory[better] ?: 0) > 0 &&
                            isMegaphoneEligibleForUse(better, trainingSelected, nextInventory, trainee)
                    }

                if (hasBetterAvailable) {
                    return null
                }
            }

            if (shouldSkipMegaphoneForSummerReserve(itemName, nextInventory)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Skipping $itemName: conserving for summer training (reserve floor: $megaphoneSummerReserveCount total).",
                )
                return null
            }
            if (shouldSkipMegaphoneForLowGain(itemName, trainingSelected, nextInventory, trainee)) {
                val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
                val threshold =
                    when (itemName) {
                        "Coaching Megaphone" -> coachingMegaphoneMinStatGain
                        "Motivating Megaphone" -> motivatingMegaphoneMinStatGain
                        "Empowering Megaphone" -> empoweringMegaphoneMinStatGain
                        else -> 0
                    }
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Skipping $itemName: selected $trainingSelected main gain ($selectedMainGain) below minimum ($threshold). Conserving Megaphone for a higher-gain turn.",
                )
                return null
            }

            val reason =
                if (surplusBurn) {
                    when (itemName) {
                        "Coaching Megaphone" -> "Summer reserve quota met: using surplus Coaching Megaphone (min gain ignored)."
                        "Motivating Megaphone" ->
                            if (trainee.activeMegaphoneType == "Coaching Megaphone" && trainee.megaphoneTurnCounter > 0) {
                                "Summer reserve quota met: upgrading Coaching → Motivating (main gain meets Motivating floor)."
                            } else {
                                "Summer reserve quota met: using surplus Motivating Megaphone (no Coaching available)."
                            }
                        else -> "Increasing training gains for the next few turns."
                    }
                } else {
                    "Increasing training gains for the next few turns."
                }
            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing best available megaphone: \"$itemName\".", nextInventory, reason = reason)) {
                trainee.activeMegaphoneType = itemName
                trainee.megaphoneTurnCounter =
                    when (itemName) {
                        "Empowering Megaphone" -> 2
                        "Motivating Megaphone" -> 3
                        "Coaching Megaphone" -> 4
                        else -> 0
                    }
                if (trainingSelected != null) {
                    refreshFailureMitigationChoiceAfterMegaphone(trainingSelected, trainee, nextInventory)
                    markStatSpecificTrainingItemQueued(trainingSelected)
                }
                return reason
            }
        }

        return null
    }

    /**
     * Returns how many units of `itemName` are currently being held back as part of the energy reserve floor.
     * Reserves are allocated across `energyItemConservationOrder` lowest-tier-first up to `energyItemReserveCount` total units.
     */
    private fun reservedEnergyUnitsFor(itemName: String, inventory: Map<String, Int>): Int {
        if (bForceUseReservedItem || energyItemReserveCount <= 0) return 0
        var remaining = energyItemReserveCount
        for (name in energyItemConservationOrder) {
            if (remaining <= 0) break
            val count = inventory[name] ?: 0
            val reservedHere = minOf(count, remaining)
            if (name == itemName) return reservedHere
            remaining -= reservedHere
        }
        return 0
    }

    /**
     * Returns the energy item name currently being conserved as the last-resort emergency-race-recovery stash.
     *
     * Mirrors the conservation logic inside `isBestEnergyItemToUse` so the dialog-open gate predicts the same outcome the dialog scan would reach.
     *
     * @param inventory The inventory snapshot to evaluate.
     * @return The conserved item name, or `null` if conservation is bypassed or no conservable item is in inventory.
     */
    private fun getConservedEnergyItem(inventory: Map<String, Int>): String? {
        if (bForceUseReservedItem || energyItemReserveCount <= 0) return null
        return energyItemConservationOrder.firstOrNull { (inventory[it] ?: 0) > 0 }
    }

    /**
     * Returns true when training-effect items (Megaphones, Good-Luck Charm) should be conserved this turn
     * because the trainee mood is below NORMAL AND the selected training's main stat gain is below the
     * user-configured floor. Mirrors the inline conservation checks in `handleInlineUsage()` so the
     * Training Items dialog can be short-circuited upfront when these items would be skipped anyway.
     *
     * @param trainingSelected The training the bot is about to execute (null = no selection).
     * @param trainee The current trainee snapshot (mood is read).
     * @return True if Megaphone/Charm should be skipped this turn.
     */
    private fun shouldConserveTrainingEffectItems(trainingSelected: StatName?, trainee: Trainee?): Boolean {
        if (isClimaxCharmTrainingActive()) return false
        if (trainingSelected == null || trainee == null) return false
        if (trainee.mood >= Mood.NORMAL) return false
        val selectedMainGain = selectedTrainingMainGain(trainingSelected, trainee)
        return selectedMainGain < lowMainStatGainItemFloor
    }

    /** Returns the user-configured minimum main stat gain threshold for the given ankle weight item. */
    private fun ankleWeightMinStatGainThreshold(itemName: String): Int =
        when (itemName) {
            "Speed Ankle Weights" -> speedAnkleWeightMinStatGain
            "Stamina Ankle Weights" -> staminaAnkleWeightMinStatGain
            "Power Ankle Weights" -> powerAnkleWeightMinStatGain
            "Guts Ankle Weights" -> gutsAnkleWeightMinStatGain
            else -> 0
        }

    /**
     * Returns true when an ankle weight should be skipped because inventory is at or below the summer reserve floor.
     * Ignored during summer training.
     */
    private fun shouldSkipAnkleWeightForSummerReserve(itemName: String, inventory: Map<String, Int>): Boolean {
        if (date.isSummer() || ankleWeightSummerReserve <= 0) return false
        return (inventory[itemName] ?: 0) <= ankleWeightSummerReserve
    }

    /**
     * Returns true when an ankle weight should be skipped because main stat gain is below the Charm floor on a risky training.
     * Mirrors Good-Luck Charm min-gain rules outside summer (failure above max → need minCharmGain). Ignored during summer.
     * Uses raw OCR gain only — active megaphone bonus is not included.
     */
    private fun shouldSkipAnkleWeightForLowGain(itemName: String, trainingSelected: StatName?): Boolean {
        if (trainingSelected == null) return true
        if (date.isSummer()) return false
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        if (failureChance <= training.getMaximumFailureChance()) return false
        if (minCharmGain <= 0) return false
        val mainGain = selectedTrainingMainGain(trainingSelected)
        return mainGain < minCharmGain
    }

    /** Returns true when an ankle weight may be used this turn (passes summer reserve and min-gain gates). */
    private fun isAnkleWeightEligibleForUse(itemName: String, trainingSelected: StatName?, inventory: Map<String, Int>): Boolean =
        !shouldSkipAnkleWeightForSummerReserve(itemName, inventory) && !shouldSkipAnkleWeightForLowGain(itemName, trainingSelected)

    /**
     * Returns how many units of `itemName` are held back as part of the megaphone summer reserve.
     * Reserves are allocated across `megaphoneSummerReserveOrder` best-tier-first up to `megaphoneSummerReserveCount` total units.
     */
    private fun reservedMegaphoneUnitsFor(itemName: String, inventory: Map<String, Int>): Int =
        reservedMegaphoneBreakdown(inventory)[itemName] ?: 0

    private fun reservedMegaphoneBreakdown(inventory: Map<String, Int>): Map<String, Int> =
        Companion.reservedMegaphoneBreakdown(inventory, megaphoneSummerReserveCount, megaphoneSummerReserveOrder)

    /**
     * Returns true when a megaphone should be skipped because all non-reserved stock is held for summer training.
     * Ignored during summer training.
     */
    private fun shouldSkipMegaphoneForSummerReserve(itemName: String, inventory: Map<String, Int>): Boolean {
        if (date.isSummer() || megaphoneSummerReserveCount <= 0) return false
        val reservedHere = reservedMegaphoneUnitsFor(itemName, inventory)
        return reservedHere > 0 && (inventory[itemName] ?: 0) <= reservedHere
    }

    /** Returns true when a megaphone may be used this turn (passes summer reserve and min-gain gates). */
    private fun isMegaphoneEligibleForUse(
        itemName: String,
        trainingSelected: StatName?,
        inventory: Map<String, Int>,
        trainee: Trainee? = null,
    ): Boolean =
        canQueueMegaphone(itemName, trainee ?: this.trainee, trainingSelected, inventory) &&
            !shouldSkipMegaphoneForSummerReserve(itemName, inventory) &&
            !shouldSkipMegaphoneForLowGain(itemName, trainingSelected, inventory, trainee)

    private fun totalMegaphoneStock(inventory: Map<String, Int>): Int =
        megaphoneSummerReserveOrder.sumOf { inventory[it] ?: 0 }

    /** True when total megaphone stock meets the summer reserve target (reserve quota satisfied). */
    private fun hasMegaphoneSummerReserveQuotaMet(inventory: Map<String, Int>): Boolean {
        if (date.isSummer() || megaphoneSummerReserveCount <= 0) return false
        return totalMegaphoneStock(inventory) >= megaphoneSummerReserveCount
    }

    private fun availableMegaphoneUnits(itemName: String, inventory: Map<String, Int>): Int {
        val count = inventory[itemName] ?: 0
        return (count - reservedMegaphoneUnitsFor(itemName, inventory)).coerceAtLeast(0)
    }

    /** Outside summer, after the reserve quota is met, spend surplus on Coaching first (then Motivating; never Empowering). */
    private fun isMegaphoneSurplusBurnMode(inventory: Map<String, Int>): Boolean =
        hasMegaphoneSummerReserveQuotaMet(inventory) && !date.isSummer()

    /**
     * Megaphone to queue in surplus-burn mode, or null when none should fire.
     * Reserve composition drives tier choice:
     * - 1×60% + 1×40% held for summer → spend surplus 40% before 20%.
     * - 2×60% held for summer → spend 40% only when 2+ surplus 40% exist; otherwise 20%.
     * Coaching ignores min gain; Motivating upgrade from active Coaching still requires the Motivating floor.
     */
    private fun preferredSurplusBurnMegaphone(
        inventory: Map<String, Int>,
        trainingSelected: StatName,
        trainee: Trainee,
    ): String? {
        if (!isMegaphoneSurplusBurnMode(inventory)) return null

        val coachingActive =
            trainee.megaphoneTurnCounter > 0 && trainee.activeMegaphoneType == "Coaching Megaphone"
        val mainGain = if (coachingActive) selectedTrainingMainGain(trainingSelected) else 0
        val motivatingUpgradeAllowed =
            motivatingMegaphoneMinStatGain <= 0 || mainGain >= motivatingMegaphoneMinStatGain

        return Companion.pickSurplusBurnMegaphone(
            inventory = inventory,
            reserveCount = megaphoneSummerReserveCount,
            reserveOrder = megaphoneSummerReserveOrder,
            activeMegaphoneType = trainee.activeMegaphoneType,
            megaphoneTurnCounter = trainee.megaphoneTurnCounter,
            motivatingUpgradeAllowed = motivatingUpgradeAllowed,
        )
    }

    private fun hasMegaphoneAvailableForTraining(
        trainingSelected: StatName,
        trainee: Trainee,
        inventory: Map<String, Int>,
    ): Boolean {
        if (shouldConserveTrainingEffectItems(trainingSelected, trainee)) return false
        if (isMegaphoneSurplusBurnMode(inventory)) {
            return preferredSurplusBurnMegaphone(inventory, trainingSelected, trainee) != null
        }
        return megaphoneSummerReserveOrder.any { name ->
            (inventory[name] ?: 0) > 0 &&
                canQueueMegaphone(name, trainee, trainingSelected, inventory) &&
                isMegaphoneEligibleForUse(name, trainingSelected, inventory, trainee)
        }
    }

    /**
     * Returns true when a megaphone may be queued this turn.
     * Empowering (60%) is blocked while Motivating (40%) is still active outside summer; summer always allows 60% upgrades.
     * Surplus-burn mode blocks Empowering entirely and may upgrade Coaching → Motivating when gain qualifies.
     */
    private fun canQueueMegaphone(
        itemName: String,
        trainee: Trainee,
        trainingSelected: StatName? = null,
        inventory: Map<String, Int> = currentInventory,
    ): Boolean {
        if (isMegaphoneSurplusBurnMode(inventory) && itemName == "Empowering Megaphone") {
            return false
        }
        return when (itemName) {
            "Empowering Megaphone" ->
                when {
                    trainee.megaphoneTurnCounter == 0 -> true
                    trainee.activeMegaphoneType == "Empowering Megaphone" -> false
                    date.isSummer() -> true
                    else -> trainee.activeMegaphoneType != "Motivating Megaphone"
                }
            "Motivating Megaphone" ->
                when {
                    trainee.megaphoneTurnCounter == 0 -> true
                    isMegaphoneSurplusBurnMode(inventory) &&
                        trainee.activeMegaphoneType == "Coaching Megaphone" &&
                        trainingSelected != null -> {
                        val mainGain = selectedTrainingMainGain(trainingSelected)
                        motivatingMegaphoneMinStatGain <= 0 || mainGain >= motivatingMegaphoneMinStatGain
                    }
                    else -> false
                }
            else -> trainee.megaphoneTurnCounter == 0
        }
    }

    /** Decrements the active megaphone duration once per turn (race prep path or executeAction). */
    private fun finishTurnMegaphoneDecrement() {
        if (megaphoneDecrementedThisTurn) {
            return
        }
        decrementMegaphoneTurnCounter(trainee)
        megaphoneDecrementedThisTurn = true
    }

    /** Decrements the active megaphone duration at end of turn; clears type when expired. */
    private fun decrementMegaphoneTurnCounter(trainee: Trainee) {
        if (trainee.megaphoneTurnCounter <= 0) return
        trainee.megaphoneTurnCounter--
        if (trainee.megaphoneTurnCounter == 0) {
            trainee.activeMegaphoneType = null
        }
        MessageLog.i(
            TAG,
            "[TRACKBLAZER] Megaphone duration reduced. Active: ${trainee.activeMegaphoneType ?: "none"}. Turns remaining: ${trainee.megaphoneTurnCounter}.",
        )
    }

    /**
     * Returns true when a megaphone should be skipped because the selected training's main stat gain is below the
     * user-configured threshold for that megaphone type. Disabled when the threshold is 0 or during summer training.
     *
     * @param itemName The megaphone item name being evaluated.
     * @param trainingSelected The training the bot is about to execute (null = no selection).
     * @return True if the megaphone should be conserved this turn.
     */
    private fun shouldSkipMegaphoneForLowGain(
        itemName: String,
        trainingSelected: StatName?,
        inventory: Map<String, Int> = currentInventory,
        trainee: Trainee? = null,
    ): Boolean {
        if (trainingSelected == null) return true
        if (date.isSummer()) return false

        if (isMegaphoneSurplusBurnMode(inventory)) {
            when (itemName) {
                "Coaching Megaphone" -> return false
                "Empowering Megaphone" -> return true
                "Motivating Megaphone" -> {
                    val coachingActive =
                        trainee != null &&
                            trainee.megaphoneTurnCounter > 0 &&
                            trainee.activeMegaphoneType == "Coaching Megaphone"
                    if (!coachingActive) return false
                    if (motivatingMegaphoneMinStatGain <= 0) return false
                    val selectedMainGain = selectedTrainingMainGain(trainingSelected)
                    return selectedMainGain < motivatingMegaphoneMinStatGain
                }
            }
        }

        val threshold =
            when (itemName) {
                "Coaching Megaphone" -> coachingMegaphoneMinStatGain
                "Motivating Megaphone" -> motivatingMegaphoneMinStatGain
                "Empowering Megaphone" -> empoweringMegaphoneMinStatGain
                else -> 0
            }
        if (threshold <= 0) return false

        val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
        return selectedMainGain < threshold
    }

    /**
     * Returns true when the given heal item targets at least one of the trainee's currently active negative statuses.
     * Miracle Cure heals every status; every other entry in `badConditionMap` heals exactly one specific status.
     * Used to short-circuit the Training Items dialog when no inventory item can actually clear an active condition.
     *
     * @param itemName The name of the item to check.
     * @param trainee The current trainee snapshot (currentNegativeStatuses is read).
     * @return True if the item can heal an active negative status; false otherwise.
     */
    private fun canHealActiveNegativeStatus(itemName: String, trainee: Trainee): Boolean {
        if (itemName == "Miracle Cure") return true
        val target = badConditionMap[itemName] ?: return false
        return trainee.currentNegativeStatuses.contains(target)
    }

    /**
     * Orchestrates the usage of items based on dynamic conditions and updates internal inventory.
     *
     * @param trainee Reference to the trainee's state.
     * @param trainingSelected The stat name of the selected training to help with item usage (e.g. ankle weights).
     */
    private fun useItems(trainee: Trainee, trainingSelected: StatName? = null) {
        if (date.day < 13) return

        val needSync = !bInventorySynced
        val hasEnergyItems =
            currentInventory.any { (name, count) ->
                val available = (count - reservedEnergyUnitsFor(name, currentInventory)).coerceAtLeast(0)
                available > 0 && shopList.energyItemNames.contains(name)
            } ||
                ((currentInventory["Royal Kale Juice"] ?: 0) > 0)
        val hasMoodItems = currentInventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
        val hasBadConditionItems =
            currentInventory.any { (name, count) ->
                count > 0 && shopList.badConditionHealItemNames.contains(name) &&
                    (canHealActiveNegativeStatus(name, trainee) || isPendingPostEventCureItem(name))
            }
        val hasPendingPostEventCure = pendingPostEventCureItemToUse() != null
        val hasStatItems = currentInventory.any { (name, count) -> count > 0 && shopList.statItemNames.contains(name) }

        val skipTrainingEffectItems = shouldConserveTrainingEffectItems(trainingSelected, trainee)
        val hasMegaphones =
            !skipTrainingEffectItems &&
                trainingSelected != null &&
                hasMegaphoneAvailableForTraining(trainingSelected, trainee, currentInventory)
        val neededAnkleWeight = ankleWeightItemForStat(trainingSelected)
        val hasAnkleWeights =
            trainingSelected != null &&
                neededAnkleWeight.isNotEmpty() &&
                (currentInventory[neededAnkleWeight] ?: 0) > 0 &&
                isAnkleWeightEligibleForUse(neededAnkleWeight, trainingSelected, currentInventory)
        if (trainingSelected != null) {
            failureMitigationChoiceForPass = resolveFailureMitigationChoice(trainingSelected, trainee, currentInventory)
            preReconcileCharmMitigationIfBlocked(trainingSelected, trainee)
        } else {
            failureMitigationChoiceForPass = FailureMitigationChoice.NONE
        }

        val failureChance = if (trainingSelected != null) training.trainingMap[trainingSelected]?.failureChance ?: 0 else 0
        val hasCharm =
            trainingSelected != null &&
                shouldQueueGoodLuckCharmForTraining(trainingSelected, trainee, failureChance, skipTrainingEffectItems)
        val highFailureEnergyTrain = failureMitigationChoiceForPass == FailureMitigationChoice.ENERGY

        val potentialUse =
            shouldTryEnergyRecoveryItems(trainee.energy) ||
                (trainingSelected != null && kaleJuiceRestRecoveryEligible(currentInventory, trainee.energy)) ||
                highFailureEnergyTrain ||
                hasPendingPostEventCure ||
                (trainee.mood <= Mood.NORMAL && trainee.energy < 70 && hasMoodItems) ||
                (trainee.currentNegativeStatuses.isNotEmpty() && hasBadConditionItems) ||
                hasStatItems ||
                hasMegaphones ||
                hasAnkleWeights ||
                hasCharm

        if (needSync || potentialUse) {
            val reasons = mutableListOf<String>()
            if (needSync) reasons.add("Sync needed")
            if (shouldTryEnergyRecoveryItems(trainee.energy)) reasons.add("Low energy")
            if (trainingSelected != null && kaleJuiceRestRecoveryEligible(currentInventory, trainee.energy)) reasons.add("Surplus Kale recovery")
            if (hasPendingPostEventCure) reasons.add("Scheduled post-event cure")
            if (highFailureEnergyTrain) reasons.add("High-failure energy train")
            if (trainee.mood <= Mood.NORMAL && trainee.energy < 70 && hasMoodItems) reasons.add("Low mood")
            if (trainee.currentNegativeStatuses.isNotEmpty() && hasBadConditionItems) reasons.add("Bad conditions")
            if (hasStatItems) reasons.add("Stat items available")
            if (hasMegaphones) reasons.add("Megaphone available")
            if (hasAnkleWeights) reasons.add("Ankle weights available")
            if (hasCharm) reasons.add("Good-luck charm available")

            MessageLog.i(TAG, "[TRACKBLAZER] Opening Training Items dialog (${reasons.joinToString(", ")})...")
            if (shopList.openTrainingItemsDialog()) {
                manageInventoryItems(trainee, trainingSelected)
            }
        } else {
            MessageLog.i(TAG, "[TRACKBLAZER] Skipping Training Items dialog as no relevant items are in the cached inventory.")
        }
    }

    /**
     * Returns a formatted summary of the current inventory categorized with item amounts.
     *
     * @param withDividers If true, includes the standard "Current Inventory" dividers and footer.
     * @return Formatted inventory summary string.
     */
    fun getInventorySummary(withDividers: Boolean = false): String {
        // Group items by category from the central shopItems mapping.
        val inventoryByCategory =
            currentInventory.filter { it.value > 0 }.keys.groupBy { itemName ->
                shopList.shopItems[itemName]?.category ?: "Other"
            }

        val summary =
            if (withDividers) {
                StringBuilder("\n============== Current Inventory ==============\n")
            } else {
                StringBuilder("\n[Current Inventory]\n")
            }

        var hasItems = false

        // Sort categories to maintain consistent order (Stats first, then others).
        val categoryOrder = listOf("Stats", "Energy and Motivation", "Bond", "Get Good Conditions", "Heal Bad Conditions", "Training Facilities", "Training Effects", "Races")
        val sortedCategories =
            inventoryByCategory.keys.sortedWith(
                compareBy { category ->
                    val index = categoryOrder.indexOf(category)
                    if (index == -1) categoryOrder.size else index
                },
            )

        sortedCategories.forEach { category ->
            val items = inventoryByCategory[category] ?: emptyList()
            if (items.isNotEmpty()) {
                summary.append("\n$category\n")
                items.sorted().forEach { name ->
                    summary.append("- $name: ${currentInventory[name]}\n")
                }
                hasItems = true
            }
        }

        if (!hasItems) {
            if (bInventorySynced) {
                summary.append("\nInventory is empty.\n")
            } else {
                summary.append("\nInventory has not been scanned yet.\n")
            }
        }

        if (withDividers) {
            summary.append("\n===============================================")
        }

        return summary.toString()
    }

    /**
     * Determines if using the current energy item is part of the best possible combination of available energy items.
     * This follows a greedy approach to maximize energy gain, allowing a small overshoot above 100% so that a larger
     * combined gain (e.g. Vita 65 + Vita 40 = 105) is preferred over a strictly-under-100 combination (e.g. 65 + 20 = 85).
     *
     * @param trainee The trainee's current state.
     * @param itemName The name of the item being considered.
     * @param nextInventory The current inventory counts reflecting changes in this pass.
     * @param remainingItemsOfInterest The set of items we still expect to encounter in the current pass.
     * @return True if this item should be used, false otherwise.
     */
    private fun isBestEnergyItemToUse(trainee: Trainee, itemName: String, nextInventory: Map<String, Int>, remainingItemsOfInterest: Set<String>): Boolean {
        val currentGain = energyGains[itemName] ?: return false
        val currentEnergy = trainee.energy

        val hasMoodItems = nextInventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
        val isKaleJuiceUsable =
            currentEnergy <= 20 || hasMoodItems || trainee.mood == Mood.AWFUL || kaleJuiceRestRecoveryEligible(nextInventory, currentEnergy)

        // Royal Kale Juice "Last Resort" logic: If energy is very low, we prioritize Kale Juice over everything.
        // It gives 100, so any other energy item used first would be wasted.
        if (currentEnergy <= 20 && isKaleJuiceUsable) {
            val hasKaleJuice =
                (itemName == "Royal Kale Juice") ||
                    (nextInventory["Royal Kale Juice"] ?: 0) > 0 ||
                    remainingItemsOfInterest.contains("Royal Kale Juice")
            if (hasKaleJuice) {
                return itemName == "Royal Kale Juice"
            }
        }

        // Collect all available energy items from this scan pass.
        // Subtract any units that fall inside the user-configured emergency reserve (lowest-tier first across `energyItemConservationOrder`).
        val availableEnergyItems = mutableListOf<Int>()
        remainingItemsOfInterest.forEach { name ->
            val gain = energyGains[name]
            if (gain != null) {
                // If this is Kale Juice, only include it if it's usable.
                if (name == "Royal Kale Juice" && !isKaleJuiceUsable) return@forEach

                val rawCount = nextInventory[name] ?: 0
                val available = (rawCount - reservedEnergyUnitsFor(name, nextInventory)).coerceAtLeast(0)
                repeat(available) { availableEnergyItems.add(gain) }
            }
        }

        // Safety net: if the current item was not counted via remainingItemsOfInterest (already-removed edge case),
        // make sure the greedy sees it as an available option.
        if (!remainingItemsOfInterest.contains(itemName)) {
            availableEnergyItems.add(currentGain)
        }

        // Sort gains descending for greedy selection.
        availableEnergyItems.sortDescending()

        // Greedy with soft overshoot: prefer combinations that approach 100% even if they exceed it by up to 10.
        // This prefers Vita 65 + Vita 40 (= 105) over Vita 65 + Vita 20 (= 85) so we don't leave ~15% on the table.
        val overshootCap = 110
        var simulatedEnergy = currentEnergy
        val pickedEnergyItems = mutableListOf<Int>()
        for (gain in availableEnergyItems) {
            if (simulatedEnergy + gain <= overshootCap) {
                simulatedEnergy += gain
                pickedEnergyItems.add(gain)
            }
        }

        // If currentGain was one of the picked items, use it.
        return pickedEnergyItems.contains(currentGain)
    }

    companion object {
        /** Master Cleat Hammers that may be spent before the Finale; the rest are held for days 73-75. */
        fun spareMasterCleatHammers(masterHammerCount: Int, finaleReserve: Int): Int =
            (masterHammerCount - finaleReserve).coerceAtLeast(0)

        /**
         * Whether a Master Cleat Hammer may be used on this race.
         * Pre-finale: only surplus above [finaleReserve] on G1/G2.
         * Days 73-74: only when at least 2 copies remain (spend one, keep one for Day 75).
         * Day 75: any remaining copy on G1.
         */
        fun canUseMasterCleatHammer(
            day: Int,
            masterHammerCount: Int,
            grade: RaceGrade,
            finaleReserve: Int,
        ): Boolean {
            if (masterHammerCount <= 0) {
                return false
            }
            val eligibleGrade =
                if (day >= 73) {
                    grade == RaceGrade.G1
                } else {
                    grade == RaceGrade.G1 || grade == RaceGrade.G2
                }
            if (!eligibleGrade) {
                return false
            }
            return when {
                day < 73 -> spareMasterCleatHammers(masterHammerCount, finaleReserve) > 0
                day == 75 -> true
                else -> masterHammerCount >= 2
            }
        }

        /** Summer megaphone reserve allocation (best tier first). */
        fun reservedMegaphoneBreakdown(
            inventory: Map<String, Int>,
            reserveCount: Int,
            reserveOrder: List<String>,
        ): Map<String, Int> {
            if (reserveCount <= 0) return emptyMap()
            val breakdown = mutableMapOf<String, Int>()
            var remaining = reserveCount
            for (name in reserveOrder) {
                if (remaining <= 0) break
                val count = inventory[name] ?: 0
                val reservedHere = minOf(count, remaining)
                if (reservedHere > 0) {
                    breakdown[name] = reservedHere
                }
                remaining -= reservedHere
            }
            return breakdown
        }

        fun availableMegaphoneUnits(
            itemName: String,
            inventory: Map<String, Int>,
            reserveCount: Int,
            reserveOrder: List<String>,
        ): Int {
            val reserved = reservedMegaphoneBreakdown(inventory, reserveCount, reserveOrder)[itemName] ?: 0
            return ((inventory[itemName] ?: 0) - reserved).coerceAtLeast(0)
        }

        /**
         * Surplus-burn megaphone tier after the summer reserve quota is met (outside summer).
         * Never returns Empowering Megaphone.
         */
        fun pickSurplusBurnMegaphone(
            inventory: Map<String, Int>,
            reserveCount: Int,
            reserveOrder: List<String>,
            activeMegaphoneType: String?,
            megaphoneTurnCounter: Int,
            motivatingUpgradeAllowed: Boolean,
        ): String? {
            if (reserveCount <= 0) return null

            fun available(name: String): Int = availableMegaphoneUnits(name, inventory, reserveCount, reserveOrder)

            if (megaphoneTurnCounter > 0 && activeMegaphoneType == "Coaching Megaphone") {
                return if (motivatingUpgradeAllowed && available("Motivating Megaphone") > 0) {
                    "Motivating Megaphone"
                } else {
                    null
                }
            }
            if (megaphoneTurnCounter > 0) return null

            val reserve = reservedMegaphoneBreakdown(inventory, reserveCount, reserveOrder)
            val reserveIsOneEmpoweringOneMotivating =
                reserve["Empowering Megaphone"] == 1 && reserve["Motivating Megaphone"] == 1
            val reserveIsTwoEmpowering = reserve["Empowering Megaphone"] == 2

            return when {
                reserveIsOneEmpoweringOneMotivating -> {
                    when {
                        available("Motivating Megaphone") > 0 -> "Motivating Megaphone"
                        available("Coaching Megaphone") > 0 -> "Coaching Megaphone"
                        else -> null
                    }
                }
                reserveIsTwoEmpowering -> {
                    when {
                        available("Motivating Megaphone") >= 2 -> "Motivating Megaphone"
                        available("Coaching Megaphone") > 0 -> "Coaching Megaphone"
                        else -> null
                    }
                }
                else -> {
                    when {
                        available("Coaching Megaphone") > 0 -> "Coaching Megaphone"
                        available("Motivating Megaphone") > 0 -> "Motivating Megaphone"
                        else -> null
                    }
                }
            }
        }

        /** Non-summer: more than one Kale Juice in stock. */
        fun hasSurplusKaleJuiceForRest(isSummer: Boolean, kaleJuiceCount: Int): Boolean =
            !isSummer && kaleJuiceCount > 1

        /** Energy low enough that the bot would rest/recover instead of training. */
        fun energyLowEnoughForRestRecovery(energy: Int, restRecoveryEnergyThreshold: Int = 50): Boolean =
            energy <= restRecoveryEnergyThreshold

        /** Whether surplus Kale Juice may cover rest-level energy recovery outside summer. */
        fun kaleJuiceRestRecoveryEligible(
            isSummer: Boolean,
            kaleJuiceCount: Int,
            energy: Int,
            restRecoveryEnergyThreshold: Int = 50,
        ): Boolean =
            hasSurplusKaleJuiceForRest(isSummer, kaleJuiceCount) &&
                energyLowEnoughForRestRecovery(energy, restRecoveryEnergyThreshold)

        /** Whether energy-restoring items should be considered before training analysis. */
        fun shouldTryEnergyRecoveryItems(
            energy: Int,
            energyThreshold: Int,
            inventory: Map<String, Int>,
            energyItemNames: Collection<String> = listOf("Vita 20", "Vita 40", "Vita 65"),
        ): Boolean {
            if (energy > energyThreshold) return false
            val vitaAvailable = energyItemNames.any { (inventory[it] ?: 0) > 0 }
            val kaleAvailable = (inventory["Royal Kale Juice"] ?: 0) > 0
            return vitaAvailable || kaleAvailable
        }
    }
}
