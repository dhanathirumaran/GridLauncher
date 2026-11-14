package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.domain.models.Direction
import javax.inject.Inject

class MoveGridItemUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(itemId: Int, newX: Int, newY: Int) = runCatching {
        val currentGrid = appsManager.homeGridFlow.first()
        val packed = GridPacker.moveAndPack(
            items = currentGrid,
            movingItemId = itemId,
            targetCol = newX,
            targetRow = newY,
            columns = Constants.gridColumns
        )
        appsManager.setGrid(packed)
    }

    suspend operator fun invoke(itemId: Int, direction: Direction) = runCatching {
        val currentGrid = appsManager.homeGridFlow.first()
        val item = currentGrid.first { it.id == itemId }
        val targetX = when (direction) {
            Direction.Left -> (item.x - 1).coerceAtLeast(0)
            Direction.Right -> (item.x + 1).coerceAtMost(Constants.gridColumns - item.width)
            else -> item.x
        }
        val targetY = when (direction) {
            Direction.Up -> (item.y - 1).coerceAtLeast(0)
            Direction.Down -> item.y + 1
            else -> item.y
        }
        invoke(itemId, targetX, targetY).getOrThrow()
    }
}