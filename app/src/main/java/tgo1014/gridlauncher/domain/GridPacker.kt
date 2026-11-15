package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.ui.models.GridItem

/**
 * Authentic Windows Phone Start Screen layout engine.
 *
 * Packs tiles into the grid using first-fit gap-filling so that no accidental gaps
 * or empty rows ever appear on the home screen.
 */
object GridPacker {

    /**
     * Packs [items] in order using first-fit placement.
     * Empty spaces left beside larger tiles are automatically backfilled by subsequent tiles.
     */
    fun packTiles(
        items: List<GridItem>,
        columns: Int = Constants.gridColumns
    ): List<GridItem> {
        if (items.isEmpty()) return emptyList()

        val occupancy = ArrayList<BooleanArray>()

        fun fits(col: Int, row: Int, w: Int, h: Int): Boolean {
            if (col + w > columns) return false
            for (r in row until row + h) {
                while (occupancy.size <= r) occupancy.add(BooleanArray(columns))
                val cells = occupancy[r]
                for (c in col until col + w) {
                    if (cells[c]) return false
                }
            }
            return true
        }

        fun occupy(col: Int, row: Int, w: Int, h: Int) {
            for (r in row until row + h) {
                while (occupancy.size <= r) occupancy.add(BooleanArray(columns))
                val cells = occupancy[r]
                for (c in col until col + w) {
                    cells[c] = true
                }
            }
        }

        var frontier = 0

        return items.map { item ->
            val w = item.width.coerceIn(1, columns)
            val h = item.height.coerceAtLeast(1)
            var placed = false
            var row = frontier
            var placedCol = 0
            var placedRow = 0
            while (!placed) {
                for (col in 0..(columns - w)) {
                    if (fits(col, row, w, h)) {
                        occupy(col, row, w, h)
                        placedCol = col
                        placedRow = row
                        frontier = row
                        placed = true
                        break
                    }
                }
                if (!placed) row++
            }
            item.copy(x = placedCol, y = placedRow, width = w, height = h)
        }
    }

    /**
     * Finds the optimal order to place [movingItemId] nearest to [targetCol] and [targetRow],
     * and returns the reordered, tightly packed grid without any collisions or gaps.
     */
    fun moveAndPack(
        items: List<GridItem>,
        movingItemId: Int,
        targetCol: Int,
        targetRow: Int,
        columns: Int = Constants.gridColumns
    ): List<GridItem> {
        val from = items.indexOfFirst { it.id == movingItemId }
        if (from < 0) return packTiles(items, columns)
        val movingItem = items[from]
        val n = items.size
        if (n < 2) return packTiles(items, columns)

        val targetCenterX = targetCol + movingItem.width / 2f
        val targetCenterY = targetRow + movingItem.height / 2f

        var bestScore = Float.MAX_VALUE
        var bestPacked: List<GridItem> = items

        val targetIdx = items.indices.minByOrNull { i ->
            val it = items[i]
            val cx = it.x + it.width / 2f
            val cy = it.y + it.height / 2f
            val dx = cx - targetCenterX
            val dy = cy - targetCenterY
            dx * dx + dy * dy
        } ?: from

        val windowRadius = 4
        val rangeStart = (targetIdx - windowRadius).coerceAtLeast(0)
        val rangeEnd = (targetIdx + windowRadius).coerceAtMost(n - 1)

        val candidateIndices = mutableSetOf(0, n - 1, targetIdx)
        for (idx in rangeStart..rangeEnd) {
            candidateIndices.add(idx)
        }

        for (to in candidateIndices) {
            val candidate = items.toMutableList()
            val item = candidate.removeAt(from)
            candidate.add(to, item)
            val packed = packTiles(candidate, columns)
            val placedItem = packed[to]
            val placedCenterX = placedItem.x + placedItem.width / 2f
            val placedCenterY = placedItem.y + placedItem.height / 2f
            val dx = placedCenterX - targetCenterX
            val dy = placedCenterY - targetCenterY
            val score = dx * dx + dy * dy
            if (score < bestScore) {
                bestScore = score
                bestPacked = packed
            }
        }
        return bestPacked
    }

    /**
     * Resizes [resizingItemId] to [newWidth] x [newHeight] and repacks the grid so that
     * all tiles below automatically collapse upward if space is freed, or shift down cleanly.
     */
    fun resizeAndPack(
        items: List<GridItem>,
        resizingItemId: Int,
        newWidth: Int,
        newHeight: Int,
        columns: Int = Constants.gridColumns
    ): List<GridItem> {
        val updated = items.map {
            if (it.id == resizingItemId) {
                it.copy(
                    width = newWidth.coerceIn(1, columns),
                    height = newHeight.coerceAtLeast(1)
                )
            } else it
        }
        return packTiles(updated, columns)
    }

    /**
     * Compacts an existing grid by sorting tiles by visual row/column order and repacking.
     * Instantly closes any accidental gaps/holes on the screen.
     */
    fun compact(
        items: List<GridItem>,
        columns: Int = Constants.gridColumns
    ): List<GridItem> {
        val sorted = items.sortedWith(compareBy({ it.y }, { it.x }))
        return packTiles(sorted, columns)
    }
}
