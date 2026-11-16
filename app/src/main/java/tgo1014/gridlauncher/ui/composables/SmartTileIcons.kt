package tgo1014.gridlauncher.ui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object SmartTileIcons {
    val Search = Icons.Filled.Search
    val Lock = Icons.Filled.Lock

    val Flashlight: ImageVector by lazy {
        ImageVector.Builder(
            name = "Flashlight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 2f)
                lineTo(18f, 2f)
                lineTo(16f, 7f)
                lineTo(16f, 21f)
                curveTo(16f, 21.55f, 15.55f, 22f, 15f, 22f)
                lineTo(9f, 22f)
                curveTo(8.45f, 22f, 8f, 21.55f, 8f, 21f)
                lineTo(8f, 7f)
                close()
                moveTo(11f, 10f)
                lineTo(13f, 10f)
                lineTo(13f, 13f)
                lineTo(11f, 13f)
                close()
            }
        }.build()
    }

    val Walk: ImageVector by lazy {
        ImageVector.Builder(
            name = "Walk",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(13.5f, 5.5f)
                curveTo(14.33f, 5.5f, 15f, 4.83f, 15f, 4f)
                curveTo(15f, 3.17f, 14.33f, 2.5f, 13.5f, 2.5f)
                curveTo(12.67f, 2.5f, 12f, 3.17f, 12f, 4f)
                curveTo(12f, 4.83f, 12.67f, 5.5f, 13.5f, 5.5f)
                close()
                moveTo(13.5f, 8.9f)
                lineTo(15.2f, 12.5f)
                lineTo(18f, 14f)
                lineTo(17.2f, 15.5f)
                lineTo(14.8f, 14.2f)
                lineTo(13.8f, 12f)
                lineTo(12.7f, 13.5f)
                lineTo(14.5f, 21.5f)
                lineTo(12.5f, 22f)
                lineTo(11f, 15f)
                lineTo(9.5f, 17f)
                lineTo(9.5f, 22f)
                lineTo(7.5f, 22f)
                lineTo(7.5f, 15.5f)
                lineTo(10.2f, 12f)
                lineTo(11f, 9.8f)
                lineTo(9f, 10.7f)
                lineTo(8.2f, 9f)
                lineTo(12.1f, 7.2f)
                curveTo(12.6f, 7f, 13.2f, 7.7f, 13.5f, 8.9f)
                close()
            }
        }.build()
    }

    val Battery: ImageVector by lazy {
        ImageVector.Builder(
            name = "Battery",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(17f, 5f)
                lineTo(14f, 5f)
                lineTo(14f, 3f)
                lineTo(10f, 3f)
                lineTo(10f, 5f)
                lineTo(7f, 5f)
                curveTo(6.45f, 5f, 6f, 5.45f, 6f, 6f)
                lineTo(6f, 20f)
                curveTo(6f, 20.55f, 6.45f, 21f, 7f, 21f)
                lineTo(17f, 21f)
                curveTo(17.55f, 21f, 18f, 20.55f, 18f, 20f)
                lineTo(18f, 6f)
                curveTo(18f, 5.45f, 17.55f, 5f, 17f, 5f)
                close()
                moveTo(16f, 19f)
                lineTo(8f, 19f)
                lineTo(8f, 7f)
                lineTo(16f, 7f)
                lineTo(16f, 19f)
                close()
            }
        }.build()
    }

    val Headphone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Headphone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                lineTo(3f, 19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                lineTo(8f, 21f)
                lineTo(8f, 13f)
                lineTo(5f, 13f)
                lineTo(5f, 12f)
                curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
                curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
                lineTo(19f, 13f)
                lineTo(16f, 13f)
                lineTo(16f, 21f)
                lineTo(19f, 21f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                lineTo(21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                close()
            }
        }.build()
    }

    val Currency: ImageVector by lazy {
        ImageVector.Builder(
            name = "Currency",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(13f, 3f)
                lineTo(6f, 3f)
                lineTo(6f, 5f)
                lineTo(11f, 5f)
                curveTo(13.2f, 5f, 14.5f, 6.1f, 14.8f, 7f)
                lineTo(6f, 7f)
                lineTo(6f, 9f)
                lineTo(14.8f, 9f)
                curveTo(14.3f, 11f, 12.5f, 12f, 10.5f, 12f)
                lineTo(6f, 12f)
                lineTo(6f, 14f)
                lineTo(13.5f, 22f)
                lineTo(16.5f, 22f)
                lineTo(8.5f, 13.5f)
                curveTo(11.5f, 13.3f, 14.5f, 11.5f, 16.5f, 9f)
                lineTo(18f, 9f)
                lineTo(18f, 7f)
                lineTo(16.8f, 7f)
                curveTo(16.5f, 5f, 15f, 3f, 13f, 3f)
                close()
            }
        }.build()
    }

    val WeatherSun: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherSun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 7f)
                curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
                curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
                curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
                curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
                close()
                moveTo(11f, 1f)
                lineTo(13f, 1f)
                lineTo(13f, 4f)
                lineTo(11f, 4f)
                close()
                moveTo(11f, 20f)
                lineTo(13f, 20f)
                lineTo(13f, 23f)
                lineTo(11f, 23f)
                close()
                moveTo(1f, 11f)
                lineTo(4f, 11f)
                lineTo(4f, 13f)
                lineTo(1f, 13f)
                close()
                moveTo(20f, 11f)
                lineTo(23f, 11f)
                lineTo(23f, 13f)
                lineTo(20f, 13f)
                close()
            }
        }.build()
    }

    val WeatherCloud: ImageVector by lazy {
        ImageVector.Builder(
            name = "WeatherCloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
                lineTo(19f, 20f)
                curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
                curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
                close()
            }
        }.build()
    }

    val Umbrella: ImageVector by lazy {
        ImageVector.Builder(
            name = "Umbrella",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                lineTo(11f, 12f)
                lineTo(11f, 19.5f)
                curveTo(11f, 20.33f, 10.33f, 21f, 9.5f, 21f)
                curveTo(8.67f, 21f, 8f, 20.33f, 8f, 19.5f)
                lineTo(6f, 19.5f)
                curveTo(6f, 21.43f, 7.57f, 23f, 9.5f, 23f)
                curveTo(11.43f, 23f, 13f, 21.43f, 13f, 19.5f)
                lineTo(13f, 12f)
                lineTo(22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
            }
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Calendar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 3f)
                lineTo(18f, 3f)
                lineTo(18f, 1f)
                lineTo(16f, 1f)
                lineTo(16f, 3f)
                lineTo(8f, 3f)
                lineTo(8f, 1f)
                lineTo(6f, 1f)
                lineTo(6f, 3f)
                lineTo(5f, 3f)
                curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
                lineTo(3f, 19f)
                curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
                lineTo(19f, 21f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                lineTo(21f, 5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                close()
                moveTo(19f, 19f)
                lineTo(5f, 19f)
                lineTo(5f, 8f)
                lineTo(19f, 8f)
                lineTo(19f, 19f)
                close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(8f, 5f)
                lineTo(19f, 12f)
                lineTo(8f, 19f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 19f)
                lineTo(10f, 19f)
                lineTo(10f, 5f)
                lineTo(6f, 5f)
                close()
                moveTo(14f, 5f)
                lineTo(14f, 19f)
                lineTo(18f, 19f)
                lineTo(18f, 5f)
                close()
            }
        }.build()
    }

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipNext",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 18f)
                lineTo(14.5f, 12f)
                lineTo(6f, 6f)
                close()
                moveTo(16f, 6f)
                lineTo(18f, 6f)
                lineTo(18f, 18f)
                lineTo(16f, 18f)
                close()
            }
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 6f)
                lineTo(8f, 6f)
                lineTo(8f, 18f)
                lineTo(6f, 18f)
                close()
                moveTo(9.5f, 12f)
                lineTo(18f, 18f)
                lineTo(18f, 6f)
                close()
            }
        }.build()
    }

    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "MusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3f)
                lineTo(12f, 13.55f)
                curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
                curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
                curveTo(6f, 19.21f, 7.79f, 21f, 10f, 21f)
                curveTo(12.21f, 21f, 14f, 19.21f, 14f, 17f)
                lineTo(14f, 7f)
                lineTo(18f, 7f)
                lineTo(18f, 3f)
                close()
            }
        }.build()
    }

    val AlarmBell: ImageVector by lazy {
        ImageVector.Builder(
            name = "AlarmBell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 22f)
                curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
                lineTo(10f, 20f)
                curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
                close()
                moveTo(18f, 16f)
                lineTo(18f, 11f)
                curveTo(18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f)
                lineTo(13.5f, 4f)
                curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
                curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
                lineTo(10.5f, 4.68f)
                curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
                lineTo(6f, 16f)
                lineTo(4f, 18f)
                lineTo(4f, 19f)
                lineTo(20f, 19f)
                lineTo(20f, 18f)
                lineTo(18f, 16f)
                close()
            }
        }.build()
    }
}

