package tgo1014.gridlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.AppIconManager
import tgo1014.gridlauncher.domain.models.DispatcherProvider
import tgo1014.gridlauncher.domain.models.Icon
import com.materialkolor.ktx.isLight
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject


class AppIconManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
) : AppIconManager {

    override suspend fun getIcon(packageName: String): Icon {
        return withContext(dispatcherProvider.io) {
            getIconFromCache(packageName) ?: cacheAngGetIcon(packageName)
        }
    }

    override suspend fun saveShortcutIcon(key: String, drawable: Drawable): String {
        return withContext(dispatcherProvider.io) {
            val safeKey = key.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val monoBitmap = drawable.asBitmap(256, 256).toMonochromeWhite()
            val file = monoBitmap.cacheImage("shortcut_${safeKey}_mono.png")
            file.absolutePath
        }
    }

    override suspend fun saveShortcutIcon(key: String, bitmap: Bitmap): String {
        return withContext(dispatcherProvider.io) {
            val safeKey = key.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val monoBitmap = bitmap.toMonochromeWhite()
            val file = monoBitmap.cacheImage("shortcut_${safeKey}_mono.png")
            file.absolutePath
        }
    }

    /**
     * Cache directory for application icons and shortcuts.
     *
     * BACKUP QUOTA ARCHITECTURE:
     * We use `context.noBackupFilesDir` instead of `context.filesDir`.
     *
     * Android Auto Backup enforces a 25 MB quota per app for cloud backups to Google Drive.
     * Storing hundreds of high-resolution launcher icon PNGs in `filesDir` consumes 10-20 MB
     * of cloud storage, risking backup quota exhaustion.
     *
     * `noBackupFilesDir` persists across app launches and updates, but is strictly excluded
     * from remote cloud backup by the Android OS. Any legacy icons from `filesDir/icons` or
     * `cacheDir` are automatically migrated on first lookup.
     */
    private val iconsDir: File
        get() = File(context.noBackupFilesDir, "icons").apply { if (!exists()) mkdirs() }

    private fun getIconFile(fileName: String): File {
        val file = File(iconsDir, fileName)
        if (!file.exists()) {
            val legacyFilesDir = File(context.filesDir, "icons/$fileName")
            if (legacyFilesDir.exists()) {
                runCatching {
                    legacyFilesDir.copyTo(file, overwrite = true)
                    legacyFilesDir.delete()
                }
            } else {
                val legacyCache = File(context.cacheDir, fileName)
                if (legacyCache.exists()) {
                    runCatching {
                        legacyCache.copyTo(file, overwrite = true)
                        legacyCache.delete()
                    }
                }
            }
        }
        return file
    }

    private fun getIconFromCache(packageName: String): Icon? {
        val icon = getIconFile(packageName.appIconPath)
        if (!icon.exists()) {
            return null
        }

        // ICON CACHE INVALIDATION ON APPLICATION UPDATE:
        // Keying cached icons solely by package name without timestamp invalidation causes updated
        // app branding to remain permanently stale.
        // We compare the cached PNG file's last modified timestamp against PackageManager's lastUpdateTime.
        // If the application was updated after the cached icon was generated, we purge the stale icon
        // and background files and return null, triggering a fresh icon extraction from PackageManager.
        val lastUpdateTime = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            }
        }.getOrNull()

        if (lastUpdateTime != null && lastUpdateTime > icon.lastModified()) {
            runCatching { icon.delete() }
            val bg = getIconFile(packageName.appBgPath)
            if (bg.exists()) runCatching { bg.delete() }
            return null
        }

        val bg = getIconFile(packageName.appBgPath)
        val bgFile = bg.takeIf { it.exists() }
        val isLight = bgFile?.toBitmap()?.getDominantColor()?.isLight() == true
        return Icon(
            iconFilePath = icon.absolutePath,
            bgFilePath = bgFile?.absolutePath,
            isLightBackground = isLight
        )
    }

    private suspend fun cacheAngGetIcon(packageName: String): Icon {
        return withContext(Dispatchers.IO) {
            val icon = getDynamicIconFromSystem(packageName)
                ?: getDefaultIconFromSystem(packageName)
            val cachedIcon = icon.cache(packageName)
            cachedIcon
        }
    }

    private suspend fun IconBitmap.cache(packageName: String): Icon {
        return withContext(Dispatchers.IO) {
            val iconFile = this@cache.icon?.cacheImage(packageName.appIconPath)
            val bgFile = this@cache.bg?.cacheImage(packageName.appBgPath)
            val isLight = this@cache.bg?.getDominantColor()?.isLight() == true
            Icon(
                iconFilePath = iconFile?.absolutePath,
                bgFilePath = bgFile?.absolutePath,
                isLightBackground = isLight
            )
        }
    }

    private val String.appIconPath get() = "${this}_icon_mono.png"
    private val String.appBgPath get() = "${this}_bg_mono.png"

    private fun Bitmap.cacheImage(fileName: String): File {
        val destFile = File(iconsDir, fileName)
        if (destFile.exists()) {
            destFile.delete()
        }
        FileOutputStream(destFile).use { fos ->
            this.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return destFile
    }

    private fun getDefaultIconFromSystem(packageName: String): IconBitmap {
        val rawBitmap = packageManager.getApplicationIcon(packageName).toBitmap()
        return IconBitmap(
            icon = rawBitmap.toMonochromeWhite()
        )
    }

    private fun getDynamicIconFromSystem(packageName: String): IconBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }
        return runCatching {
            val drawable = packageManager.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable) {
                return IconBitmap(icon = drawable.bitmap.toMonochromeWhite())
            }
            if (drawable is AdaptiveIconDrawable) {
                // On Android 13+ (API 33), check for official themed monochrome icon
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val mono = drawable.monochrome?.mutate()
                    if (mono != null) {
                        mono.setTint(android.graphics.Color.WHITE)
                        return IconBitmap(
                            icon = mono.asBitmap(),
                            bg = drawable.background.asBitmap()
                        )
                    }
                }
                // Convert foreground into clean white monochrome glyph
                val foregroundBitmap = drawable.foreground.asBitmap().toMonochromeWhite()
                return IconBitmap(
                    icon = foregroundBitmap,
                    bg = drawable.background.asBitmap()
                )
            }
            null
        }.getOrNull()
    }

    private fun Bitmap.toMonochromeWhite(): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        var hasTransparency = false
        var minLum = 255
        var maxLum = 0
        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xff
            if (a < 20) {
                hasTransparency = true
            } else {
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                if (lum < minLum) minLum = lum
                if (lum > maxLum) maxLum = lum
            }
        }

        val lumRange = maxLum - minLum
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel ushr 24) and 0xff
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

            val finalAlpha = if (hasTransparency) {
                if (lumRange > 60) {
                    val normalizedLum = (lum - minLum).toFloat() / lumRange
                    (a * (0.45f + 0.55f * normalizedLum)).toInt().coerceIn(0, 255)
                } else {
                    a
                }
            } else {
                val inverted = 255 - lum
                (inverted * (a / 255f)).toInt().coerceIn(0, 255)
            }
            pixels[i] = (finalAlpha shl 24) or 0x00FFFFFF
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private data class IconBitmap(
        val icon: Bitmap? = null,
        val bg: Bitmap? = null,
    )

    fun Drawable.asBitmap(
        widthPixels: Int = 500,
        heightPixels: Int = 500
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPixels, heightPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        this.setBounds(0, 0, widthPixels, heightPixels)
        this.draw(canvas)
        return bitmap
    }
}