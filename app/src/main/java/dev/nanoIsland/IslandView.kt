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
        private set

    var currentWidthPx: Float = dpToPx(IslandShape.PUNCH_HOLE.widthDp)
        private set

    var currentHeightPx: Float = dpToPx(IslandShape.PUNCH_HOLE.heightDp)
        private set

    fun morphTo(shape: IslandShape) {
        currentShape = shape
        currentWidthPx = dpToPx(shape.widthDp)
        currentHeightPx = dpToPx(shape.heightDp)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(currentWidthPx, currentHeightPx) / 2f
        val left = (width - currentWidthPx) / 2f
        val top = (height - currentHeightPx) / 2f
        rect.set(left, top, left + currentWidthPx, top + currentHeightPx)
        canvas.drawRoundRect(rect, radius, radius, islandPaint)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
