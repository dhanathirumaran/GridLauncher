package tgo1014.gridlauncher.domain.usecases

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import tgo1014.gridlauncher.ui.models.SmartTileType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateAppListUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appsManager: AppsManager,
    private val addToGridUseCase: AddToGridUseCase,
) {

    companion object {
        private val gridUpdateMutex = Mutex()
    }

    suspend operator fun invoke() = gridUpdateMutex.withLock {
        runCatching {
            appsManager.updateAppsList()
            val installedApps = appsManager.installedAppsFlow.firstOrNull().orEmpty()
            // Guard: If installedApps is empty, DO NOT wipe the grid!
            if (installedApps.isEmpty()) return@runCatching

            val installedPackageNames = installedApps.map { it.packageName }.toSet()
            val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()

            // 1. Remove apps that were uninstalled from device and retain shortcuts & smart tiles
            val installedAppMap = installedApps.associateBy { it.packageName }
            val seenPackages = mutableSetOf<String>()
            val filteredGrid = currentGrid.mapNotNull { item ->
                when (item.itemType) {
                    GridItemType.APP -> {
                        val pkg = item.app.packageName
                        if (pkg == context.packageName) {
                            null // Never keep launcher itself
                        } else if (pkg in installedPackageNames && seenPackages.add(pkg)) {
                            val freshApp = installedAppMap[pkg] ?: item.app
                            item.copy(app = freshApp)
                        } else if (seenPackages.add(pkg)) {
                            // Check if genuinely uninstalled or merely omitted in a transient/delayed query
                            val isStillInstalled = runCatching {
                                context.packageManager.getPackageInfo(pkg, 0)
                            }.isSuccess
                            if (isStillInstalled) {
                                item // Keep tile safely
                            } else {
                                null // Confirmed uninstalled
                            }
                        } else {
                            null // Duplicate tile
                        }
                    }
                    GridItemType.SHORTCUT, GridItemType.SMART_TILE -> item
                }
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

            val appsToAdd = installedApps
                .filter { it.packageName != context.packageName && it.packageName !in existingPackageNames }
                .distinctBy { it.packageName }

            if (appsToAdd.isNotEmpty()) {
                addToGridUseCase(appsToAdd)
            }
        }
    }

}