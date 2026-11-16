package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.CancellationException
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class RemoveFromGridUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(gridItem: GridItem) = runCatching {
        appsManager.updateGrid { currentGrid ->
            val remaining = currentGrid.filterNot { it.id == gridItem.id }
            GridPacker.compact(remaining)
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        android.util.Log.e("RemoveFromGridUseCase", "Failed to remove item from grid", e)
    }
}