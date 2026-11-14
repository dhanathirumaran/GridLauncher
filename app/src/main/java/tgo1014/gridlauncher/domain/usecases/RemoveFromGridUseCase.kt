package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.data.widget.LauncherAppWidgetHost
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import javax.inject.Inject

class RemoveFromGridUseCase @Inject constructor(
    private val appsManager: AppsManager,
    private val appWidgetHost: LauncherAppWidgetHost,
) {
    suspend operator fun invoke(gridItem: GridItem) = runCatching {
        val newGrid = appsManager.homeGridFlow
            .firstOrNull()
            .orEmpty()
            .toMutableList()
        newGrid.removeAll { it.id == gridItem.id }
        appsManager.setGrid(newGrid)
        if (gridItem.itemType == GridItemType.WIDGET && gridItem.appWidgetId != -1) {
            runCatching {
                appWidgetHost.deleteAppWidgetId(gridItem.appWidgetId)
            }
        }
    }
}