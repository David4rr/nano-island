package dev.nanoIsland

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Phase 6 Touch Gesture System:
 * - Pull down >= 72dp: morphs into rounded square with open->closed lock morph -> locks screen.
 * - Swipe up <= -36dp: melting dissipation into top bezel -> captures screenshot -> ghost pop return.
 * - Release before threshold: synchronized spring snap-back.
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

        fun computePullDownProgress(diffY: Float, thresholdPx: Float): Float {
            if (thresholdPx <= 0f) return 0f
            return (diffY / thresholdPx).coerceIn(0f, 1.2f)
        }

        fun computeSwipeUpMeltProgress(diffY: Float, upThresholdPx: Float): Float {
            if (upThresholdPx <= 0f) return 0f
            return (-diffY / upThresholdPx).coerceIn(0f, 1.2f)
        }

        fun computeMeltAlpha(progress: Float): Float {
            val p = progress.coerceIn(0f, 1f)
            return if (p < 0.35f) 1f else (1f - (p - 0.35f) / 0.65f).coerceIn(0f, 1f)
        }
    }

    private var startY = 0f
    var isDragging: Boolean = false
        private set

    private var hasSnappedLock: Boolean = false

    var translationYSpring: SpringAnimation? = null
        private set
    var scaleXSpring: SpringAnimation? = null
        private set

    private var snapAnimator: ValueAnimator? = null
    private var reconstituteAnimator: ValueAnimator? = null
    private var reconstituteRunnable: Runnable? = null

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
                cancelAnimations()
                startY = event.rawY
                isDragging = true
                hasSnappedLock = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val diffY = event.rawY - startY

                if (diffY > 0f) {
                    // Pulling down: interactive morph to rounded square + open->closed lock
                    val pullProgress = computePullDownProgress(diffY, threshold)
                    islandView.pullDownProgress = pullProgress
                    islandView.swipeUpMeltProgress = 0f
                    islandView.translationY = computeTranslationY(diffY)
                    islandView.scaleX = 1f

                    // Tactile snap tick when padlock clicks shut at 0.85
                    if (pullProgress >= 0.85f && !hasSnappedLock) {
                        hasSnappedLock = true
                        triggerHapticTick(islandView)
                    } else if (pullProgress < 0.85f) {
                        hasSnappedLock = false
                    }
                } else if (diffY < 0f) {
                    // Swiping up: melting dissipation into top bezel
                    val meltProgress = computeSwipeUpMeltProgress(diffY, upThreshold)
                    islandView.pullDownProgress = 0f
                    islandView.swipeUpMeltProgress = meltProgress
                    islandView.translationY = computeTranslationY(diffY)
                    islandView.scaleX = 1f
                } else {
                    islandView.pullDownProgress = 0f
                    islandView.swipeUpMeltProgress = 0f
                    islandView.translationY = 0f
                    islandView.scaleX = 1f
                }
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
                        triggerHapticConfirm(islandView)
                        islandView.pullDownProgress = 1f
                        lockFn()
                    }

                    diffY <= -upThreshold -> {
                        // Branch 2: Swipe-up threshold exceeded -> Melting disappearance + Screenshot
                        Log.d(TAG, "Swipe up threshold reached ($diffY <= -$upThreshold), melting away and capturing screenshot")
                        triggerHapticConfirm(islandView)
                        islandView.swipeUpMeltProgress = 1f
                        islandView.islandAlpha = 0f
                        screenshotFn()
                        reconstituteIslandAfterMelt()
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
        cancelAnimations()

        val startPull = islandView.pullDownProgress
        val startMelt = islandView.swipeUpMeltProgress
        val startTransY = islandView.translationY
        val startAlpha = islandView.islandAlpha

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val inv = (1f - fraction).coerceAtLeast(0f)
                islandView.pullDownProgress = startPull * inv
                islandView.swipeUpMeltProgress = startMelt * inv
                islandView.translationY = startTransY * inv
                islandView.islandAlpha = (startAlpha + (1f - startAlpha) * fraction).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    resetTransform()
                }
            })
        }
        snapAnimator = anim
        anim.start()
    }

    fun reconstituteIslandAfterMelt() {
        cancelAnimations()

        val upThreshold = swipeUpThresholdPx
        val runnable = Runnable {
            val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 340L
                interpolator = OvershootInterpolator(1.2f)
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    val v = va.animatedValue as Float
                    islandView.swipeUpMeltProgress = v
                    islandView.translationY = computeTranslationY(-upThreshold) * v
                    islandView.islandAlpha = f.coerceIn(0f, 1f)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        resetTransform()
                    }
                })
            }
            reconstituteAnimator = anim
            anim.start()
        }
        reconstituteRunnable = runnable
        islandView.postDelayed(runnable, 350L)
    }

    fun resetTransform() {
        cancelAnimations()
        islandView.translationY = 0f
        islandView.scaleX = 1f
        islandView.scaleY = 1f
        islandView.resetToShape()
    }

    fun cancelSprings() {
        translationYSpring?.cancel()
        scaleXSpring?.cancel()
        translationYSpring = null
        scaleXSpring = null
    }

    fun cancelAnimations() {
        cancelSprings()
        snapAnimator?.cancel()
        snapAnimator = null
        reconstituteAnimator?.cancel()
        reconstituteAnimator = null
        reconstituteRunnable?.let { islandView.removeCallbacks(it) }
        reconstituteRunnable = null
    }

    private fun triggerHapticTick(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = view.context.getSystemService(Vibrator::class.java)
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    return
                }
            }
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Exception) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun triggerHapticConfirm(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = view.context.getSystemService(Vibrator::class.java)
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    return
                }
            }
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } catch (_: Exception) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
}
