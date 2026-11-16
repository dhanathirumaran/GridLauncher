package tgo1014.gridlauncher.data.smarttile

import android.graphics.Bitmap
import android.os.SystemClock

data class MediaPlaybackState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArt: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val lastPositionUpdateTimeMs: Long = 0L,
    val packageName: String = ""
) {
    val livePositionMs: Long
        get() {
            if (!isPlaying || lastPositionUpdateTimeMs <= 0L) return positionMs
            val elapsed = SystemClock.elapsedRealtime() - lastPositionUpdateTimeMs
            return if (durationMs > 0) {
                (positionMs + elapsed).coerceIn(0L, durationMs)
            } else {
                (positionMs + elapsed).coerceAtLeast(0L)
            }
        }
}
