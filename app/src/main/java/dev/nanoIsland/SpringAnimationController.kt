package dev.nanoIsland

import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class SpringAnimationController(private val islandView: IslandView) {

    private val widthHolder = FloatValueHolder(islandView.currentWidthPx)
    private val heightHolder = FloatValueHolder(islandView.currentHeightPx)
    private val radiusHolder = FloatValueHolder(islandView.currentCornerRadiusPx)

    private val widthAnim = SpringAnimation(widthHolder)
    private val heightAnim = SpringAnimation(heightHolder)
    private val radiusAnim = SpringAnimation(radiusHolder)

    private var isAnimating = false
    private var currentEndCallback: (() -> Unit)? = null

    init {
        widthAnim.addUpdateListener { _, value, _ ->
            islandView.currentWidthPx = value
            islandView.invalidate()
        }
        heightAnim.addUpdateListener { _, value, _ ->
            islandView.currentHeightPx = value
            islandView.invalidate()
        }
        radiusAnim.addUpdateListener { _, value, _ ->
            islandView.currentCornerRadiusPx = value
            islandView.invalidate()
        }

        val onAnimationFinished = {
            if (!widthAnim.isRunning && !heightAnim.isRunning && !radiusAnim.isRunning) {
                isAnimating = false
                islandView.currentShape = islandView.targetShape
                val callback = currentEndCallback
                currentEndCallback = null
                callback?.invoke()
            }
        }

        widthAnim.addEndListener { _, _, _, _ -> onAnimationFinished() }
        heightAnim.addEndListener { _, _, _, _ -> onAnimationFinished() }
        radiusAnim.addEndListener { _, _, _, _ -> onAnimationFinished() }
    }

    fun animateTo(
        targetShape: IslandShape,
        onStart: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ) {
        islandView.targetShape = targetShape
        currentEndCallback = onEnd

        val targetWidthPx = islandView.dpToPx(targetShape.widthDp)
        val targetHeightPx = islandView.dpToPx(targetShape.heightDp)
        val targetRadiusPx = islandView.dpToPx(targetShape.cornerRadiusDp)

        val isExpanding = targetWidthPx > islandView.currentWidthPx || targetHeightPx > islandView.currentHeightPx

        val stiffness = if (isExpanding) 200f else 1500f
        val damping = if (isExpanding) 0.75f else 1.0f

        widthAnim.cancel()
        heightAnim.cancel()
        radiusAnim.cancel()

        widthHolder.value = islandView.currentWidthPx
        heightHolder.value = islandView.currentHeightPx
        radiusHolder.value = islandView.currentCornerRadiusPx

        widthAnim.spring = SpringForce(targetWidthPx).apply {
            this.stiffness = stiffness
            this.dampingRatio = damping
        }
        heightAnim.spring = SpringForce(targetHeightPx).apply {
            this.stiffness = stiffness
            this.dampingRatio = damping
        }
        radiusAnim.spring = SpringForce(targetRadiusPx).apply {
            this.stiffness = stiffness
            this.dampingRatio = damping
        }

        onStart?.invoke()
        isAnimating = true

        widthAnim.start()
        heightAnim.start()
        radiusAnim.start()
    }

    fun cancel() {
        widthAnim.cancel()
        heightAnim.cancel()
        radiusAnim.cancel()
        isAnimating = false
    }
}
