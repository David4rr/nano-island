package dev.nanoIsland

import android.util.Log
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class SpringAnimationController(private val islandView: IslandView) {

    private val progressHolder = FloatValueHolder(0f)
    private val morphAnim = SpringAnimation(progressHolder)

    private var startWidthPx = 0f
    private var startHeightPx = 0f
    private var startRadiusPx = 0f
    private var targetWidthPx = 0f
    private var targetHeightPx = 0f
    private var targetRadiusPx = 0f

    private var isAnimating = false
    private var isExpanding = false
    private var currentEndCallback: (() -> Unit)? = null

    init {
        morphAnim.addUpdateListener { _, p, _ ->
            val clampedP = p.coerceIn(0f, 1f)
            // Expressive liquid mercury dynamics:
            // Width stretches out eagerly (squish & stretch), height blossoms open gracefully
            val pw = if (isExpanding) {
                1f - Math.pow((1f - clampedP).toDouble(), 1.45).toFloat()
            } else {
                1f - Math.pow((1f - clampedP).toDouble(), 1.15).toFloat()
            }
            val ph = if (isExpanding) {
                Math.pow(clampedP.toDouble(), 1.25).toFloat()
            } else {
                Math.pow(clampedP.toDouble(), 1.10).toFloat()
            }

            val currentW = startWidthPx + (targetWidthPx - startWidthPx) * pw
            val currentH = startHeightPx + (targetHeightPx - startHeightPx) * ph

            // Dynamic curvature: maintain unbroken capsule ends (r = h/2) during small heights,
            // transitioning seamlessly to card squircle radius as height expands
            val maxCapsuleRadius = currentH / 2f
            val rawRadius = startRadiusPx + (targetRadiusPx - startRadiusPx) * clampedP
            val currentR = minOf(maxCapsuleRadius, rawRadius)

            islandView.currentWidthPx = currentW
            islandView.currentHeightPx = currentH
            islandView.currentCornerRadiusPx = currentR
            islandView.invalidate()
        }

        morphAnim.addEndListener { _, _, _, _ ->
            isAnimating = false
            islandView.currentWidthPx = targetWidthPx
            islandView.currentHeightPx = targetHeightPx
            islandView.currentCornerRadiusPx = targetRadiusPx
            islandView.currentShape = islandView.targetShape
            islandView.invalidate()

            val callback = currentEndCallback
            currentEndCallback = null
            callback?.invoke()
        }
    }

    fun animateTo(
        targetShape: IslandShape,
        onStart: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ) {
        islandView.targetShape = targetShape
        currentEndCallback = onEnd

        startWidthPx = islandView.currentWidthPx
        startHeightPx = islandView.currentHeightPx
        startRadiusPx = islandView.currentCornerRadiusPx

        targetWidthPx = islandView.dpToPx(targetShape.widthDp)
        targetHeightPx = islandView.dpToPx(targetShape.heightDp)
        targetRadiusPx = islandView.dpToPx(targetShape.cornerRadiusDp)

        isExpanding = targetWidthPx > startWidthPx || targetHeightPx > startHeightPx

        // Expressive liquid physics:
        // Expand: stiffness 220f, damping 0.92f -> luxurious ~360ms fluid bloom with silky cushion settle
        // Collapse: stiffness 850f, damping 1.0f -> snappy, decisive ~150ms retract without bounce
        val stiffness = if (isExpanding) 220f else 850f
        val damping = if (isExpanding) 0.92f else SpringForce.DAMPING_RATIO_NO_BOUNCY

        morphAnim.cancel()
        progressHolder.value = 0f

        morphAnim.spring = SpringForce(1f).apply {
            this.stiffness = stiffness
            this.dampingRatio = damping
        }

        onStart?.invoke()
        isAnimating = true
        morphAnim.start()
    }

    fun cancel() {
        morphAnim.cancel()
        isAnimating = false
    }
}
