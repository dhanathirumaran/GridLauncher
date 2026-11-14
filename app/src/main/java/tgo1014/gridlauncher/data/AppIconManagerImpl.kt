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

    private fun getIconFromCache(packageName: String): Icon? {
        val icon = File(context.cacheDir, packageName.appIconPath)
        if (!icon.exists()) {
            return null
        }
        val bg = File(context.cacheDir, packageName.appBgPath)
        return Icon(
            iconFilePath = icon.absolutePath,
            bgFilePath = bg.takeIf { it.exists() }?.absolutePath
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
            Icon(
                iconFilePath = this@cache.icon?.cacheImage(packageName.appIconPath)?.absolutePath,
                bgFilePath = this@cache.bg?.cacheImage(packageName.appBgPath)?.absolutePath,
            )
        }
    }

    private val String.appIconPath get() = "${this}_icon_mono.png"
    private val String.appBgPath get() = "${this}_bg_mono.png"

    private fun Bitmap.cacheImage(fileName: String): File {
        val cacheFile = File(context.cacheDir, fileName)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        FileOutputStream(cacheFile).use { fos ->
            this.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return cacheFile
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