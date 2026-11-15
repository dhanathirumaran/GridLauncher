package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
        context.isSystemDarkTheme && context.wallpaperDarkFile.exists() -> context.wallpaperDarkFile
        context.isSystemDarkTheme && context.wallpaperFile.exists() -> {
            val bitmap = context.wallpaperFile.toBitmap()
            if (bitmap != null) {
                bitmap.reduceBitmapBrightness().saveToFile(context.wallpaperDarkFile)
                context.wallpaperDarkFile
            } else {
                context.wallpaperFile
            }
        }
        else -> context.wallpaperFile
    }

}