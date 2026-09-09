package dev.nanoIsland

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Passive accessibility service for executor actions:
 * - GLOBAL_ACTION_LOCK_SCREEN (API 28+)
 * - takeScreenshot (API 30+) / GLOBAL_ACTION_TAKE_SCREENSHOT (API 28-29)
 *
 * Configured as passive (eventTypes = 0, flags = 0, notificationTimeout = 0)
 * ensuring 0.0% idle CPU and zero event listening overhead.
 */
class NanoAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "NanoAccessibilityService connected (passive)")

        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = 0
            flags = 0
            notificationTimeout = 0
        }
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero event listening: strictly passive executor
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "NanoAccessibilityService unbound")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "NanoAccessibilityService destroyed")
        instance = null
    }

    companion object {
        private const val TAG = "NanoAccessibility"

        const val ERROR_SERVICE_NOT_CONNECTED = -1
        const val ERROR_GLOBAL_ACTION_FAILED = -2

        @Volatile
        private var instance: NanoAccessibilityService? = null

        val isConnected: Boolean
            get() = instance != null

        /**
         * Check if NanoAccessibilityService is enabled in Android Accessibility Settings.
         */
        fun isServiceEnabled(context: Context): Boolean {
            if (isConnected) return true
            val expectedComponent = ComponentName(context, NanoAccessibilityService::class.java).flattenToString()
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.equals(expectedComponent, ignoreCase = true)) {
                    return true
                }
                val unflattened = ComponentName.unflattenFromString(componentNameString)
                if (unflattened != null && unflattened.packageName == context.packageName &&
                    unflattened.className.contains("NanoAccessibilityService")
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * Open Android Accessibility Settings screen.
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * Lock device screen via GLOBAL_ACTION_LOCK_SCREEN (API 28+).
         * Unlike Device Admin API, biometric/fingerprint unlock remains available.
         */
        fun lockScreen(): Boolean {
            val service = instance ?: run {
                Log.w(TAG, "lockScreen failed: NanoAccessibilityService not connected")
                return false
            }
            val success = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) result: $success")
            return success
        }

        /**
         * Capture clean screenshot.
         * On API 30+: uses takeScreenshot() with HardwareBuffer callback.
         * On API 28-29: falls back to performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT).
         *
         * @param executor Optional executor for callback (defaults to MainExecutor).
         * @param onSuccess Invoked with wrapped Bitmap and HardwareBuffer.
         *                  Caller is responsible for calling buffer.close() when finished.
         * @param onFailure Invoked with error code on failure.
         */
        fun takeScreenshot(
            executor: Executor? = null,
            onSuccess: ((Bitmap?, HardwareBuffer?) -> Unit)? = null,
            onFailure: ((Int) -> Unit)? = null
        ): Boolean {
            val service = instance ?: run {
                Log.w(TAG, "takeScreenshot failed: NanoAccessibilityService not connected")
                onFailure?.invoke(ERROR_SERVICE_NOT_CONNECTED)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val mainExec = executor ?: ContextCompat.getMainExecutor(service)
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExec,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val buffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = try {
                                Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to wrap hardware buffer into Bitmap", e)
                                null
                            }
                            Log.d(TAG, "Screenshot captured successfully: ${bitmap?.width}x${bitmap?.height}")
                            onSuccess?.invoke(bitmap, buffer)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "takeScreenshot failed with error code: $errorCode")
                            onFailure?.invoke(errorCode)
                        }
                    }
                )
                return true
            } else {
                Log.d(TAG, "API < 30: performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)")
                val success = service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                if (success) {
                    onSuccess?.invoke(null, null)
                } else {
                    onFailure?.invoke(ERROR_GLOBAL_ACTION_FAILED)
                }
                return success
            }
        }

        /**
         * Convenience overload for screenshot callback receiving only Bitmap.
         */
        fun takeScreenshot(callback: ((Bitmap?) -> Unit)?): Boolean {
            return takeScreenshot(
                onSuccess = { bitmap, buffer ->
                    try {
                        callback?.invoke(bitmap)
                    } finally {
                        buffer?.close()
                    }
                },
                onFailure = {
                    callback?.invoke(null)
                }
            )
        }
    }
}
