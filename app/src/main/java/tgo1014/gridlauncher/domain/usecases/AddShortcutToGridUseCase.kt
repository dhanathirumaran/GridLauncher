package tgo1014.gridlauncher.domain.usecases

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CancellationException
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import javax.inject.Inject

class AddShortcutToGridUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    /**
     * Adds a pinned deep shortcut or legacy shortcut to the launcher grid.
     *
     * GRID POSITIONING INTEGRITY:
     * Previously, setting `x = 0, y = 0` and calling `GridPacker.compact()` caused the new shortcut
     * to sort before existing tiles in row 0. In a full grid, the shortcut became the 2nd tile
     * (placed at 2, 0) and pushed every existing tile down.
     *
     * We now scan for the first available 2x2 gap in the grid row-by-row. If an open slot is found,
     * the shortcut occupies it without moving other tiles. If the grid is full, it is appended
     * at the bottom (`x = 0, y = maxY + maxH`), preserving existing tile positions completely.
     *
     * ATOMICITY & CANCELLATION:
     * Uses `appsManager.updateGrid` to prevent lost update race conditions against concurrent UI drags,
     * and explicitly rethrows [CancellationException] to preserve coroutine lifecycle cancellation.
     */
    suspend operator fun invoke(
        shortcutId: String,
        packageName: String,
        label: String,
        iconPath: String?,
        intentUri: String?
    ) = runCatching {
        appsManager.updateGrid { rawCurrentGrid ->
            val currentGrid = rawCurrentGrid.toMutableList()
            var maxId = currentGrid.maxOfOrNull { it.id } ?: -1

            val rectGrid = currentGrid.map {
                Rect(
                    offset = Offset(x = it.x.toFloat(), y = it.y.toFloat()),
                    size = Size(width = it.width.toFloat(), height = it.height.toFloat())
                )
            }
            var targetX = 0
            var targetY = if (currentGrid.isEmpty()) 0 else currentGrid.maxOf { it.y + it.height }
            val maxY = currentGrid.maxOfOrNull { it.y } ?: 0

            // Search row-by-row for the first available 2x2 hole
            run repeatBlock@{
                repeat(maxY + 1) { y ->
                    for (x in 0..(Constants.gridColumns - 2) step 2) {
                        val candidate = Rect(
                            offset = Offset(x = x.toFloat(), y = y.toFloat()),
                            size = Size(2f, 2f)
                        )
                        if (rectGrid.none { it.overlaps(candidate) }) {
                            targetX = x
                            targetY = y
                            return@repeatBlock
                        }
                    }
                }
            }

            val gridItem = GridItem(
                id = ++maxId,
                itemType = GridItemType.SHORTCUT,
                shortcutId = shortcutId,
                shortcutPackage = packageName,
                shortcutLabel = label,
                shortcutIconPath = iconPath,
                shortcutIntentUri = intentUri,
                width = 2,
                height = 2,
                x = targetX,
                y = targetY
            )
            currentGrid.add(gridItem)
            GridPacker.compact(currentGrid)
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        android.util.Log.e("AddShortcutToGridUseCase", "Failed to add shortcut to grid", e)
    }
}
