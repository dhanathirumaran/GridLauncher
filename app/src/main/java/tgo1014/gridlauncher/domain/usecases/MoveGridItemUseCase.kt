package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.domain.models.Direction
import javax.inject.Inject

class MoveGridItemUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(itemId: Int, newX: Int, newY: Int) = runCatching {
        appsManager.updateGrid { currentGrid ->
            GridPacker.moveAndPack(
                items = currentGrid,
                movingItemId = itemId,
                targetCol = newX,
                targetRow = newY,
                columns = Constants.gridColumns
            )
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        android.util.Log.e("MoveGridItemUseCase", "Failed to move grid item", e)
    }

    suspend operator fun invoke(itemId: Int, direction: Direction) = runCatching {
        appsManager.updateGrid { currentGrid ->
            val item = currentGrid.firstOrNull { it.id == itemId } ?: return@updateGrid currentGrid
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
            GridPacker.moveAndPack(
                items = currentGrid,
                movingItemId = itemId,
                targetCol = targetX,
                targetRow = targetY,
                columns = Constants.gridColumns
            )
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        android.util.Log.e("MoveGridItemUseCase", "Failed to move grid item direction", e)
    }
}