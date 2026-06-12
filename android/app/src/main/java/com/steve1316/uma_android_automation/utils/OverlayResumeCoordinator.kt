package com.steve1316.uma_android_automation.utils

import android.content.Context
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.bot.campaigns.Trackblazer
import com.steve1316.uma_android_automation.components.ButtonAgenda
import com.steve1316.uma_android_automation.components.ButtonTrainingItems
import com.steve1316.uma_android_automation.components.LabelSkillListScreenSkillPoints

/**
 * Tracks overlay pause/resume sessions so stop/start from the floating overlay button does not
 * always re-run first-session skill, agenda, and shop checks unless Advanced Settings allow it.
 */
object OverlayResumeCoordinator {
    private val TAG = "[${MainActivity.loggerTag}]OverlayResumeCoordinator"

    private const val PREFS_NAME = "overlay_resume_session"

    private const val KEY_SESSION_ACTIVE = "session_active"
    private const val KEY_PAUSED_AT_TURN = "paused_at_turn"
    private const val KEY_SKILL_CHECK_HANDLED = "skill_check_handled"
    private const val KEY_AGENDA_LOADED = "agenda_loaded"
    private const val KEY_SHOP_CHECK_PERFORMED = "shop_check_performed"
    private const val KEY_PAUSED_IN_SKILL = "paused_in_skill"
    private const val KEY_PAUSED_IN_SHOP = "paused_in_shop"
    private const val KEY_PAUSED_IN_AGENDA = "paused_in_agenda"

    /** Set when the Home screen Start/Stop button requests a stop (not overlay pause). */
    @Volatile
    var homeStopRequested: Boolean = false

    /** Set when the Home screen Start button begins a run (not overlay resume). */
    @Volatile
    var homeStartRequested: Boolean = false

    @Volatile
    private var sessionInitialized: Boolean = false

    @Volatile
    private var sessionStartSkipsApplied: Boolean = false

    @Volatile
    private var isOverlayResumeSession: Boolean = false

    private var pausedAtTurn: Int = -1
    private var deferredAgendaOnSameTurn: Boolean = false
    private var turnChangeShopRecheckPending: Boolean = false

    private var pausedInSkillScreen: Boolean = false
    private var pausedInShopScreen: Boolean = false
    private var pausedInAgendaFlow: Boolean = false

    private fun overlayResumeRecheckSkills(): Boolean =
        SettingsHelper.getBooleanSetting("advanced", "overlayResumeRecheckSkills", false)

    private fun overlayResumeReloadAgenda(): Boolean =
        SettingsHelper.getBooleanSetting("advanced", "overlayResumeReloadAgenda", false)

    private fun overlayResumeRecheckShop(): Boolean =
        SettingsHelper.getBooleanSetting("advanced", "overlayResumeRecheckShop", false)

    private fun overlayRecheckShopOnTurnChange(): Boolean =
        SettingsHelper.getBooleanSetting("advanced", "overlayRecheckShopOnTurnChange", false)

    private fun overlayRecheckSkillsWhenOverThreshold(): Boolean =
        SettingsHelper.getBooleanSetting("advanced", "overlayRecheckSkillsWhenOverThreshold", false)

    fun onHomeStartRequested() {
        homeStartRequested = true
        clearPersistedSession(null)
    }

    fun onHomeStopRequested() {
        homeStopRequested = true
        clearPersistedSession(null)
    }

    /** Called once per bot session on the first [Campaign.process] tick. */
    fun initializeSessionIfNeeded(campaign: Campaign) {
        if (sessionInitialized) {
            return
        }
        sessionInitialized = true

        val overlayResume = !homeStartRequested && hasPersistedSession(campaign.game.myContext)
        homeStartRequested = false

        if (!overlayResume) {
            isOverlayResumeSession = false
            MessageLog.i(TAG, "[OVERLAY RESUME] Fresh bot session (Home start or no saved overlay pause).")
            return
        }

        isOverlayResumeSession = true
        loadPersistedSession(campaign.game.myContext)
        MessageLog.i(
            TAG,
            "[OVERLAY RESUME] Resuming after overlay pause (turn $pausedAtTurn). " +
                "Immediate recheck toggles — skills: ${overlayResumeRecheckSkills()}, " +
                "agenda: ${overlayResumeReloadAgenda()}, shop: ${overlayResumeRecheckShop()}.",
        )
    }

