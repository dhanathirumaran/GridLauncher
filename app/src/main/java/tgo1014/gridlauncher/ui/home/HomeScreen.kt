package tgo1014.gridlauncher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import tgo1014.gridlauncher.ui.composables.LocalAppNotifications
import tgo1014.gridlauncher.ui.composables.LocalSmartTileData
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val smartTileData by viewModel.smartTileData.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsFlow.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalSmartTileData provides smartTileData,
        LocalAppNotifications provides notifications
    ) {
        HomeScreen(
            state = state,
            onHomeScrolled = viewModel::onSwitchedToHome,
            onItemClicked = viewModel::onGridItemClicked,
            onItemLongClicked = viewModel::onGridItemLongClicked,
            onSettingsEvent = viewModel::onSettingsEvent,
            onTileEvent = viewModel::onTileEvent,
        )
    }
}

@Composable
private fun HomeScreen(
    state: HomeState,
    onHomeScrolled: () -> Unit = {},
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemLongClicked: (item: GridItem) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {},
    onTileEvent: (TileEvent) -> Unit = {},
) = Box(modifier = Modifier.fillMaxSize()) {
    val hazeState = remember { HazeState() }
    // NATIVE SYSTEM WALLPAPER PASSTHROUGH:
    // Android WindowManager displays the phone's native system wallpaper (static, live, or dynamic)
    // directly through the transparent window (windowShowWallpaper="true" and @android:color/transparent
    // in themes.xml). No background image is drawn inside Compose, eliminating GPU overdraw, ensuring
    // zero-latency live wallpaper motion, and guaranteeing system settings wallpaper synchronization.
    GridScreenScreen(
        state = state,
        hazeState = hazeState,
        onHomeScrolled = onHomeScrolled,
        onItemClicked = onItemClicked,
        onItemLongClicked = onItemLongClicked,
        onSettingsEvent = onSettingsEvent,
        onTileEvent = onTileEvent,
    )
}