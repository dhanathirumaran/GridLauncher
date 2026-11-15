package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.data.fastBlur
import tgo1014.gridlauncher.data.isSystemDarkTheme
import tgo1014.gridlauncher.data.reduceBitmapBrightness
import tgo1014.gridlauncher.data.saveToFile
import tgo1014.gridlauncher.data.toBitmap
import tgo1014.gridlauncher.domain.SettingsRepository
import java.io.File
import javax.inject.Inject

class UpdateWallpaperBasedOnThemeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = runCatching {
        val settings = settingsRepository.tileSettingsFlow.first()
        if (settings.wallpaperPath == null || !context.wallpaperFile.exists()) {
            return@runCatching
        }
        val file = getWallpaperFileForTheme()
        if (file.exists()) {
            settingsRepository.updateSettings(
                settings.copy(wallpaperPath = file.absolutePath)
            )
        }
    }.onFailure(::println)

    private fun getWallpaperFileForTheme(): File = when {
        context.isSystemDarkTheme && context.wallpaperDarkFile.exists() -> {
            ensureBlurredWallpaperExists(context.wallpaperDarkFile, context.wallpaperBlurredDarkFile)
            context.wallpaperDarkFile
        }
        context.isSystemDarkTheme && context.wallpaperFile.exists() -> {
            val bitmap = context.wallpaperFile.toBitmap()
            if (bitmap != null) {
                bitmap.reduceBitmapBrightness().saveToFile(context.wallpaperDarkFile)
                val blurred = bitmap.fastBlur(radius = 22)
                blurred.saveToFile(context.wallpaperBlurredFile)
                blurred.reduceBitmapBrightness().saveToFile(context.wallpaperBlurredDarkFile)
                context.wallpaperDarkFile
            } else {
                context.wallpaperFile
            }
        }
        else -> {
            ensureBlurredWallpaperExists(context.wallpaperFile, context.wallpaperBlurredFile)
            context.wallpaperFile
        }
    }

    private fun ensureBlurredWallpaperExists(sourceFile: File, blurredTargetFile: File) {
        if (!blurredTargetFile.exists() && sourceFile.exists()) {
            sourceFile.toBitmap()?.let { bmp ->
                bmp.fastBlur(radius = 22).saveToFile(blurredTargetFile)
            }
        }
    }
}