    /**
     * Applies skip/recheck rules once the bot has reached the main screen and the current turn is known.
     * Called from [Campaign.handleMainScreen] before agenda load and global checks.
     */
    fun applySessionStartSkipsOnce(campaign: Campaign) {
        if (!isOverlayResumeSession || sessionStartSkipsApplied) {
            return
        }
        sessionStartSkipsApplied = true
        applySessionStartSkips(campaign)
    }

    /** Persists career progress flags when the overlay pause ends the bot thread. */
    fun onBotSessionEnded(game: Game, taskResult: TaskResult?) {
        sessionInitialized = false
        sessionStartSkipsApplied = false
        isOverlayResumeSession = false
        deferredAgendaOnSameTurn = false
        turnChangeShopRecheckPending = false

        if (homeStopRequested) {
            clearPersistedSession(game.myContext)
            homeStopRequested = false
            return
        }

        if (taskResult?.code == TaskResultCode.TASK_RESULT_COMPLETE) {
            clearPersistedSession(game.myContext)
            return
        }

        val campaign = game.task as? Campaign ?: return
        persistSession(game, campaign)
    }

    /** Returns true when agenda loading should be skipped on the first main-screen entry after overlay resume. */
    fun shouldSkipImmediateAgendaLoad(campaign: Campaign): Boolean {
        if (!isOverlayResumeSession || overlayResumeReloadAgenda()) {
            return false
        }
        if (deferredAgendaOnSameTurn && campaign.date.day == pausedAtTurn) {
            return false
        }
        return true
    }

    /** Called when the in-game turn changes during an overlay-resume session. */
    fun onTurnAdvanced(campaign: Campaign, newTurn: Int) {
        if (!isOverlayResumeSession) {
            return
        }

        if (turnChangeShopRecheckPending && newTurn != pausedAtTurn) {
            (campaign as? Trackblazer)?.markInitialShopCheckPendingForOverlayTurnChange()
            turnChangeShopRecheckPending = false
            MessageLog.i(TAG, "[OVERLAY RESUME] Turn advanced ($pausedAtTurn → $newTurn). Shop check will run on next main-screen entry.")
        }
    }

    /**
     * Allows a deferred skill-point check when the threshold toggle is enabled for overlay resume.
     * @return True when the skill check should proceed even though immediate recheck is disabled.
     */
    fun shouldForceSkillPointCheck(campaign: Campaign): Boolean {
        if (!isOverlayResumeSession || overlayResumeRecheckSkills()) {
            return false
        }
        return overlayRecheckSkillsWhenOverThreshold() && campaign.trainee.skillPoints >= campaign.skillPointsRequiredForOverlayResume()
    }

    /**
     * Resumes rare mid-flow states (skill list, shop, agenda UI) when the overlay was stopped inside them.
     *
     * @return True when the current screen was handled and the main loop should continue.
     */
    fun handleMidFlowResume(campaign: Campaign): Boolean {
        if (!isOverlayResumeSession) {
            return false
        }
        if (!pausedInSkillScreen && !pausedInShopScreen && !pausedInAgendaFlow) {
            return false
        }

        MessageLog.i(TAG, "[OVERLAY RESUME] Attempting to resume mid-flow screen handling...")

        if (pausedInSkillScreen && LabelSkillListScreenSkillPoints.check(campaign.game.imageUtils)) {
            pausedInSkillScreen = false
            val planName = if (campaign.enableSkillPointCheckForOverlayResume()) "skillPointCheck" else null
            if (campaign.handleSkillListScreen(planName)) {
                campaign.markSkillPointCheckHandledForOverlayResume()
                MessageLog.i(TAG, "[OVERLAY RESUME] Resumed skill list handling.")
                return true
            }
        }

        if (pausedInShopScreen && campaign is Trackblazer && ButtonTrainingItems.check(campaign.game.imageUtils)) {
            pausedInShopScreen = false
            campaign.buyItems()
            campaign.markInitialShopCheckPerformedForOverlayResume()
            MessageLog.i(TAG, "[OVERLAY RESUME] Resumed in-progress shop purchase flow.")
            return true
        }

        if (pausedInAgendaFlow && (campaign.checkRacingScreen() || ButtonAgenda.check(campaign.game.imageUtils) != null)) {
            pausedInAgendaFlow = false
            deferredAgendaOnSameTurn = false
            campaign.loadUserRaceAgendaForOverlayResume()
            MessageLog.i(TAG, "[OVERLAY RESUME] Resumed in-progress race agenda flow.")
            return true
        }

        pausedInSkillScreen = false
        pausedInShopScreen = false
        pausedInAgendaFlow = false
        return false
    }

