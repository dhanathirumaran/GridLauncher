package tgo1014.gridlauncher.domain.usecases

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.data.calendar.CalendarEventManager
import tgo1014.gridlauncher.data.clock.ClockAppManager
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
) {

    companion object {
        private val gridUpdateMutex = Mutex()
    }

    /**
     * Reconciles the home grid with the currently installed applications and smart tiles.
     *
     * RESILIENCE & INTEGRITY GUARANTEES:
     * 1. CANCELLATION INTEGRITY:
     *    Kotlin's standard `runCatching` catches [CancellationException], which breaks structured
     *    concurrency when onResume jobs are cancelled mid-flight. We explicitly rethrow [CancellationException].
     *
     * 2. ATOMIC TRANSACTION & DEDUPLICATION (PREVENTS DUPLICATE SMART TILES):
     *    Previously, SMART_TILES_INITIALIZED lived in SharedPreferences (excluded from backups).
     *    Restores or mid-init job cancellations appended all 7 smart tiles repeatedly.
     *    Now, the grid transformation and initialization flag are committed together in a single
     *    atomic DataStore transaction (`updateGridAndSmartTiles`).
     *    Smart tiles are strictly deduped by [SmartTileType] so duplicate tiles can never exist.
     *
     * 3. RACE CONDITION RESILIENCE (NO LOST UPDATES):
     *    Mutations are serialized inside `dataStore.edit`, preventing drag-and-drop operations
     *    on the UI thread from being overwritten by stale snapshots during background scans.
     *
     * 4. MAIN-THREAD JANK PREVENTION:
     *    Pre-warms Calendar and Clock package caches on the background dispatcher so that
     *    subsequent Compose rendering does not perform synchronous Binder IPC calls to system_server.
     */
    suspend operator fun invoke() = gridUpdateMutex.withLock {
        runCatching {
            // Pre-warm package caches off the main UI thread
            CalendarEventManager.prewarmPackageCache(context)
            ClockAppManager.prewarmCache(context)

            appsManager.updateAppsList()
            val installedApps = appsManager.installedAppsFlow.firstOrNull().orEmpty()
            // Guard: If installedApps is empty, DO NOT wipe the grid!
            if (installedApps.isEmpty()) return@runCatching

            val installedPackageNames = installedApps.map { it.packageName }.toSet()
            val installedAppMap = installedApps.associateBy { it.packageName }

            appsManager.updateGridAndSmartTiles { currentGrid, wasInitialized ->
                // 1. Remove apps uninstalled/disabled from device, purge deprecated LOCK_SCREEN,
                //    and strictly deduplicate smart tiles by SmartTileType.
                val seenPackages = mutableSetOf<String>()
                val seenSmartTypes = mutableSetOf<SmartTileType>()

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
                                val isStillLaunchable = runCatching {
                                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                                    info.enabled && context.packageManager.getLaunchIntentForPackage(pkg) != null
                                }.getOrDefault(false)

                                if (isStillLaunchable) item else null
                            } else {
                                null // Duplicate app tile
                            }
                        }
                        GridItemType.SHORTCUT, GridItemType.WIDGET -> item
                        GridItemType.SMART_TILE -> {
                            val type = item.smartTileType
                            @Suppress("DEPRECATION")
                            if (type == null || type == SmartTileType.LOCK_SCREEN || !seenSmartTypes.add(type)) {
                                null // Purge null, deprecated, or duplicate smart tiles
                            } else {
                                item
                            }
                        }
                    }
                }

                // GRID STABILITY GUARD:
                // Only run GridPacker.compact() if items were genuinely removed to avoid
                // non-idempotent 2D bin packing shifts on already-packed grids.
                var workingGrid = if (filteredGrid.size < currentGrid.size) {
                    GridPacker.compact(filteredGrid).toMutableList()
                } else {
                    filteredGrid.toMutableList()
                }

                // 2. Pre-pin smart tiles on first run or if entire grid was empty/lost.
                val needsInit = !wasInitialized || currentGrid.isEmpty()
                var newInitialized = wasInitialized

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
                    val existingSmartTypes = workingGrid
                        .filter { it.itemType == GridItemType.SMART_TILE }
                        .mapNotNull { it.smartTileType }
                        .toSet()

                    var maxId = workingGrid.maxOfOrNull { it.id } ?: -1
                    for (smartType in allSmartTypes) {
                        if (smartType !in existingSmartTypes) {
                            val item = GridItem(
                                id = ++maxId,
                                itemType = GridItemType.SMART_TILE,
                                smartTileType = smartType,
                                width = if (smartType == SmartTileType.CALENDAR) 4 else 2,
                                height = 2,
                                x = 0,
                                y = 0
                            )
                            workingGrid.add(item)
                        }
                    }
                    workingGrid = GridPacker.compact(workingGrid).toMutableList()
                    newInitialized = true
                }

                // 3. Automatically add newly installed apps that are not yet on the grid
                val existingPackageNames = workingGrid
                    .filter { it.itemType == GridItemType.APP }
                    .map { it.app.packageName }
                    .toSet()

                val appsToAdd = installedApps
                    .filter { it.packageName != context.packageName && it.packageName !in existingPackageNames }
                    .distinctBy { it.packageName }

                if (appsToAdd.isNotEmpty()) {
                    var maxId = workingGrid.maxOfOrNull { it.id } ?: -1
                    for (app in appsToAdd) {
                        val rectGrid = workingGrid.map {
                            Rect(
                                offset = Offset(x = it.x.toFloat(), y = it.y.toFloat()),
                                size = Size(width = it.width.toFloat(), height = it.height.toFloat())
                            )
                        }
                        var finalPosition: Rect? = null
                        val maxY = workingGrid.maxOfOrNull { it.y } ?: 0
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
                        val targetY = finalPosition?.top?.toInt() ?: if (workingGrid.isEmpty()) 0 else workingGrid.maxOf { it.y + it.height }
                        val gridItem = GridItem(
                            id = ++maxId,
                            app = app,
                            width = 2,
                            height = 2,
                            x = targetX,
                            y = targetY
                        )
                        workingGrid.add(gridItem)
                    }
                    workingGrid = GridPacker.compact(workingGrid).toMutableList()
                }

                Pair(workingGrid, newInitialized)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            android.util.Log.e("UpdateAppListUseCase", "Failed to update app list", e)
        }
    }
}