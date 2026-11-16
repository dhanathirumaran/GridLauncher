package tgo1014.gridlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem

class GridPackerTest {

    private fun item(id: Int, w: Int, h: Int, x: Int = 0, y: Int = 0) = GridItem(
        id = id,
        app = App(name = "App $id", packageName = "com.test.$id"),
        width = w,
        height = h,
        x = x,
        y = y
    )

    @Test
    fun `packTiles fills gaps and packs four 2x2 items in row 0 of an 8 column grid`() {
        val items = listOf(
            item(1, 2, 2),
            item(2, 2, 2),
            item(3, 2, 2),
            item(4, 2, 2),
        )
        val packed = GridPacker.packTiles(items, columns = 8)

        assertEquals(0, packed[0].x)
        assertEquals(0, packed[0].y)

        assertEquals(2, packed[1].x)
        assertEquals(0, packed[1].y)

        assertEquals(4, packed[2].x)
        assertEquals(0, packed[2].y)

        assertEquals(6, packed[3].x)
        assertEquals(0, packed[3].y)
    }

    @Test
    fun `compact removes empty gap between rows`() {
        // Items placed with an empty gap at row 2..5
        val items = listOf(
            item(1, 8, 2, x = 0, y = 0),
            item(2, 2, 2, x = 0, y = 8), // Gap of rows 2..7
            item(3, 2, 2, x = 2, y = 8),
        )
        val compacted = GridPacker.compact(items, columns = 8)

        // Item 1 is at y=0, height=2 (occupies rows 0..1)
        assertEquals(0, compacted[0].y)

        // Item 2 and 3 should have pulled up to row 2!
        assertEquals(2, compacted[1].y)
        assertEquals(0, compacted[1].x)

        assertEquals(2, compacted[2].y)
        assertEquals(2, compacted[2].x)
    }

    @Test
    fun `moveAndPack places tile nearest to target and repacks remaining items`() {
        val items = listOf(
            item(1, 2, 2, x = 0, y = 0),
            item(2, 2, 2, x = 2, y = 0),
            item(3, 2, 2, x = 4, y = 0),
            item(4, 2, 2, x = 6, y = 0),
        )
        // Move item 1 towards column 6
        val moved = GridPacker.moveAndPack(items, movingItemId = 1, targetCol = 6, targetRow = 0, columns = 8)

        val item1 = moved.first { it.id == 1 }
        assertEquals(6, item1.x)
        assertEquals(0, item1.y)
    }

    @Test
    fun `smart tiles pack alongside app tiles cleanly`() {
        val appTile = item(1, 2, 2, x = 0, y = 0)
        val smartTile = GridItem(
            id = 2,
            itemType = tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE,
            smartTileType = tgo1014.gridlauncher.ui.models.SmartTileType.FLASHLIGHT,
            width = 2,
            height = 2
        )
        val packed = GridPacker.packTiles(listOf(appTile, smartTile), columns = 8)
        assertEquals(2, packed[1].x)
        assertEquals(0, packed[1].y)
    }

    @Test
    fun `gap beside wide tile is properly backfilled by subsequent smaller tile`() {
        val tileA = item(1, 4, 2) // fits col 0..3, row 0..1 (leaving col 4..7 empty in row 0)
        val tileB = item(2, 6, 2) // cannot fit row 0 (needs 6 cols, only 4 free), so placed at row 2
        val tileC = item(3, 4, 2) // fits in row 0 at col 4!

        val packed = GridPacker.packTiles(listOf(tileA, tileB, tileC), columns = 8)

        val packedA = packed.first { it.id == 1 }
        val packedB = packed.first { it.id == 2 }
        val packedC = packed.first { it.id == 3 }

        assertEquals(0, packedA.x)
        assertEquals(0, packedA.y)

        assertEquals(0, packedB.x)
        assertEquals(2, packedB.y)

        // tileC must backfill row 0 at col 4
        assertEquals(4, packedC.x)
        assertEquals(0, packedC.y)
    }
}