    private fun applySessionStartSkips(campaign: Campaign) {
        if (!overlayResumeRecheckSkills()) {
            if (!overlayRecheckSkillsWhenOverThreshold() || campaign.trainee.skillPoints < campaign.skillPointsRequiredForOverlayResume()) {
                campaign.markSkillPointCheckHandledForOverlayResume()
            }
        }

        if (!overlayResumeReloadAgenda()) {
            val agendaWasLoaded = readPersistedBoolean(campaign.game.myContext, KEY_AGENDA_LOADED, false)
            val currentTurn = campaign.date.day
            val sameTurnAsPause = currentTurn > 0 && currentTurn == pausedAtTurn
            if (!agendaWasLoaded && sameTurnAsPause && campaign.isUserAgendaEnabledForOverlayResume()) {
                deferredAgendaOnSameTurn = true
                MessageLog.i(TAG, "[OVERLAY RESUME] Agenda was not loaded before pause; will retry on this turn.")
            } else {
                campaign.markAgendaLoadedForOverlayResume()
            }
        }

        if (!overlayResumeRecheckShop()) {
            (campaign as? Trackblazer)?.markInitialShopCheckPerformedForOverlayResume()
            if (overlayRecheckShopOnTurnChange()) {
                turnChangeShopRecheckPending = true
            }
        }
    }

    private fun persistSession(game: Game, campaign: Campaign) {
        val context = game.myContext
        val inSkill = LabelSkillListScreenSkillPoints.check(game.imageUtils)
        val inShop = campaign is Trackblazer && ButtonTrainingItems.check(game.imageUtils)
        val inAgenda = campaign.checkRacingScreen() || ButtonAgenda.check(game.imageUtils) != null

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putInt(KEY_PAUSED_AT_TURN, campaign.date.day)
            .putBoolean(KEY_SKILL_CHECK_HANDLED, campaign.hasHandledSkillPointCheckForOverlayResume())
            .putBoolean(KEY_AGENDA_LOADED, campaign.isAgendaLoadedForOverlayResume())
            .putBoolean(
                KEY_SHOP_CHECK_PERFORMED,
                (campaign as? Trackblazer)?.hasInitialShopCheckPerformedForOverlayResume() ?: true,
            )
            .putBoolean(KEY_PAUSED_IN_SKILL, inSkill)
            .putBoolean(KEY_PAUSED_IN_SHOP, inShop)
            .putBoolean(KEY_PAUSED_IN_AGENDA, inAgenda && !campaign.isAgendaLoadedForOverlayResume())
            .apply()

        MessageLog.i(
            TAG,
            "[OVERLAY RESUME] Saved overlay pause snapshot (turn ${campaign.date.day}, " +
                "skill=$inSkill, shop=$inShop, agenda=$inAgenda).",
        )
    }

    private fun loadPersistedSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        pausedAtTurn = prefs.getInt(KEY_PAUSED_AT_TURN, -1)
        pausedInSkillScreen = prefs.getBoolean(KEY_PAUSED_IN_SKILL, false)
        pausedInShopScreen = prefs.getBoolean(KEY_PAUSED_IN_SHOP, false)
        pausedInAgendaFlow = prefs.getBoolean(KEY_PAUSED_IN_AGENDA, false)
    }

    private fun hasPersistedSession(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SESSION_ACTIVE, false)

    private fun readPersistedBoolean(context: Context, key: String, default: Boolean): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, default)

    private fun clearPersistedSession(context: Context?) {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
        pausedAtTurn = -1
        pausedInSkillScreen = false
        pausedInShopScreen = false
        pausedInAgendaFlow = false
        deferredAgendaOnSameTurn = false
        turnChangeShopRecheckPending = false
    }
}
