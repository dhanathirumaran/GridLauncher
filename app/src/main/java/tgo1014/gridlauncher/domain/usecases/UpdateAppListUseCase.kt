package tgo1014.gridlauncher.domain.usecases

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SmartTileType
import javax.inject.Inject

class UpdateAppListUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appsManager: AppsManager,
    private val addToGridUseCase: AddToGridUseCase,
) {

    suspend operator fun invoke() = runCatching {
        appsManager.updateAppsList()
        val installedApps = appsManager.installedAppsFlow.firstOrNull().orEmpty()
        val installedPackageNames = installedApps.map { it.packageName }.toSet()
        val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()

        // 1. Remove apps that were uninstalled from device and retain shortcuts & smart tiles
        val filteredGrid = currentGrid.filter {
            (it.itemType == GridItemType.APP && it.app.packageName in installedPackageNames) ||
            it.itemType == GridItemType.SHORTCUT ||
            it.itemType == GridItemType.SMART_TILE
        }
        val compactedGrid = GridPacker.compact(filteredGrid)
        if (compactedGrid != currentGrid) {
            appsManager.setGrid(compactedGrid)
        }

        // 2. Pre-pin the smart tiles on first run
        val prefs = context.getSharedPreferences("SMART_TILES_PREFS", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("SMART_TILES_INITIALIZED", false)) {
            val allSmartTypes = listOf(
                SmartTileType.FINDER,
                SmartTileType.FLASHLIGHT,
                SmartTileType.LOCK_SCREEN,
                SmartTileType.STEP_COUNTER,
                SmartTileType.BATTERY,
                SmartTileType.CURRENCY,
                SmartTileType.WEATHER
            )
            val latestGrid = appsManager.homeGridFlow.firstOrNull().orEmpty().toMutableList()
            var maxId = latestGrid.maxOfOrNull { it.id } ?: -1
            for (smartType in allSmartTypes) {
                val item = GridItem(
                    id = ++maxId,
                    itemType = GridItemType.SMART_TILE,
                    smartTileType = smartType,
                    width = 2,
                    height = 2,
                    x = 0,
                    y = 0
                )
                latestGrid.add(item)
            }
            prefs.edit().putBoolean("SMART_TILES_INITIALIZED", true).apply()
            val compactedWithSmartTiles = GridPacker.compact(latestGrid)
            appsManager.setGrid(compactedWithSmartTiles)
        }

        // 3. Automatically add all installed apps that are not yet on the grid
        val refreshedGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()
        val existingPackageNames = refreshedGrid
            .filter { it.itemType == GridItemType.APP }
            .map { it.app.packageName }
            .toSet()

        val appsToAdd = installedApps.filter { it.packageName !in existingPackageNames }
        if (appsToAdd.isNotEmpty()) {
            addToGridUseCase(appsToAdd)
        }
    }

}