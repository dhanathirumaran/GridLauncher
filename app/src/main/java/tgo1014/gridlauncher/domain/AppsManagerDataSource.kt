package tgo1014.gridlauncher.domain

import kotlinx.coroutines.flow.Flow
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem

interface AppsManagerDataSource {
    var installedAppsList: Flow<List<App>>
    val homeGridFlow: Flow<List<GridItem>>
    suspend fun setAppList(appList: List<App>)
    suspend fun setGrid(grid: List<GridItem>)

    /**
     * Atomically transforms the grid under DataStore's transactional edit lock.
     * Prevents read-modify-write lost update race conditions between UI interactions
     * (such as tile dragging) and asynchronous background tasks (such as app scans on resume).
     */
    suspend fun updateGrid(transform: (currentGrid: List<GridItem>) -> List<GridItem>): List<GridItem>

    /**
     * Atomically transforms the grid and updates the smart tiles initialized flag in the SAME
     * DataStore transaction. Guarantees that the grid and its initialization state are committed
     * together, preventing duplicated smart tiles across cloud backups/restores and mid-init cancellations.
     */
    suspend fun updateGridAndSmartTiles(
        transform: (currentGrid: List<GridItem>, wasInitialized: Boolean) -> Pair<List<GridItem>, Boolean>
    ): List<GridItem>
}