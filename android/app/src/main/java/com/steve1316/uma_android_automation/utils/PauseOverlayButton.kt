package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import com.steve1316.automation_library.R
import com.steve1316.automation_library.data.SharedData
import kotlin.math.abs

/**
 * Floating pause/resume overlay using the same icon assets as the start/stop overlay.
 *
 * Visual state is inverted from the main overlay:
 * - Bot running (not paused): red stop icon, stationary
 * - Bot paused: green play icon, spinning
 */
class PauseOverlayButton(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private val overlayLayoutParamsType: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private val overlayLayoutParams =
        WindowManager.LayoutParams().apply {
            type = overlayLayoutParamsType
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            windowAnimations = android.R.style.Animation_Toast
            gravity = Gravity.TOP or Gravity.START
        }

    private val buttonSizePx: Int = dpToPx(SharedData.overlayButtonSizeDP)
    private val touchSlop: Int = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private lateinit var overlayView: View
    private lateinit var overlayButton: ImageButton
    private lateinit var spinAnimation: Animation

    private var isPaused: Boolean = false
    private var onClickListener: (() -> Unit)? = null

    init {
        createOverlayButton()
        initializeAnimations()
        applyVisualState()
    }

    fun setOnClickListener(listener: () -> Unit) {
        onClickListener = listener
    }

    fun setPausedState(paused: Boolean) {
        if (isPaused == paused) {
            return
        }
        isPaused = paused
        applyVisualState()
    }

    fun cleanup() {
        overlayButton.clearAnimation()
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {
        }
    }

    private fun createOverlayButton() {
        overlayView = LayoutInflater.from(context).inflate(R.layout.bot_actions, null)
        overlayButton = overlayView.findViewById<ImageButton>(R.id.bot_actions_overlay_button)
        overlayButton.layoutParams.width = buttonSizePx
        overlayButton.layoutParams.height = buttonSizePx
        overlayButton.requestLayout()

        setInitialOverlayPosition()
        loadSavedPosition()

        windowManager.addView(overlayView, overlayLayoutParams)
        setupTouchListener()
    }

    private fun setInitialOverlayPosition() {
        val startPrefs = context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
        val savedPauseX = startPrefs.getInt(PAUSE_X_KEY, Int.MIN_VALUE)
        if (savedPauseX != Int.MIN_VALUE) {
            return
        }

        val mainPrefs = context.getSharedPreferences(MAIN_OVERLAY_PREFS, Context.MODE_PRIVATE)
        val mainX = mainPrefs.getInt("lastX", defaultOverlayX())
        val mainY = mainPrefs.getInt("lastY", defaultOverlayY())
        overlayLayoutParams.x = mainX
        overlayLayoutParams.y = mainY + buttonSizePx + dpToPx(8f)
    }

    private fun loadSavedPosition() {
        val prefs = context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(PAUSE_X_KEY)) {
            overlayLayoutParams.x = prefs.getInt(PAUSE_X_KEY, overlayLayoutParams.x)
            overlayLayoutParams.y = prefs.getInt(PAUSE_Y_KEY, overlayLayoutParams.y)
        }
    }

    private fun savePosition() {
        context.getSharedPreferences(OVERLAY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(PAUSE_X_KEY, overlayLayoutParams.x)
            .putInt(PAUSE_Y_KEY, overlayLayoutParams.y)
            .apply()
    }

    private fun defaultOverlayX(): Int {
        val displayWidth = if (SharedData.displayWidth > 0) SharedData.displayWidth else context.resources.displayMetrics.widthPixels
        return displayWidth - buttonSizePx - dpToPx(16f)
    }

    private fun defaultOverlayY(): Int {
        val displayHeight = if (SharedData.displayHeight > 0) SharedData.displayHeight else context.resources.displayMetrics.heightPixels
        return displayHeight / 4
    }

    private fun initializeAnimations() {
        spinAnimation = AnimationUtils.loadAnimation(context, R.anim.stop_button_animation)
    }

    private fun applyVisualState() {
        overlayButton.clearAnimation()
        if (isPaused) {
            overlayButton.setImageResource(R.drawable.play_circle_filled)
            overlayButton.startAnimation(spinAnimation)
        } else {
            overlayButton.setImageResource(R.drawable.stop_circle_filled)
        }
    }

    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = overlayLayoutParams.x
                    initialY = overlayLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        overlayLayoutParams.x = initialX + deltaX
                        overlayLayoutParams.y = initialY + deltaY
                        windowManager.updateViewLayout(overlayView, overlayLayoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        savePosition()
                    } else {
                        onClickListener?.invoke()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics,
        ).toInt()

    companion object {
        private const val MAIN_OVERLAY_PREFS = "OverlayPrefs"
        private const val OVERLAY_PREFS = "PauseOverlayPrefs"
        private const val PAUSE_X_KEY = "lastX"
        private const val PAUSE_Y_KEY = "lastY"
    }
}
