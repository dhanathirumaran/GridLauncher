package tgo1014.gridlauncher.domain.models

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.app.Constants.defaultRadius
import java.io.File

/**
 * Persisted visual and behavioral settings for GridLauncher tiles and window appearance.
 *
 * SYSTEM WALLPAPER ARCHITECTURE:
 * - GridLauncher uses Android's native `windowShowWallpaper="true"`.
 * - In earlier versions, `isTransparencyEnabled` was hardcoded to `wallpaperPath != null`,
 *   which forced users to manually pick a photo to get transparent tiles.
 * - By decoupling `isTransparentTilesEnabled: Boolean = true`, tiles render as sleek translucent
 *   glass panels floating directly over the active system wallpaper (static, live, or dynamic)
 *   out of the box, without requiring any manual image picking.
 */
@Serializable
data class TileSettings(
    val isTileFlipEnabled: Boolean = true,
    val cornerRadius: Int = defaultRadius,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
    val blurRadius: Int = 12,
    val tintAlpha: Float = 0.10f,
    val isTransparentTilesEnabled: Boolean = true,
) {

    /**
     * Legacy wallpaper files are permanently nullified so that Android's native
     * WindowManager system wallpaper passthrough (windowShowWallpaper=true)
     * is always active without custom image file overrides.
     */
    val wallpaperFile: File?
        get() = null

    val wallpaperBlurredFile: File?
        get() = null

    val hasBlurredWallpaper: Boolean
        get() = false

    /**
     * Controls whether tiles render as translucent glass panels.
     * Defaults to true, enabling immediate system wallpaper passthrough.
     */
    val isTransparencyEnabled: Boolean
        get() = isTransparentTilesEnabled
}