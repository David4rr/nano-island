package dev.nanoIsland

import android.app.Notification
import android.os.Build
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class NanoNotificationListener : NotificationListenerService() {

    private val recentCache = LruCache<String, NotificationState>(100)
    val dismissedKeys = LruCache<String, Boolean>(200)

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        instance = this
        Log.d(TAG, "NanoNotificationListener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "NanoNotificationListener disconnected, requesting rebind")
        try {
            requestRebind(ComponentName(this, NanoNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request rebind", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        if (instance === this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        handleNotificationPosted(sbn, rankingMap)
    }

    private fun handleNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        Log.d(TAG, "onNotificationPosted: pkg=${sbn.packageName} key=${sbn.key}")

        if (dismissedKeys.get(sbn.key) == true) {
            Log.d(TAG, "Notification was dismissed during snooze, cancelling now: ${sbn.key}")
            dismissedKeys.remove(sbn.key)
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel dismissed notification", e)
            }
            return
        }

        if (!isConnected) {
            Log.d(TAG, "Not connected, ignoring")
            return
        }

        if (!isHighImportance(sbn, rankingMap)) {
            Log.d(TAG, "Not high importance, ignoring")
            return
        }

        // SNOOZE IMMEDIATELY: prevents native heads-up banner before any checks or UI dispatches
        safeSnooze(sbn.key, 15_000L)

        // If it's a pure group summary with no distinct text, snooze is sufficient to suppress banner
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            Log.d(TAG, "Group summary notification snoozed to suppress banner: ${sbn.key}")
        }

        if (isDuplicate(sbn)) {
            Log.d(TAG, "Duplicate notification snoozed to prevent double banner: ${sbn.key}")
            return
        }

        Log.d(TAG, "Intercepting high importance notification: ${sbn.key}")
        val payload = extractPayload(sbn)
        _notificationFlow.tryEmit(payload)
        IslandWindowManager.onNotificationReceived(payload)
    }

    private fun isHighImportance(sbn: StatusBarNotification, rankingMap: RankingMap?): Boolean {
        if (sbn.packageName == packageName && sbn.tag != "test_heads_up") return false

        val rMap = rankingMap ?: currentRanking
        if (rMap != null) {
            val ranking = Ranking()
            if (rMap.getRanking(sbn.key, ranking)) {
                return ranking.importance >= NotificationManager.IMPORTANCE_HIGH
            }
        }

        @Suppress("DEPRECATION")
        return sbn.notification.priority >= Notification.PRIORITY_HIGH
    }

    private fun isDuplicate(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val titleHash = title.hashCode()
        val textHash = text.hashCode()

        val trackId = extras.getString("androidx.media3.session.TrackId")
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val playbackState = extras.getInt("android.media.playbackState", -1).takeIf { it != -1 }

        val currentState = NotificationState(
            titleHash = titleHash,
            textHash = textHash,
            playbackState = playbackState,
            trackId = trackId
        )

        val cached = recentCache.get(sbn.key)
        if (cached != null && cached == currentState) {
            return true
        }

        recentCache.put(sbn.key, currentState)
        return false
    }

    private fun extractPayload(sbn: StatusBarNotification): NotificationPayload {
        val notification = sbn.notification
        val extras = notification.extras

        var title = extras.getCharSequence("android.conversationTitle")?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

        var text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                val lastMsg = messages.last()
                if (lastMsg is android.os.Bundle) {
                    val msgText = lastMsg.getCharSequence("text")?.toString()
                    val sender = lastMsg.getCharSequence("sender")?.toString()
                    if (!msgText.isNullOrEmpty()) {
                        text = msgText
                    }
                    if (!sender.isNullOrEmpty() && title.isNullOrEmpty()) {
                        title = sender
                    }
                }
            }
        }
        var iconBitmap: Bitmap? = null
        try {
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                val drawable = largeIcon.loadDrawable(this)
                if (drawable != null) {
                    iconBitmap = drawableToBitmap(drawable)
                }
            }
            if (iconBitmap == null && notification.smallIcon != null) {
                val smallDrawable = notification.smallIcon.loadDrawable(this)
                if (smallDrawable != null) {
                    iconBitmap = drawableToBitmap(smallDrawable)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting notification icon", e)
        }

        return NotificationPayload(
            key = sbn.key,
            title = title,
            text = text,
            icon = iconBitmap,
            packageName = sbn.packageName
        )
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val TAG = "NanoNotifListener"

        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        var instance: NanoNotificationListener? = null
            private set

        private val _notificationFlow = MutableSharedFlow<NotificationPayload>(
            extraBufferCapacity = 16
        )
        val notificationFlow: SharedFlow<NotificationPayload> = _notificationFlow.asSharedFlow()

        fun safeSnooze(key: String, durationMs: Long = 5_000L): Boolean {
            val listener = instance
            if (!isConnected || listener == null) return false
            return try {
                listener.snoozeNotification(key, durationMs)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to snooze notification $key", e)
                false
            }
        }

        fun dismissNotification(key: String): Boolean {
            val listener = instance
            if (!isConnected || listener == null) return false
            listener.dismissedKeys.put(key, true)
            return try {
                listener.cancelNotification(key)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel notification $key", e)
                false
            }
        }
    }
}
