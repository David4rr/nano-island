package dev.nanoIsland

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Phase 6 Touch Gesture System:
 * - Pull down >= 72dp: elastic stretch -> lock screen via AccessibilityService.
 * - Swipe up <= -72dp: elastic compress -> take screenshot.
 * - Release before threshold: spring snap-back for translationY and scaleX.
 */
class IslandTouchListener(
    val islandView: IslandView,
    val lockFn: () -> Unit,
    val screenshotFn: () -> Unit
) : View.OnTouchListener {

    companion object {
        private const val TAG = "IslandTouchListener"
        const val PULL_THRESHOLD_DP = 72f
        const val SWIPE_UP_THRESHOLD_DP = 36f
        const val RESISTANCE_FACTOR = 0.4f
        const val MAX_SCALE_EXPANSION = 0.15f

        fun computeTranslationY(diffY: Float): Float {
            return diffY * RESISTANCE_FACTOR
        }

        fun computeScaleX(diffY: Float, thresholdPx: Float): Float {
            if (thresholdPx <= 0f) return 1f
            return if (diffY > 0f) {
                1f + ((diffY / thresholdPx).coerceIn(0f, 1f) * MAX_SCALE_EXPANSION)
            } else {
                1f
            }
        }
    }

    private var startY = 0f
    var isDragging: Boolean = false
        private set

    var translationYSpring: SpringAnimation? = null
        private set
    var scaleXSpring: SpringAnimation? = null
        private set

    val pullThresholdPx: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            PULL_THRESHOLD_DP,
            islandView.resources.displayMetrics
        )

    val swipeUpThresholdPx: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            SWIPE_UP_THRESHOLD_DP,
            islandView.resources.displayMetrics
        )

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        val threshold = pullThresholdPx
        val upThreshold = swipeUpThresholdPx

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!islandView.isTouchInsideIsland(event.x, event.y)) {
                    isDragging = false
                    return false
                }
                cancelSprings()
                startY = event.rawY
                isDragging = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val diffY = event.rawY - startY

                // Elastic resistance on translationY and scaleX
                islandView.translationY = computeTranslationY(diffY)
                islandView.scaleX = computeScaleX(diffY, threshold)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) return false
                isDragging = false
                val diffY = event.rawY - startY

                when {
                    diffY >= threshold -> {
                        // Branch 1: Pull-down threshold exceeded -> Lock Screen
                        Log.d(TAG, "Pull down threshold reached ($diffY >= $threshold), locking screen")
                        triggerHaptic(islandView)
                        resetTransform()
                        lockFn()
                    }

                    diffY <= -upThreshold -> {
                        // Branch 2: Swipe-up threshold exceeded -> Screenshot
                        Log.d(TAG, "Swipe up threshold reached ($diffY <= -$upThreshold), taking screenshot")
                        triggerHaptic(islandView)
                        resetTransform()
                        screenshotFn()
                    }

                    else -> {
                        // Branch 3: Snap back
                        Log.d(TAG, "Gesture released below threshold ($diffY), snapping back")
                        snapBack()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    snapBack()
                    return true
                }
                return false
            }
        }
        return false
    }

    fun snapBack() {
        cancelSprings()

        val animY = SpringAnimation(islandView, DynamicAnimation.TRANSLATION_Y).apply {
            spring = SpringForce(0f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
        }
        val animX = SpringAnimation(islandView, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(1f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
        }

        translationYSpring = animY
        scaleXSpring = animX

        animY.start()
        animX.start()
    }

    fun resetTransform() {
        cancelSprings()
        islandView.translationY = 0f
        islandView.scaleX = 1f
        islandView.scaleY = 1f
    }

    fun cancelSprings() {
        translationYSpring?.cancel()
        scaleXSpring?.cancel()
        translationYSpring = null
        scaleXSpring = null
    }

    private fun triggerHaptic(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val vibrator = view.context.getSystemService(Vibrator::class.java)
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Exception) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
