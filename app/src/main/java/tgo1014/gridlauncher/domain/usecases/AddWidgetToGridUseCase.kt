package tgo1014.gridlauncher.domain.usecases

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import javax.inject.Inject

class AddWidgetToGridUseCase @Inject constructor(
    private val appsManager: AppsManager
) {
    suspend operator fun invoke(
        appWidgetId: Int,
        packageName: String,
        className: String,
        width: Int = 4,
        height: Int = 2
    ) = runCatching {
        val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()
        val clampedWidth = width.coerceIn(1, Constants.gridColumns)
        val clampedHeight = height.coerceAtLeast(1)

        if (currentGrid.isEmpty()) {
            addWidgetItem(currentGrid, appWidgetId, packageName, className, clampedWidth, clampedHeight, 0, 0)
            return@runCatching
        }

        val rectGrid = currentGrid.map {
            Rect(
                offset = Offset(x = it.x.toFloat(), y = it.y.toFloat()),
                size = Size(width = it.width.toFloat(), height = it.height.toFloat())
            )
        }

        var finalPosition: Rect? = null
        run repeatBlock@{
            repeat(currentGrid.maxOf { it.y } + 1) { y ->
                repeat(Constants.gridColumns - clampedWidth + 1) { x ->
                    val newPosition = Rect(
                        offset = Offset(x = x.toFloat(), y = y.toFloat()),
                        size = Size(clampedWidth.toFloat(), clampedHeight.toFloat())
                    )
                    if (rectGrid.none { it.overlaps(newPosition) }) {
                        finalPosition = newPosition
                        return@repeatBlock
                    }
                }
            }
        }

        if (finalPosition == null) {
            addWidgetItem(
                currentGrid = currentGrid,
                appWidgetId = appWidgetId,
                packageName = packageName,
                className = className,
                width = clampedWidth,
                height = clampedHeight,
                x = 0,
                y = currentGrid.maxOf { it.y + it.height },
            )
        } else {
            addWidgetItem(
                currentGrid = currentGrid,
                appWidgetId = appWidgetId,
                packageName = packageName,
                className = className,
                width = clampedWidth,
                height = clampedHeight,
                x = finalPosition!!.left.toInt(),
                y = finalPosition!!.top.toInt(),
            )
        }
    }

    private suspend fun addWidgetItem(
        currentGrid: List<GridItem>,
        appWidgetId: Int,
        packageName: String,
        className: String,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ) {
        val newList = currentGrid.toMutableList()
        val maxId = currentGrid.maxOfOrNull { it.id } ?: -1
        val gridItem = GridItem(
            id = maxId + 1,
            width = width,
            height = height,
            x = x,
            y = y,
            itemType = GridItemType.WIDGET,
            appWidgetId = appWidgetId,
            widgetPackage = packageName,
            widgetProvider = className
        )
        newList.add(gridItem)
        appsManager.setGrid(newList)
    }
}
