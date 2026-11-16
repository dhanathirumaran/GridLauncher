package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import java.io.File

/**
 * Storage location for wallpaper bitmap files.
 *
 * BACKUP QUOTA ARCHITECTURE:
 * Wallpapers are stored in `noBackupFilesDir` rather than `filesDir`.
 *
 * Android Auto Backup (cloud backup to Google Drive) enforces a strict 25 MB quota per app.
 * High-resolution wallpaper PNGs (full screen, dark variant, pre-blurred, and dark pre-blurred)
 * can easily occupy 15-20 MB in total. Storing them in `filesDir` risks exhausting the 25 MB
 * limit, causing cloud backup failures or bloat.
 *
 * `noBackupFilesDir` is permanent internal storage that persists across app restarts and updates,
 * but is explicitly and automatically excluded from all Android cloud backups by the operating system.
 * Any legacy files from `filesDir` or `cacheDir` are automatically migrated on first access.
 */
private fun Context.getOrMigrateNoBackupFile(name: String): File {
    val targetFile = File(noBackupFilesDir, name)
    if (!targetFile.exists()) {
        val legacyFilesDirFile = File(filesDir, name)
        val legacyCacheFile = File(cacheDir, name)
        if (legacyFilesDirFile.exists()) {
            runCatching {
                legacyFilesDirFile.copyTo(targetFile, overwrite = true)
                legacyFilesDirFile.delete()
            }
        } else if (legacyCacheFile.exists()) {
            runCatching {
                legacyCacheFile.copyTo(targetFile, overwrite = true)
                legacyCacheFile.delete()
            }
        }
    }
    return targetFile
}

val Context.wallpaperFile: File
    get() = getOrMigrateNoBackupFile("wallpaper.png")

val Context.wallpaperDarkFile: File
    get() = getOrMigrateNoBackupFile("wallpaperDark.png")

val Context.wallpaperBlurredFile: File
    get() = getOrMigrateNoBackupFile("wallpaperBlurred.png")

val Context.wallpaperBlurredDarkFile: File
    get() = getOrMigrateNoBackupFile("wallpaperBlurredDark.png")
