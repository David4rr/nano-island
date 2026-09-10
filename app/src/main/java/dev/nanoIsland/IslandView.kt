package dev.nanoIsland

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
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
    private val iconRect = RectF()
    private val cardPath = Path()
    private val iconPath = Path()

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        typeface = Typeface.DEFAULT
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    var activeNotification: NotificationPayload? = null
        private set
    var currentShape: IslandShape = IslandShape.PUNCH_HOLE

    var targetShape: IslandShape = IslandShape.PUNCH_HOLE

    var currentWidthPx: Float = dpToPx(IslandShape.PUNCH_HOLE.widthDp)

    var currentHeightPx: Float = dpToPx(IslandShape.PUNCH_HOLE.heightDp)

    var currentCornerRadiusPx: Float = dpToPx(IslandShape.PUNCH_HOLE.cornerRadiusDp)

    fun setNotification(payload: NotificationPayload?) {
        activeNotification = payload
        invalidate()
    }
    val contentAlpha: Float
        get() {
            val cardW = dpToPx(IslandShape.CARD.widthDp)
            if (targetShape != IslandShape.CARD) {
                // On collapse to PUNCH_HOLE or PILL: fade out rapidly in first 20% of collapse
                return ((currentWidthPx / cardW - 0.80f) / 0.20f).coerceIn(0f, 1f)
            }
            // On expand to CARD: fade in gracefully in the final 35% of expansion
            if (cardW <= 0f) return 0f
            return ((currentWidthPx / cardW - 0.65f) / 0.35f).coerceIn(0f, 1f)
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

        val notification = activeNotification
        val alphaProgress = contentAlpha
        if (notification != null && alphaProgress > 0f) {
            val alpha = (alphaProgress * 255).toInt().coerceIn(0, 255)
            titlePaint.alpha = alpha
            titlePaint.textSize = dpToPx(14f)
            textPaint.alpha = alpha
            textPaint.textSize = dpToPx(13f)
            iconPaint.alpha = alpha

            val saveCount = canvas.save()
            cardPath.reset()
            cardPath.addRoundRect(rect, currentCornerRadiusPx, currentCornerRadiusPx, Path.Direction.CW)
            canvas.clipPath(cardPath)

            val paddingHorizontal = dpToPx(20f)
            val iconSize = dpToPx(44f)
            val iconRadius = dpToPx(10f)
            val iconSpacing = dpToPx(14f)

            val availableContentWidth = currentWidthPx - (paddingHorizontal * 2f)
            val textAvailableWidth = (availableContentWidth - iconSize - iconSpacing).coerceAtLeast(0f).toInt()

            val title = notification.title ?: ""
            val titleEllipsized = if (title.isNotEmpty() && textAvailableWidth > 0) {
                TextUtils.ellipsize(title, titlePaint, textAvailableWidth.toFloat(), TextUtils.TruncateAt.END).toString()
            } else title

            val bodyText = notification.text ?: ""
            val bodyLayout = if (bodyText.isNotEmpty() && textAvailableWidth > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, textPaint, textAvailableWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.15f)
                        .setIncludePad(false)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(
                        bodyText,
                        textPaint,
                        textAvailableWidth,
                        Layout.Alignment.ALIGN_NORMAL,
                        1.15f,
                        0f,
                        false
                    )
                }
            } else null

            val titleFontMetrics = titlePaint.fontMetrics
            val titleHeight = titleFontMetrics.descent - titleFontMetrics.ascent
            val spacingBetweenTitleAndBody = if (bodyLayout != null) dpToPx(4f) else 0f
            val bodyHeight = bodyLayout?.height?.toFloat() ?: 0f
            val totalTextHeight = titleHeight + spacingBetweenTitleAndBody + bodyHeight

            val totalContentHeight = maxOf(iconSize, totalTextHeight)
            val contentTop = top + (currentHeightPx - totalContentHeight) / 2f

            val iconLeft = left + paddingHorizontal
            val iconTop = contentTop + (totalContentHeight - iconSize) / 2f
            val textStartX = iconLeft + iconSize + iconSpacing

            val icon = notification.icon
            if (icon != null) {
                val iconSave = canvas.save()
                iconRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                iconPath.reset()
                iconPath.addRoundRect(iconRect, iconRadius, iconRadius, Path.Direction.CW)
                canvas.clipPath(iconPath)
                canvas.drawBitmap(icon, null, iconRect, iconPaint)
                canvas.restoreToCount(iconSave)
            }

            val titleY = contentTop + (totalContentHeight - totalTextHeight) / 2f - titleFontMetrics.ascent
            if (titleEllipsized.isNotEmpty()) {
                canvas.drawText(titleEllipsized, textStartX, titleY, titlePaint)
            }

            if (bodyLayout != null) {
                val bodyTop = titleY + titleFontMetrics.descent + spacingBetweenTitleAndBody
                val bodySave = canvas.save()
                canvas.translate(textStartX, bodyTop)
                bodyLayout.draw(canvas)
                canvas.restoreToCount(bodySave)
            }

            canvas.restoreToCount(saveCount)
        }
    }

    internal fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }

    /**
     * Test whether touch coordinates (relative to IslandView) hit the drawn island bounds,
     * including comfortable touch slop padding.
     */
    fun isTouchInsideIsland(touchX: Float, touchY: Float): Boolean {
        val viewWidth = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        val left = (viewWidth - currentWidthPx) / 2f
        val top = 0f
        val slopH = dpToPx(16f)
        val slopV = dpToPx(16f)
        val hitRect = RectF(
            left - slopH,
            top,
            left + currentWidthPx + slopH,
            top + currentHeightPx + slopV
        )
        return hitRect.contains(touchX, touchY)
    }
}
