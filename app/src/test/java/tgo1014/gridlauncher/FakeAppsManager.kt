package tgo1014.gridlauncher

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem

class FakeAppsManager : AppsManager {

    override val installedAppsFlow: Flow<List<App>> = flow {}

    private val _homeGridFlow: MutableStateFlow<List<GridItem>> = MutableStateFlow(emptyList())
    override val homeGridFlow: Flow<List<GridItem>> = _homeGridFlow.asStateFlow()

    override fun openApp(app: App) {

    }

    override fun openShortcut(gridItem: GridItem) {

    }

    override suspend fun updateAppsList() {
    }

    override fun uninstallApp(app: App) {
    }

    override suspend fun setGrid(grid: List<GridItem>) {
        _homeGridFlow.update { grid }
    }

    override suspend fun updateGrid(transform: (List<GridItem>) -> List<GridItem>): List<GridItem> {
        val updated = transform(_homeGridFlow.value)
        _homeGridFlow.value = updated
        return updated
    }

    override suspend fun updateGridAndSmartTiles(
        transform: (List<GridItem>, Boolean) -> Pair<List<GridItem>, Boolean>
    ): List<GridItem> {
        val (updated, _) = transform(_homeGridFlow.value, false)
        _homeGridFlow.value = updated
        return updated
    }
}