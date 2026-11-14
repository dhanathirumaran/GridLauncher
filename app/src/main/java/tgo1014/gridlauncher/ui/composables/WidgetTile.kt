package tgo1014.gridlauncher.ui.composables

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import tgo1014.gridlauncher.data.widget.LauncherAppWidgetHost
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.tileEditMode

val LocalAppWidgetHost = compositionLocalOf<LauncherAppWidgetHost?> { null }
val LocalAppWidgetManager = compositionLocalOf<AppWidgetManager?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetTile(
    item: GridItem,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    modifier: Modifier = Modifier,
    tileSettings: TileSettings = TileSettings(),
    isEditMode: Boolean = false,
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemLongClicked: (item: GridItem) -> Unit = {},
) {
    val shape = RoundedCornerShape(tileSettings.cornerRadius)
    val appWidgetInfo = remember(item.appWidgetId) {
        if (item.appWidgetId != -1) {
            appWidgetManager.getAppWidgetInfo(item.appWidgetId)
        } else null
    }

    LaunchedEffect(item.width, item.height, item.appWidgetId) {
        if (item.appWidgetId != -1) {
            val widthDp = item.width * 48
            val heightDp = item.height * 48
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            runCatching {
                appWidgetManager.updateAppWidgetOptions(item.appWidgetId, options)
            }
        }
    }

    Box(
        modifier = Modifier
            .tileEditMode(isEditMode)
            .clip(shape)
            .then(modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (tileSettings.isTransparencyEnabled) Color.Black.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surface
                )
        ) {
            if (appWidgetInfo != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .graphicsLayer {
                            alpha = if (tileSettings.isTransparencyEnabled) 0.68f else 1.0f
                        },
                    factory = { context ->
                        val config = Configuration(context.resources.configuration).apply {
                            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                        }
                        val widgetContext = context.createConfigurationContext(config)
                        val hostView = appWidgetHost.createView(widgetContext, item.appWidgetId, appWidgetInfo)
                        hostView.setAppWidget(item.appWidgetId, appWidgetInfo)
                        hostView.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        hostView.setOnLongClickListener {
                            onItemLongClicked(item)
                            true
                        }
                        hostView
                    },
                    update = { hostView ->
                        if (hostView.appWidgetId != item.appWidgetId) {
                            hostView.setAppWidget(item.appWidgetId, appWidgetInfo)
                        }
                        hostView.setOnLongClickListener {
                            onItemLongClicked(item)
                            true
                        }
                    }
                )

                // Theme Tint Overlay to blend the widget card into the launcher's accent color
                if (tileSettings.isTransparencyEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shape)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Widget Unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Overlay in edit mode to intercept clicks for repositioning and resizing
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .combinedClickable(
                        onClick = { onItemClicked(item) },
                        onLongClick = { onItemLongClicked(item) }
                    )
            )
        }
    }
}
