package dev.nanoIsland

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager

object IslandWindowManager {

    private var islandView: IslandView? = null
    private var windowManager: WindowManager? = null
    private var animationController: SpringAnimationController? = null
    val isAttached: Boolean
        get() = islandView != null

    val currentShape: IslandShape
        get() = islandView?.currentShape ?: IslandShape.PUNCH_HOLE

    private fun getTopOffsetPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            context.resources.displayMetrics
        ).toInt()
    }

    fun attach(context: Context): Boolean {
        val appContext = context.applicationContext
        val canDraw = Settings.canDrawOverlays(appContext)
        if (!canDraw) {
            return false
        }
        if (islandView != null) {
            return true
        }

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        windowManager = wm
        val initialShape = IslandShape.PUNCH_HOLE
        val widthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            initialShape.widthDp,
            appContext.resources.displayMetrics
        ).toInt()
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            initialShape.heightDp,
            appContext.resources.displayMetrics
        ).toInt()

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = getTopOffsetPx(appContext)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val view = IslandView(appContext)
        val controller = SpringAnimationController(view)
        try {
            wm.addView(view, params)
            islandView = view
            animationController = controller
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun animateTo(shape: IslandShape, onEnd: (() -> Unit)? = null) {
        val view = islandView ?: return
        val wm = windowManager ?: return
        val controller = animationController ?: return

        val startWidth = view.currentWidthPx.toInt()
        val targetWidth = view.dpToPx(shape.widthDp).toInt()
        val startHeight = view.currentHeightPx.toInt()
        val targetHeight = view.dpToPx(shape.heightDp).toInt()

        val maxW = maxOf(startWidth, targetWidth)
        val maxH = maxOf(startHeight, targetHeight)

        val params = view.layoutParams as? WindowManager.LayoutParams
        if (params != null && (params.width < maxW || params.height < maxH)) {
            params.width = maxW
            params.height = maxH
            try {
                wm.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }

        controller.animateTo(
            targetShape = shape,
            onEnd = {
                val endParams = view.layoutParams as? WindowManager.LayoutParams
                if (endParams != null) {
                    endParams.width = targetWidth
                    endParams.height = targetHeight
                    try {
                        wm.updateViewLayout(view, endParams)
                    } catch (_: Exception) {}
                }
                onEnd?.invoke()
            }
        )
    }

    fun morphTo(shape: IslandShape) {
        val view = islandView ?: return
        val wm = windowManager ?: return
        animationController?.cancel()

        view.morphTo(shape)

        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.width = view.currentWidthPx.toInt()
        params.height = view.currentHeightPx.toInt()
        params.y = getTopOffsetPx(view.context)
        try {
            wm.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    fun detach() {
        val view = islandView ?: return
        animationController?.cancel()
        animationController = null
        val wm = windowManager
        if (view.isAttachedToWindow && wm != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {}
        }
        islandView = null
        windowManager = null
    }
}
