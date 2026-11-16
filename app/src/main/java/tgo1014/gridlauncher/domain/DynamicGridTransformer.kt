package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SmartTileType

/**
 * Transforms the user's persisted base grid into a dynamic effective grid for UI presentation.
 *
 * ARCHITECTURAL DESIGN NOTE:
 * - The Media Tile is EPHEMERAL (id = -999). It is generated exclusively in memory while media
 *   is actively playing and is NEVER persisted to the database.
 * - When media playback begins, all tiles below the insertion seam are visually shifted down
 *   by +2 rows.
 * - When users drag, resize, or delete tiles while media is playing, the coordinates must be
 *   translated back from visual space to base grid space via [toBaseGridY] before calling
 *   domain use cases. Without this translation, database coordinates would be permanently shifted
 *   by 2 rows, causing layout corruption once media playback terminates.
 */
object DynamicGridTransformer {

    /**
     * Sentinel ID for the ephemeral dynamic media player tile.
     * All touch handlers, resize gestures, and edit-mode selections must ignore this ID.
     */
    const val MEDIA_TILE_ID = -999

    /**
     * Finds a clean horizontal insertion line where no existing tile straddles the seam.
     *
     * If a multi-row tile starts at row 1 and has height 2 (occupying rows 1 and 2), inserting
     * at row 2 would bisect and overlap that tile. This loop scans downward until a completely
     * clear seam boundary is found.
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
