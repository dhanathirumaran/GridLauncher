package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import java.io.File

val Context.wallpaperFile: File
    get() {
        val filesDirFile = File(filesDir, "wallpaper.png")
        val cacheDirFile = File(cacheDir, "wallpaper.png")
        if (!filesDirFile.exists() && cacheDirFile.exists()) {
            runCatching { cacheDirFile.copyTo(filesDirFile, overwrite = true) }
        }
        return filesDirFile
    }

val Context.wallpaperDarkFile: File
    get() {
        val filesDirFile = File(filesDir, "wallpaperDark.png")
        val cacheDirFile = File(cacheDir, "wallpaperDark.png")
        if (!filesDirFile.exists() && cacheDirFile.exists()) {
            runCatching { cacheDirFile.copyTo(filesDirFile, overwrite = true) }
        }
        return filesDirFile
    }


val AppHazeStyle: HazeStyle
    @Composable get() = HazeStyle(
        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
        tint = HazeTint.Color(androidx.compose.ui.graphics.Color.White.copy(0.10f)),
        blurRadius = 12.dp,
        noiseFactor = 0f
    )