package tgo1014.gridlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SmartTileType

class DynamicGridTransformerTest {

    @Test
    fun computeEffectiveGrid_whenMediaInactive_returnsBaseGridUnmodified() {
        val baseGrid = listOf(
            GridItem(id = 1, app = App("App 1"), width = 4, height = 2, x = 0, y = 0),
            GridItem(id = 2, app = App("App 2"), width = 4, height = 2, x = 4, y = 0),
            GridItem(id = 3, app = App("App 3"), width = 2, height = 2, x = 0, y = 2)
        )

        val result = DynamicGridTransformer.computeEffectiveGrid(baseGrid, isMediaActive = false)
        assertEquals(baseGrid, result)
    }

    @Test
    fun computeEffectiveGrid_whenMediaActive_insertsMediaTileInRow2AndShiftsLowerTiles() {
        val baseGrid = listOf(
            GridItem(id = 1, app = App("App 1"), width = 4, height = 2, x = 0, y = 0),
            GridItem(id = 2, app = App("App 2"), width = 4, height = 2, x = 4, y = 0),
            GridItem(id = 3, app = App("App 3"), width = 2, height = 2, x = 0, y = 2),
            GridItem(id = 4, app = App("App 4"), width = 2, height = 2, x = 2, y = 2)
        )

        val result = DynamicGridTransformer.computeEffectiveGrid(baseGrid, isMediaActive = true)

        assertEquals(5, result.size)

        // Top row items unchanged
        assertEquals(0, result.first { it.id == 1 }.y)
        assertEquals(0, result.first { it.id == 2 }.y)

        // Media tile inserted at row 2 (y = 2, full width 8, height 2)
        val mediaTile = result.first { it.id == DynamicGridTransformer.MEDIA_TILE_ID }
        assertEquals(0, mediaTile.x)
        assertEquals(2, mediaTile.y)
        assertEquals(8, mediaTile.width)
        assertEquals(2, mediaTile.height)
        assertEquals(GridItemType.SMART_TILE, mediaTile.itemType)
        assertEquals(SmartTileType.MEDIA_PLAYER, mediaTile.smartTileType)

        // Lower row items shifted down by 2 rows (y: 2 -> 4)
        assertEquals(4, result.first { it.id == 3 }.y)
        assertEquals(4, result.first { it.id == 4 }.y)
    }

    @Test
    fun computeEffectiveGrid_whenRow1HasHeight1_insertsMediaTileAtY1() {
        val baseGrid = listOf(
            GridItem(id = 1, app = App("App 1"), width = 8, height = 1, x = 0, y = 0),
            GridItem(id = 2, app = App("App 2"), width = 4, height = 2, x = 0, y = 1)
        )

        val result = DynamicGridTransformer.computeEffectiveGrid(baseGrid, isMediaActive = true)

        // Top row unchanged
        assertEquals(0, result.first { it.id == 1 }.y)

        // Media tile inserted at y = 1
        val mediaTile = result.first { it.id == DynamicGridTransformer.MEDIA_TILE_ID }
        assertEquals(1, mediaTile.y)

        // Lower item shifted from y = 1 to y = 3
        assertEquals(3, result.first { it.id == 2 }.y)
    }

    @Test
    fun computeEffectiveGrid_whenBaseGridEmpty_returnsEmptyList() {
        val result = DynamicGridTransformer.computeEffectiveGrid(emptyList(), isMediaActive = true)
        assertTrue(result.isEmpty())
    }
}
