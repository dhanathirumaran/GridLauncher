package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
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

val Context.wallpaperBlurredFile: File
    get() {
        val filesDirFile = File(filesDir, "wallpaperBlurred.png")
        val cacheDirFile = File(cacheDir, "wallpaperBlurred.png")
        if (!filesDirFile.exists() && cacheDirFile.exists()) {
            runCatching { cacheDirFile.copyTo(filesDirFile, overwrite = true) }
        }
        return filesDirFile
    }

val Context.wallpaperBlurredDarkFile: File
    get() {
        val filesDirFile = File(filesDir, "wallpaperBlurredDark.png")
        val cacheDirFile = File(cacheDir, "wallpaperBlurredDark.png")
        if (!filesDirFile.exists() && cacheDirFile.exists()) {
            runCatching { cacheDirFile.copyTo(filesDirFile, overwrite = true) }
        }
        return filesDirFile
    }
