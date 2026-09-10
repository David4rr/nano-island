package dev.nanoIsland

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager

object IslandWindowManager {

    private var islandView: IslandView? = null
    private var windowManager: WindowManager? = null
    private var animationController: SpringAnimationController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeNotification: NotificationPayload? = null
    private var autoDismissRunnable: Runnable? = null
    private val interceptedKeys = mutableSetOf<String>()
    var baseShape: IslandShape = IslandShape.PUNCH_HOLE
    var touchListener: IslandTouchListener? = null
        private set

    val isAttached: Boolean
        get() = islandView != null

    val currentShape: IslandShape
        get() = islandView?.currentShape ?: IslandShape.PUNCH_HOLE
    private fun getTopOffsetPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            18f,
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
        val windowHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            220f,
            appContext.resources.displayMetrics
        ).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            windowHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
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
        val listener = IslandTouchListener(
            islandView = view,
            lockFn = {
                NanoAccessibilityService.lockScreen()
            },
            screenshotFn = {
                NanoAccessibilityService.takeScreenshot(
                    onSuccess = { _, buffer ->
                        buffer?.close()
                    }
                )
            }
        )
        view.setOnTouchListener(listener)
        try {
            wm.addView(view, params)
            islandView = view
            animationController = controller
            touchListener = listener
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun animateTo(shape: IslandShape, onEnd: (() -> Unit)? = null) {
        val view = islandView ?: return
        val controller = animationController ?: return

        if (shape != IslandShape.CARD) {
            baseShape = shape
        }

        if (shape != IslandShape.PUNCH_HOLE) {
            setWindowTouchable(true)
        }
        controller.animateTo(
            targetShape = shape,
            onEnd = {
                if (shape == IslandShape.PUNCH_HOLE) {
                    setWindowTouchable(false)
                }
                onEnd?.invoke()
            }
        )
    }

    fun morphTo(shape: IslandShape) {
        val view = islandView ?: return
        if (shape != IslandShape.CARD) {
            baseShape = shape
        }
        animationController?.cancel()
        view.morphTo(shape)
        setWindowTouchable(shape != IslandShape.PUNCH_HOLE)
    }

    private fun setWindowTouchable(touchable: Boolean) {
        val view = islandView ?: return
        val wm = windowManager ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val isNotTouchable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        val shouldBeNotTouchable = !touchable
        if (isNotTouchable != shouldBeNotTouchable) {
            if (shouldBeNotTouchable) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try {
                wm.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }


    fun onNotificationReceived(payload: NotificationPayload) {
        mainHandler.post {
            val view = islandView ?: return@post
            autoDismissRunnable?.let {
                mainHandler.removeCallbacks(it)
                autoDismissRunnable = null
            }
            interceptedKeys.add(payload.key)
            activeNotification = payload
            view.setNotification(payload)
            val scheduleDismiss = {
                val dismiss = Runnable {
                    dismissActiveNotification()
                }
                autoDismissRunnable = dismiss
                mainHandler.postDelayed(dismiss, 3500L)
            }

            if (view.targetShape == IslandShape.CARD || view.currentShape == IslandShape.CARD) {
                scheduleDismiss()
            } else {
                animateTo(IslandShape.CARD) {
                    scheduleDismiss()
                }
            }
        }
    }

    fun dismissActiveNotification() {
        mainHandler.post {
            autoDismissRunnable?.let {
                mainHandler.removeCallbacks(it)
                autoDismissRunnable = null
            }
            if (activeNotification == null && interceptedKeys.isEmpty()) return@post
            val target = baseShape
            animateTo(target) {
                val keysToDismiss = interceptedKeys.toList()
                interceptedKeys.clear()
                for (key in keysToDismiss) {
                    NanoNotificationListener.dismissNotification(key)
                }
                activeNotification = null
                islandView?.setNotification(null)
            }
        }
    }

    fun detach() {
        val view = islandView ?: return
        autoDismissRunnable?.let {
            mainHandler.removeCallbacks(it)
            autoDismissRunnable = null
        }
        activeNotification = null
        animationController?.cancel()
        animationController = null
        view.setOnTouchListener(null)
        touchListener?.resetTransform()
        touchListener = null
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
