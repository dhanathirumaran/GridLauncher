package tgo1014.gridlauncher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import tgo1014.gridlauncher.ui.composables.LocalAppNotifications
import tgo1014.gridlauncher.ui.composables.LocalSmartTileData
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.AsyncImage
import tgo1014.gridlauncher.ui.theme.modifyIf

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
    if (state.tileSettings.isTransparencyEnabled) {
        val blurredFile = state.tileSettings.wallpaperBlurredFile
        val hasBlurred = state.tileSettings.hasBlurredWallpaper && blurredFile != null

        // Layer 1: Pre-blurred wallpaper underlay (captured into Haze for zero real-time GPU shader cost)
        if (hasBlurred) {
            key(blurredFile) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(blurredFile)
                        .crossfade(true)
                        .build(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .haze(state = hazeState)
                )
            }
        }

        // Layer 2: Sharp wallpaper (displayed across screen background and in the gaps between tiles)
        key(state.tileSettings.wallpaperFile) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(state.tileSettings.wallpaperFile)
                    .crossfade(true)
                    .build(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .modifyIf(!hasBlurred) {
                        haze(state = hazeState)
                    }
            )
        }
    }
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