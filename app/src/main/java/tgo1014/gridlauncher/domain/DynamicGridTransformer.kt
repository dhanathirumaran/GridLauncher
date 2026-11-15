package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SmartTileType

object DynamicGridTransformer {

    const val MEDIA_TILE_ID = -999

    /**
     * Dynamically inserts an 8x2 Live Music Player tile into Row 2 (directly below row 1 items)
     * and shifts all subsequent tiles down by 2 rows in memory when media is active.
     * When media is inactive, returns the base grid unmodified.
     */
    fun computeEffectiveGrid(
        baseGrid: List<GridItem>,
        isMediaActive: Boolean
    ): List<GridItem> {
        if (!isMediaActive || baseGrid.isEmpty()) return baseGrid

        val row0Items = baseGrid.filter { it.y == 0 }
        val insertionY = row0Items.maxOfOrNull { it.y + it.height } ?: 1

        val mediaTile = GridItem(
            id = MEDIA_TILE_ID,
            itemType = GridItemType.SMART_TILE,
            smartTileType = SmartTileType.MEDIA_PLAYER,
            width = 8,
            height = 2,
            x = 0,
            y = insertionY
        )

        val topItems = baseGrid.filter { it.y < insertionY }
        val shiftedBottomItems = baseGrid.filter { it.y >= insertionY }.map { it.copy(y = it.y + 2) }

        return topItems + mediaTile + shiftedBottomItems
    }
}
