package dev.nanoIsland

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager

object IslandWindowManager {

    private var islandView: IslandView? = null
    private var windowManager: WindowManager? = null

    val isAttached: Boolean
        get() = islandView != null && (islandView?.isAttachedToWindow == true)

    fun attach(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) {
            return false
        }
        if (islandView != null) {
            return true
        }

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        windowManager = wm

        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            10f,
            appContext.resources.displayMetrics
        ).toInt()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
        }

        val view = IslandView(appContext)
        wm.addView(view, params)
        islandView = view
        return true
    }

    fun detach() {
        val view = islandView ?: return
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
