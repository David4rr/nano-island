package dev.nanoIsland

import android.graphics.Bitmap

data class NotificationState(
    val titleHash: Int,
    val textHash: Int = 0,
    val playbackState: Int? = null,
    val trackId: String? = null
)

data class NotificationPayload(
    val key: String,
    val title: String?,
    val text: String?,
    val icon: Bitmap? = null,
    val packageName: String
)
