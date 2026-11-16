package tgo1014.gridlauncher.domain.models

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.app.Constants.defaultRadius
import java.io.File

/**
 * Persisted visual and behavioral settings for GridLauncher tiles and wallpaper appearance.
 *
 * WALLPAPER & FROSTED GLASS BLUR ARCHITECTURE:
 * - Transparency and real-time frosted glass blur are enabled when a wallpaper is picked.
 * - When [wallpaperPath] is set, [wallpaperFile] provides the source bitmap for Compose and Haze,
 *   allowing tiles to render with real-time frosted blur (up to 30 dp) and glass tint opacity.
 * - [wallpaperTimestamp] tracks the exact epoch millisecond when a wallpaper is stored or updated.
 *   This is essential for cache invalidation: because the destination file path is always identical
 *   ("wallpaper.png"), image loaders (Coil) and Compose's `key(...)` use this timestamp to detect
 *   when a new wallpaper has been picked, immediately bypassing stale memory caches.
 */
@Serializable
data class TileSettings(
    val isTileFlipEnabled: Boolean = true,
    val cornerRadius: Int = defaultRadius,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
    val wallpaperTimestamp: Long = 0L,
    val blurRadius: Int = 30,
    val tintAlpha: Float = 0.10f,
) {
    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }?.takeIf { it.exists() }

    val isTransparencyEnabled: Boolean
        get() = wallpaperPath != null
}