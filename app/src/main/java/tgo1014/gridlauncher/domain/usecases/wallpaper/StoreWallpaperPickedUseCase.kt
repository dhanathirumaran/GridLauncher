package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.data.fastBlur
import tgo1014.gridlauncher.data.isSystemDarkTheme
import tgo1014.gridlauncher.data.reduceBitmapBrightness
import tgo1014.gridlauncher.data.saveToFile
import tgo1014.gridlauncher.domain.SettingsRepository
import java.io.File
import javax.inject.Inject

class StoreWallpaperPickedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Decodes and persists the picked wallpaper bitmap and updates [TileSettings.wallpaperTimestamp].
     *
     * CACHE INVALIDATION & STREAM RESILIENCE:
     * 1. Reading URI bytes once into memory prevents issues where Android's PhotoPicker temporary
     *    stream grant cannot be reopened for a second bounds-checking pass.
     * 2. Updating [TileSettings.wallpaperTimestamp] guarantees that Compose and Coil detect that
     *    the wallpaper has changed, immediately invalidating any stale bitmap in memory cache.
     */
    suspend operator fun invoke(uri: Uri) = runCatching {
        withContext(Dispatchers.IO) {
            val metrics = context.resources.displayMetrics
            val reqWidth = metrics.widthPixels.coerceAtLeast(1080)
            val reqHeight = metrics.heightPixels.coerceAtLeast(1920)

            // Read the URI bytes safely once to avoid multiple stream open issues with PhotoPicker
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext

            val bitmap = decodeSampledBitmapFromBytes(bytes, reqWidth, reqHeight)
                ?: decodeDrawableBitmapFromBytes(bytes)
                ?: return@withContext

            val file = saveBitmapAndGet(bitmap)
            val settings = settingsRepository.tileSettingsFlow.first()
            settingsRepository.updateSettings(
                settings.copy(
                    wallpaperPath = file.absolutePath,
                    wallpaperTimestamp = System.currentTimeMillis()
                )
            )
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        e.printStackTrace()
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeDrawableBitmapFromBytes(bytes: ByteArray): Bitmap? {
        val drawable = Drawable.createFromStream(bytes.inputStream(), "wallpaper") ?: return null
        return drawableToBitmap(drawable)
    }

    private fun saveBitmapAndGet(bitmap: Bitmap): File {
        bitmap.saveToFile(context.wallpaperFile)
        bitmap.reduceBitmapBrightness().saveToFile(context.wallpaperDarkFile)

        // Pre-blurred wallpaper for zero real-time GPU blur cost during scrolling
        val blurred = bitmap.fastBlur(radius = 22)
        blurred.saveToFile(context.wallpaperBlurredFile)
        blurred.reduceBitmapBrightness().saveToFile(context.wallpaperBlurredDarkFile)

        return if (context.isSystemDarkTheme) {
            context.wallpaperDarkFile
        } else {
            context.wallpaperFile
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        var bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        return bitmap
    }

    companion object {
        fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }
    }
}
