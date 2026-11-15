package tgo1014.gridlauncher.domain.models

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.app.Constants.defaultRadius
import java.io.File

@Serializable
data class TileSettings(
    val isTileFlipEnabled: Boolean = true,
    val cornerRadius: Int = defaultRadius,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
    val blurRadius: Int = 12,
    val tintAlpha: Float = 0.10f,
) {

    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }

    val wallpaperBlurredFile: File?
        get() {
            if (wallpaperPath == null) return null
            val file = File(wallpaperPath)
            val parent = file.parentFile
            val blurredName = if (file.name.contains("Dark", ignoreCase = true)) {
                "wallpaperBlurredDark.png"
            } else {
                "wallpaperBlurred.png"
            }
            return File(parent, blurredName).takeIf { it.exists() } ?: file
        }

    val hasBlurredWallpaper: Boolean
        get() {
            if (wallpaperPath == null) return false
            val file = File(wallpaperPath)
            val parent = file.parentFile
            val blurredName = if (file.name.contains("Dark", ignoreCase = true)) {
                "wallpaperBlurredDark.png"
            } else {
                "wallpaperBlurred.png"
            }
            return File(parent, blurredName).exists()
        }

    val isTransparencyEnabled: Boolean
        get() = wallpaperPath != null
}