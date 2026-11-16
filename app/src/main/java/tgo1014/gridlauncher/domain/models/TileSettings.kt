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
 */
@Serializable
data class TileSettings(
    val isTileFlipEnabled: Boolean = true,
    val cornerRadius: Int = defaultRadius,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
    val blurRadius: Int = 30,
    val tintAlpha: Float = 0.10f,
) {
    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }?.takeIf { it.exists() }

    val isTransparencyEnabled: Boolean
        get() = wallpaperPath != null
}