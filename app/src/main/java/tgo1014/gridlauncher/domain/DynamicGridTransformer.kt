package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SmartTileType

object DynamicGridTransformer {

    const val MEDIA_TILE_ID = -999

    /**
     * Finds a clean horizontal insertion line where no existing tile straddles the seam.
     * Prevents overlapping tiles that start above insertionY and extend below it.
     */
    fun findInsertionY(baseGrid: List<GridItem>): Int {
        if (baseGrid.isEmpty()) return 1
        var insertionY = 1
        val maxIterations = 50
        var iter = 0
        while (iter++ < maxIterations && baseGrid.any { it.y < insertionY && it.y + it.height > insertionY }) {
            insertionY++
        }
        return insertionY
    }

    /**
     * Translates a visual Y coordinate back to the base grid Y coordinate space
     * when the media tile is dynamically displacing lower rows.
     */
    fun toBaseGridY(targetY: Int, baseGrid: List<GridItem>, isMediaActive: Boolean): Int {
        if (!isMediaActive || baseGrid.isEmpty()) return targetY
        val insertionY = findInsertionY(baseGrid)
        return if (targetY >= insertionY + 2) {
            targetY - 2
        } else if (targetY >= insertionY) {
            insertionY
        } else {
            targetY
        }
    }

    /**
     * Dynamically inserts an 8x2 Live Music Player tile below the top row(s)
     * and shifts all subsequent tiles down by 2 rows in memory when media is active.
     * When media is inactive, returns the base grid unmodified.
     */
    fun computeEffectiveGrid(
        baseGrid: List<GridItem>,
        isMediaActive: Boolean
    ): List<GridItem> {
        if (!isMediaActive || baseGrid.isEmpty()) return baseGrid

        val insertionY = findInsertionY(baseGrid)

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
