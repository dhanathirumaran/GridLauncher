package tgo1014.gridlauncher.data

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.materialkolor.ktx.themeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tgo1014.gridlauncher.domain.SettingsRepository
import tgo1014.gridlauncher.domain.ThemeRepository
import tgo1014.gridlauncher.domain.ThemeRepository.Theme
import tgo1014.gridlauncher.domain.ThemeRepository.Theme.SystemDefault
import tgo1014.gridlauncher.domain.models.TileSettings
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) : ThemeRepository {

    private val _currentTheme = MutableStateFlow<Theme>(SystemDefault)
    override val currentTheme: StateFlow<Theme> = _currentTheme.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        settingsRepository.tileSettingsFlow
            .onEach { settings -> _currentTheme.value = getCurrentTheme(settings) }
            .launchIn(scope)
    }

    private var lastWallpaperPath: String? = null
    private var cachedSeedColor: Color? = null

    private fun getCurrentTheme(settings: TileSettings): Theme {
        val file = settings.wallpaperFile ?: return SystemDefault
        if (!settings.isTransparencyEnabled) {
            return SystemDefault
        }
        val path = settings.wallpaperPath
        val seedColor = if (path == lastWallpaperPath && cachedSeedColor != null) {
            cachedSeedColor
        } else {
            val color = calculateSeedColor(file)
            lastWallpaperPath = path
            cachedSeedColor = color
            color
        }
        return if (seedColor == null) {
            SystemDefault
        } else {
            Theme.Wallpaper(seedColor)
        }
    }

    private fun calculateSeedColor(wallpaperFile: File): Color? {
        val bitmap = imageBitmapFromFile(wallpaperFile) ?: return null
        val suitableColor = bitmap.themeColors(fallback = Color.Unspecified).first()
        return if (suitableColor == Color.Unspecified) {
            null
        } else {
            suitableColor
        }
    }

    private fun imageBitmapFromFile(file: File): ImageBitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                // Downsample to max ~256x256 for color extraction to save memory
                val maxDim = maxOf(options.outWidth, options.outHeight)
                var sampleSize = 1
                while (maxDim / sampleSize > 256) {
                    sampleSize *= 2
                }
                options.inSampleSize = sampleSize
                options.inJustDecodeBounds = false
                FileInputStream(file).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            } else {
                FileInputStream(file).use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, file.name)
                    drawable?.toBitmap()?.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}