package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.domain.SettingsRepository
import java.io.File
import javax.inject.Inject

class RemoveWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = runCatching {
        // Delete active wallpaper files from noBackupFilesDir
        context.wallpaperFile.delete()
        context.wallpaperDarkFile.delete()
        context.wallpaperBlurredFile.delete()
        context.wallpaperBlurredDarkFile.delete()

        // Clean up any legacy wallpaper files from filesDir and cacheDir
        File(context.filesDir, "wallpaper.png").delete()
        File(context.filesDir, "wallpaperDark.png").delete()
        File(context.filesDir, "wallpaperBlurred.png").delete()
        File(context.filesDir, "wallpaperBlurredDark.png").delete()
        File(context.cacheDir, "wallpaper.png").delete()
        File(context.cacheDir, "wallpaperDark.png").delete()

        settingsRepository.updateSettings(
            settingsRepository.tileSettingsFlow.first().copy(
                wallpaperPath = null,
                wallpaperTimestamp = 0L
            )
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        android.util.Log.e("RemoveWallpaperUseCase", "Failed to remove wallpaper", e)
    }
}
