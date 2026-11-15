package tgo1014.gridlauncher.data.smarttile

import android.graphics.Bitmap

data class MediaPlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArt: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val packageName: String = ""
)
