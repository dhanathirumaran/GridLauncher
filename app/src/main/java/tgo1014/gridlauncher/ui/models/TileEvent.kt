package tgo1014.gridlauncher.ui.models

import tgo1014.gridlauncher.domain.models.Direction

sealed class TileEvent {
    data class OnTilePositionChanged(val itemId: Int, val targetX: Int, val targetY: Int) : TileEvent()
    data class OnTileDimensionChanged(val itemId: Int, val newWidth: Int, val newHeight: Int) : TileEvent()
    data class OnShortcutRemoved(val item: GridItem) : TileEvent()
    data class OnAppUninstalled(val item: GridItem) : TileEvent()
    data object OnDismissEditMode : TileEvent()

    data class OnTileMoved(val direction: Direction) : TileEvent()
    data object OnRemoveClicked : TileEvent()
    data object OnTileSettingsSheetDismissed : TileEvent()
}