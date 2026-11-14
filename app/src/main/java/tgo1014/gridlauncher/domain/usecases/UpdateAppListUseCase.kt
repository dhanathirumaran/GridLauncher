package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.ui.models.GridItemType
import javax.inject.Inject

class UpdateAppListUseCase @Inject constructor(
    private val appsManager: AppsManager,
    private val addToGridUseCase: AddToGridUseCase,
) {

    suspend operator fun invoke() = runCatching {
        appsManager.updateAppsList()
        val installedApps = appsManager.installedAppsFlow.firstOrNull().orEmpty()
        val installedPackageNames = installedApps.map { it.packageName }.toSet()
        val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()

        // 1. Remove apps that were uninstalled from device and remove any legacy widgets
        val filteredGrid = currentGrid.filter {
            (it.itemType == GridItemType.APP && it.app.packageName in installedPackageNames) ||
            it.itemType == GridItemType.SHORTCUT
        }
        val compactedGrid = tgo1014.gridlauncher.domain.GridPacker.compact(filteredGrid)
        if (compactedGrid != currentGrid) {
            appsManager.setGrid(compactedGrid)
        }

        // 2. Automatically add all installed apps that are not yet on the grid
        val existingPackageNames = filteredGrid
            .filter { it.itemType == GridItemType.APP }
            .map { it.app.packageName }
            .toSet()

        val appsToAdd = installedApps.filter { it.packageName !in existingPackageNames }
        if (appsToAdd.isNotEmpty()) {
            addToGridUseCase(appsToAdd)
        }
    }

}