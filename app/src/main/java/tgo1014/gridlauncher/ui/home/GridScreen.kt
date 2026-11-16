package tgo1014.gridlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import androidx.activity.compose.BackHandler
import tgo1014.gridlauncher.ui.composables.TileLayout
import tgo1014.gridlauncher.ui.composables.sheets.SettingsBottomSheet
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import tgo1014.gridlauncher.ui.theme.conditional

@Composable
fun GridScreenScreen(
    state: HomeState,
    hazeState: HazeState = remember { HazeState() },
    onHomeScrolled: () -> Unit = {},
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemLongClicked: (item: GridItem) -> Unit = {},
    onTileEvent: (TileEvent) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {},
) {
    BackHandler(enabled = state.itemBeingEdited != null) {
        onTileEvent(TileEvent.OnDismissEditMode)
    }

    TileLayout(
        grid = state.grid,
        tileSettings = state.tileSettings,
        hazeState = hazeState,
        itemBeingEdited = state.itemBeingEdited,
        goToHome = state.goToHome,
        onHomeScrolled = onHomeScrolled,
        onItemClicked = onItemClicked,
        onItemLongClicked = onItemLongClicked,
        onTilePositionChanged = { id, targetX, targetY ->
            onTileEvent(TileEvent.OnTilePositionChanged(id, targetX, targetY))
        },
        onTileDimensionChanged = { id, newW, newH ->
            onTileEvent(TileEvent.OnTileDimensionChanged(id, newW, newH))
        },
        onShortcutRemoved = { shortcutItem ->
            onTileEvent(TileEvent.OnShortcutRemoved(shortcutItem))
        },
        onAppUninstalled = { appItem ->
            onTileEvent(TileEvent.OnAppUninstalled(appItem))
        },
        onDismissEditMode = {
            onTileEvent(TileEvent.OnDismissEditMode)
        },
        footer = { modifier ->
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            ) {
                SettingsIcon(
                    modifier = modifier,
                    tileSettings = state.tileSettings,
                    onClicked = { onSettingsEvent(SettingsEvent.OnSettingsIconClicked) }
                )
            }
        },
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxSize()
    )
    SettingsBottomSheet(
        tileSettings = state.tileSettings,
        isShowing = state.isSettingsSheetShowing,
        isDefaultLauncher = state.isDefaultLauncher,
        isNotificationAccessGranted = state.isNotificationAccessGranted,
        isCalendarPermissionGranted = state.isCalendarPermissionGranted,
        onSettingsEvent = onSettingsEvent
    )
}


@Composable
@Preview
private fun SettingsIcon(
    modifier: Modifier = Modifier,
    onClicked: () -> Unit = {},
    tileSettings: TileSettings = TileSettings()
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .aspectRatio(1f)
    ) {
        val shape = CircleShape
        if (tileSettings.isTransparencyEnabled) {
            val topRimAlpha = (tileSettings.tintAlpha * 2.5f).coerceIn(0.40f, 0.75f)
            val bottomRimAlpha = (tileSettings.tintAlpha * 0.8f).coerceIn(0.12f, 0.30f)
            val rimBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = topRimAlpha),
                    Color.White.copy(alpha = bottomRimAlpha)
                )
            )
            val topAlpha = (tileSettings.tintAlpha * 1.6f).coerceIn(0.10f, 0.90f)
            val bottomAlpha = (tileSettings.tintAlpha * 0.85f).coerceIn(0.05f, 0.80f)
            val glassBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = topAlpha),
                    Color.White.copy(alpha = bottomAlpha)
                )
            )
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(glassBrush)
                    .border(1.dp, rimBrush, shape)
                    .clickable { onClicked() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        } else {
            val contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onClicked() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = contentColor
                )
            }
        }
    }
}


@Composable
@Preview
private fun PreviewSmallTile() = GridLauncherTheme {
    GridScreenScreen(
        state = HomeState(
            grid = listOf(
                GridItem(app = App("はい"), width = 1),
            )
        ),
    )
}

@Composable
@Preview
private fun Preview() = GridLauncherTheme {
    GridScreenScreen(
        state = HomeState(
            grid = listOf(
                GridItem(app = App("وأصدقاؤك"), width = 2),
                GridItem(app = App("123"), width = 2, x = 2),
                GridItem(app = App("#1231"), width = 2, x = 4),
                GridItem(app = App("$$$$"), width = 2, y = 2),
                GridItem(app = App("FooBar"), width = 2, y = 2, x = 2),
                GridItem(app = App("Aaaa"), width = 2, y = 2, x = 4),
                GridItem(app = App("AAb"), width = 2, y = 4),
                GridItem(app = App("はい"), width = 2, y = 4, x = 2),
            )
        ),
    )
}