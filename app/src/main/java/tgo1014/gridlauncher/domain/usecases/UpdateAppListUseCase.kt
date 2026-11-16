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
import java.io.File
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
            // Purge legacy manual wallpaper files to reclaim disk space and guarantee native system wallpaper passthrough
            runCatching {
                File(context.filesDir, "wallpaper.png").delete()
                File(context.filesDir, "wallpaperDark.png").delete()
                File(context.filesDir, "wallpaperBlurred.png").delete()
                File(context.filesDir, "wallpaperBlurredDark.png").delete()
                File(context.cacheDir, "wallpaper.png").delete()
                File(context.cacheDir, "wallpaperDark.png").delete()
            }

            appsManager.updateAppsList()
            val installedApps = appsManager.installedAppsFlow.firstOrNull().orEmpty()
            // Guard: If installedApps is empty, DO NOT wipe the grid!
            if (installedApps.isEmpty()) return@runCatching

            val installedPackageNames = installedApps.map { it.packageName }.toSet()
            val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty()

            val prefs = context.getSharedPreferences("SMART_TILES_PREFS", Context.MODE_PRIVATE)
            val wasInitialized = prefs.getBoolean("SMART_TILES_INITIALIZED", false)

            // 1. Remove apps that were uninstalled or disabled from device and retain shortcuts & smart tiles
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
                            // Check if genuinely uninstalled/disabled or merely omitted in a transient query
                            val isStillLaunchable = runCatching {
                                val info = context.packageManager.getApplicationInfo(pkg, 0)
                                info.enabled && context.packageManager.getLaunchIntentForPackage(pkg) != null
                            }.getOrDefault(false)

                            if (isStillLaunchable) {
                                item // Keep tile safely
                            } else {
                                null // Confirmed uninstalled or disabled
                            }
                        } else {
                            null // Duplicate tile
                        }
                    }
                    GridItemType.SHORTCUT, GridItemType.WIDGET -> item
                    GridItemType.SMART_TILE -> {
                        // Purge deprecated LOCK_SCREEN smart tile from existing user grids
                        // to ensure banking / UPI apps (e.g. BHIM) encounter zero accessibility footprints.
                        @Suppress("DEPRECATION")
                        if (item.smartTileType == SmartTileType.LOCK_SCREEN) {
                            null
                        } else {
                            item
                        }
                    }
                }
            }
            val compactedGrid = GridPacker.compact(filteredGrid)
            if (compactedGrid != currentGrid) {
                appsManager.setGrid(compactedGrid)
            }

            // 2. Pre-pin smart tiles on first run or if entire grid was lost/empty.
            // NOTE: We check `!wasInitialized || currentGrid.isEmpty()`.
            // Do NOT check `!hasSmartTiles` here: if a user intentionally removes every smart tile,
            // checking `!hasSmartTiles` would re-pin them all on the next onResume.
            val needsInit = !wasInitialized || currentGrid.isEmpty()

            if (needsInit) {
                val allSmartTypes = listOf(
                    SmartTileType.FINDER,
                    SmartTileType.FLASHLIGHT,
                    SmartTileType.STEP_COUNTER,
                    SmartTileType.BATTERY,
                    SmartTileType.CURRENCY,
                    SmartTileType.WEATHER,
                    SmartTileType.CALENDAR
                )
                val latestGrid = appsManager.homeGridFlow.firstOrNull().orEmpty().toMutableList()
                var maxId = latestGrid.maxOfOrNull { it.id } ?: -1
                for (smartType in allSmartTypes) {
                    val item = GridItem(
                        id = ++maxId,
                        itemType = GridItemType.SMART_TILE,
                        smartTileType = smartType,
                        width = if (smartType == SmartTileType.CALENDAR) 4 else 2,
                        height = 2,
                        x = 0,
                        y = 0
                    )
                    latestGrid.add(item)
                }
                val compactedWithSmartTiles = GridPacker.compact(latestGrid)
                appsManager.setGrid(compactedWithSmartTiles)

                // Only commit preference flags after setGrid succeeds
                prefs.edit()
                    .putBoolean("SMART_TILES_INITIALIZED", true)
                    .putBoolean("CALENDAR_TILE_INITIALIZED", true)
                    .apply()
            } else if (!prefs.getBoolean("CALENDAR_TILE_INITIALIZED", false)) {
                val latestGrid = appsManager.homeGridFlow.firstOrNull().orEmpty().toMutableList()
                val existingSmartTypes = latestGrid.filter { it.itemType == GridItemType.SMART_TILE }.mapNotNull { it.smartTileType }.toSet()
                if (SmartTileType.CALENDAR !in existingSmartTypes) {
                    var maxId = latestGrid.maxOfOrNull { it.id } ?: -1
                    val calItem = GridItem(
                        id = ++maxId,
                        itemType = GridItemType.SMART_TILE,
                        smartTileType = SmartTileType.CALENDAR,
                        width = 4,
                        height = 2,
                        x = 0,
                        y = 0
                    )
                    latestGrid.add(calItem)
                    val compacted = GridPacker.compact(latestGrid)
                    appsManager.setGrid(compacted)
                }
                prefs.edit().putBoolean("CALENDAR_TILE_INITIALIZED", true).apply()
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