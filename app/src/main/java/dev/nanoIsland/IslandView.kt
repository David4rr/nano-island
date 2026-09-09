package dev.nanoIsland

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class IslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val islandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val rect = RectF()

    var currentShape: IslandShape = IslandShape.PUNCH_HOLE

    var targetShape: IslandShape = IslandShape.PUNCH_HOLE

    var currentWidthPx: Float = dpToPx(IslandShape.PUNCH_HOLE.widthDp)

    var currentHeightPx: Float = dpToPx(IslandShape.PUNCH_HOLE.heightDp)

    var currentCornerRadiusPx: Float = dpToPx(IslandShape.PUNCH_HOLE.cornerRadiusDp)

    val contentAlpha: Float
        get() {
            val targetW = dpToPx(targetShape.widthDp)
            if (targetW <= 0f) return 0f
            return ((currentWidthPx / targetW - 0.7f) / 0.3f).coerceIn(0f, 1f)
        }

    fun morphTo(shape: IslandShape) {
        currentShape = shape
        targetShape = shape
        currentWidthPx = dpToPx(shape.widthDp)
        currentHeightPx = dpToPx(shape.heightDp)
        currentCornerRadiusPx = dpToPx(shape.cornerRadiusDp)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = (width - currentWidthPx) / 2f
        val top = 0f
        rect.set(left, top, left + currentWidthPx, top + currentHeightPx)
        canvas.drawRoundRect(rect, currentCornerRadiusPx, currentCornerRadiusPx, islandPaint)
    }

    internal fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
