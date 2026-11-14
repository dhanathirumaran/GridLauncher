package tgo1014.gridlauncher.domain.usecases

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class AddToGridUseCase @Inject constructor(
    private val appsManager: AppsManager
) {
    suspend operator fun invoke(app: App) = runCatching {
        invoke(listOf(app))
    }

    suspend operator fun invoke(apps: List<App>) = runCatching {
        if (apps.isEmpty()) return@runCatching
        val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty().toMutableList()
        var maxId = currentGrid.maxOfOrNull { it.id } ?: -1

        for (app in apps) {
            if (currentGrid.isEmpty()) {
                val gridItem = GridItem(
                    id = ++maxId,
                    app = app,
                    width = 2,
                    height = 2,
                    x = 0,
                    y = 0
                )
                currentGrid.add(gridItem)
                continue
            }
            val rectGrid = currentGrid.map {
                Rect(
                    offset = Offset(x = it.x.toFloat(), y = it.y.toFloat()),
                    size = Size(width = it.width.toFloat(), height = it.height.toFloat())
                )
            }
            var finalPosition: Rect? = null
            val maxY = currentGrid.maxOf { it.y }
            run repeatBlock@{
                repeat(maxY + 1) { y ->
                    repeat(Constants.gridColumns - 1) { x ->
                        val newPosition = Rect(
                            offset = Offset(x = x.toFloat(), y = y.toFloat()),
                            size = Size(2f, 2f)
                        )
                        if (rectGrid.none { it.overlaps(newPosition) }) {
                            finalPosition = newPosition
                            return@repeatBlock
                        }
                    }
                }
            }
            val targetX = finalPosition?.left?.toInt() ?: 0
            val targetY = finalPosition?.top?.toInt() ?: currentGrid.maxOf { it.y + it.height }
            val gridItem = GridItem(
                id = ++maxId,
                app = app,
                width = 2,
                height = 2,
                x = targetX,
                y = targetY
            )
            currentGrid.add(gridItem)
        }
        val compacted = GridPacker.compact(currentGrid)
        appsManager.setGrid(compacted)
    }
}