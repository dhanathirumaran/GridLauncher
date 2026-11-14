package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.data.widget.LauncherAppWidgetHost
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import javax.inject.Inject

class RemoveFromGridUseCase @Inject constructor(
    private val appsManager: AppsManager,
    private val appWidgetHost: LauncherAppWidgetHost,
) {
    suspend operator fun invoke(gridItem: GridItem) = runCatching {
        val currentGrid = appsManager.homeGridFlow
            .firstOrNull()
            .orEmpty()
        val remaining = currentGrid.filterNot { it.id == gridItem.id }
        val compacted = GridPacker.compact(remaining)
        appsManager.setGrid(compacted)
        if (gridItem.itemType == GridItemType.WIDGET && gridItem.appWidgetId != -1) {
            runCatching {
                appWidgetHost.deleteAppWidgetId(gridItem.appWidgetId)
            }
        }
    }
}