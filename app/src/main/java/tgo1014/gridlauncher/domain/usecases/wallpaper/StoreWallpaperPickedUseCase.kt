package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    suspend operator fun invoke(uri: Uri) = runCatching {
        withContext(Dispatchers.IO) {
            val metrics = context.resources.displayMetrics
            val reqWidth = metrics.widthPixels.coerceAtLeast(1080)
            val reqHeight = metrics.heightPixels.coerceAtLeast(1920)

            val bitmap = decodeSampledBitmapFromUri(uri, reqWidth, reqHeight)
                ?: decodeDrawableBitmapFromUri(uri)
                ?: return@withContext

            val file = saveBitmapAndGet(bitmap)
            val settings = settingsRepository.tileSettingsFlow.first()
            settingsRepository.updateSettings(
                settings.copy(wallpaperPath = file.absolutePath)
            )
        }
    }.onFailure(::println)

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun decodeDrawableBitmapFromUri(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val drawable = Drawable.createFromStream(inputStream, uri.toString()) ?: return null
            drawableToBitmap(drawable)
        }
    }

    private fun saveBitmapAndGet(bitmap: Bitmap): File {
        bitmap.saveToFile(context.wallpaperFile)
        bitmap.reduceBitmapBrightness().saveToFile(context.wallpaperDarkFile)
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