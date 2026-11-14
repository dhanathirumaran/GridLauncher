package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class ResolveGridCollisionUseCase @Inject constructor(
    private val appsManager: AppsManager
) {
    suspend operator fun invoke(
        targetItemId: Int,
        newX: Int,
        newY: Int,
        newWidth: Int? = null,
        newHeight: Int? = null,
    ) = runCatching {
        val currentGrid = appsManager.homeGridFlow.first()
        val item = currentGrid.firstOrNull { it.id == targetItemId } ?: return@runCatching

        val updatedWidth = (newWidth ?: item.width).coerceIn(1, Constants.gridColumns)
        val updatedHeight = (newHeight ?: item.height).coerceIn(1, 8)
        val updatedX = newX.coerceIn(0, Constants.gridColumns - updatedWidth)
        val updatedY = newY.coerceAtLeast(0)

        val updatedItem = item.copy(
            x = updatedX,
            y = updatedY,
            width = updatedWidth,
            height = updatedHeight
        )

        val otherItems = currentGrid.filterNot { it.id == targetItemId }
        val resolvedOthers = pushDownCollisions(updatedItem, otherItems)

        val newGrid = (resolvedOthers + updatedItem).sortedWith(
            compareBy({ it.y }, { it.x })
        )
        appsManager.setGrid(newGrid)
    }

    companion object {
        fun pushDownCollisions(
            fixedItem: GridItem,
            otherItems: List<GridItem>
        ): List<GridItem> {
            val list = otherItems.map { it.copy() }.toMutableList()

            // Pass 1: Resolve collisions directly against the fixed moved/resized item
            for (i in list.indices) {
                if (overlaps(list[i], fixedItem)) {
                    list[i] = list[i].copy(y = fixedItem.y + fixedItem.height)
                }
            }

            // Pass 2: Iteratively cascade push-downs among other items until no overlaps remain
            var changed = true
            var safetyCounter = 0
            while (changed && safetyCounter < 100) {
                changed = false
                safetyCounter++

                // Ensure nothing overlaps fixedItem
                for (i in list.indices) {
                    if (overlaps(list[i], fixedItem)) {
                        list[i] = list[i].copy(y = fixedItem.y + fixedItem.height)
                        changed = true
                    }
                }

                // Check pairwise collisions between items in list
                for (i in list.indices) {
                    for (j in list.indices) {
                        if (i == j) continue
                        val itemA = list[i]
                        val itemB = list[j]
                        if (overlaps(itemA, itemB)) {
                            // If itemA is above or at same y, push itemB below itemA
                            if (itemA.y <= itemB.y) {
                                val targetY = itemA.y + itemA.height
                                if (itemB.y < targetY) {
                                    list[j] = itemB.copy(y = targetY)
                                    changed = true
                                }
                            } else {
                                val targetY = itemB.y + itemB.height
                                if (itemA.y < targetY) {
                                    list[i] = itemA.copy(y = targetY)
                                    changed = true
                                }
                            }
                        }
                    }
                }
            }
            return list
        }

        fun overlaps(a: GridItem, b: GridItem): Boolean {
            return a.x < b.x + b.width &&
                    b.x < a.x + a.width &&
                    a.y < b.y + b.height &&
                    b.y < a.y + a.height
        }
    }
}
