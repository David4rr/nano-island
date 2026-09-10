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

    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val lockKeyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    var topOffsetPx: Float = dpToPx(18f)

    var activeNotification: NotificationPayload? = null
        private set
    var currentShape: IslandShape = IslandShape.PUNCH_HOLE

    var targetShape: IslandShape = IslandShape.PUNCH_HOLE

    var currentWidthPx: Float = dpToPx(IslandShape.PUNCH_HOLE.widthDp)

    var currentHeightPx: Float = dpToPx(IslandShape.PUNCH_HOLE.heightDp)

    var currentCornerRadiusPx: Float = dpToPx(IslandShape.PUNCH_HOLE.cornerRadiusDp)

    var pullDownProgress: Float = 0f
        set(value) {
            field = value
            updateInteractiveGeometry()
            invalidate()
        }

    var swipeUpMeltProgress: Float = 0f
        set(value) {
            field = value
            updateInteractiveGeometry()
            invalidate()
        }

    var islandAlpha: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

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

    fun resetToShape(shape: IslandShape = currentShape) {
        currentShape = shape
        targetShape = shape
        pullDownProgress = 0f
        swipeUpMeltProgress = 0f
        islandAlpha = 1f
        currentWidthPx = dpToPx(shape.widthDp)
        currentHeightPx = dpToPx(shape.heightDp)
        currentCornerRadiusPx = dpToPx(shape.cornerRadiusDp)
        invalidate()
    }

    fun morphTo(shape: IslandShape) {
        resetToShape(shape)
    }

    private fun updateInteractiveGeometry() {
        if (pullDownProgress > 0f) {
            val p = pullDownProgress.coerceIn(0f, 1f)
            val baseW = dpToPx(currentShape.widthDp)
            val baseH = dpToPx(currentShape.heightDp)
            val baseR = dpToPx(currentShape.cornerRadiusDp)
            val targetW = dpToPx(IslandShape.ROUNDED_SQUARE.widthDp)
            val targetH = dpToPx(IslandShape.ROUNDED_SQUARE.heightDp)
            val targetR = dpToPx(IslandShape.ROUNDED_SQUARE.cornerRadiusDp)

            currentWidthPx = baseW + (targetW - baseW) * p
            currentHeightPx = baseH + (targetH - baseH) * p
            currentCornerRadiusPx = baseR + (targetR - baseR) * p
            islandAlpha = 1f
        } else if (swipeUpMeltProgress > 0f) {
            val p = swipeUpMeltProgress.coerceIn(0f, 1f)
            val baseW = dpToPx(currentShape.widthDp)
            val baseH = dpToPx(currentShape.heightDp)

            // Liquid squeeze: spreads horizontally against top bezel
            currentWidthPx = baseW * (1f + 0.45f * p)
            // Liquid squeeze: squashes vertically into top edge
            val targetH = dpToPx(3f)
            currentHeightPx = (baseH * (1f - p) + targetH * p).coerceAtLeast(targetH)
            currentCornerRadiusPx = currentHeightPx / 2f
            // Melting dissolution: smoothly fades out
            islandAlpha = if (p < 0.35f) 1f else (1f - (p - 0.35f) / 0.65f).coerceIn(0f, 1f)
        }
    }

    private fun drawLockIcon(canvas: Canvas, cx: Float, cy: Float, progress: Float) {
        val alphaFactor = ((progress - 0.05f) / 0.25f).coerceIn(0f, 1f)
        if (alphaFactor <= 0f) return
        val alphaInt = (alphaFactor * 255).toInt().coerceIn(0, 255)

        lockPaint.alpha = alphaInt
        lockPaint.strokeWidth = dpToPx(2.2f)
        lockBodyPaint.alpha = alphaInt
        lockKeyholePaint.alpha = alphaInt

        // Lock shackle closes between 0.25 and 0.85 progress
        val lockCloseProgress = ((progress - 0.25f) / 0.60f).coerceIn(0f, 1f)
        val openFactor = 1f - lockCloseProgress

        val bodyWidth = dpToPx(16f)
        val bodyHeight = dpToPx(11.5f)
        val bodyRadius = dpToPx(3f)
        val bodyTop = cy - dpToPx(1f)
        val bodyBottom = bodyTop + bodyHeight
        val bodyRect = RectF(cx - bodyWidth / 2f, bodyTop, cx + bodyWidth / 2f, bodyBottom)

        val shackleRadius = dpToPx(5f)
        val shackleLeftX = cx - shackleRadius
        val shackleRightX = cx + shackleRadius
        val shackleArchTop = bodyTop - dpToPx(9.5f)

        // Shackle transformation: when open, elevated and rotated around left pivot
        val saveCount = canvas.save()
        val pivotX = shackleLeftX
        val pivotY = bodyTop
        val rotationAngle = -22f * openFactor
        val liftY = -dpToPx(4f) * openFactor

        canvas.translate(0f, liftY)
        canvas.rotate(rotationAngle, pivotX, pivotY)

        // Arch
        val archRect = RectF(shackleLeftX, shackleArchTop, shackleRightX, shackleArchTop + shackleRadius * 2)
        canvas.drawArc(archRect, 180f, 180f, false, lockPaint)

        // Left leg (extends downward to stay anchored into the lock body)
        val leftLegBottom = bodyTop + dpToPx(2f) - liftY
        canvas.drawLine(shackleLeftX, shackleArchTop + shackleRadius, shackleLeftX, leftLegBottom, lockPaint)

        // Right leg (open tip: when openFactor = 1, bottom is bodyTop - dpToPx(3.5f), showing distinct open gap)
        val rightLegBottom = bodyTop + dpToPx(1.5f) - (dpToPx(4.5f) * openFactor)
        canvas.drawLine(shackleRightX, shackleArchTop + shackleRadius, shackleRightX, rightLegBottom, lockPaint)

        canvas.restoreToCount(saveCount)

        // Draw lock body on top of shackle legs
        canvas.drawRoundRect(bodyRect, bodyRadius, bodyRadius, lockBodyPaint)

        // Draw keyhole
        val keyholeY = bodyTop + dpToPx(4.5f)
        canvas.drawCircle(cx, keyholeY, dpToPx(1.5f), lockKeyholePaint)
        canvas.drawRect(cx - dpToPx(0.75f), keyholeY, cx + dpToPx(0.75f), keyholeY + dpToPx(3.5f), lockKeyholePaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateInteractiveGeometry()

        val alphaInt = (islandAlpha * 255).toInt().coerceIn(0, 255)
        if (alphaInt <= 0) {
            return
        }
        islandPaint.alpha = alphaInt

        val left = (width - currentWidthPx) / 2f
        val top = topOffsetPx
        rect.set(left, top, left + currentWidthPx, top + currentHeightPx)
        canvas.drawRoundRect(rect, currentCornerRadiusPx, currentCornerRadiusPx, islandPaint)

        // Draw lock icon during pull-down morph or when target/current shape is ROUNDED_SQUARE
        val isSquareTarget = targetShape == IslandShape.ROUNDED_SQUARE || currentShape == IslandShape.ROUNDED_SQUARE
        val showLock = pullDownProgress > 0f || isSquareTarget
        if (showLock) {
            val lockProgress = if (pullDownProgress > 0f) pullDownProgress else 1f
            drawLockIcon(canvas, rect.centerX(), rect.centerY(), lockProgress)
        }

        val notification = activeNotification
        val alphaProgress = if (pullDownProgress > 0f || swipeUpMeltProgress > 0f) 0f else contentAlpha
        if (notification != null && alphaProgress > 0f && islandAlpha > 0.5f) {
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
        val top = topOffsetPx
        val slopH = dpToPx(16f)
        val slopV = dpToPx(16f)
        val hitRect = RectF(
            left - slopH,
            0f, // Cover full area above status bar to prevent clipping and enable smooth swipe-up
            left + currentWidthPx + slopH,
            top + currentHeightPx + slopV
        )
        return hitRect.contains(touchX, touchY)
    }
}
