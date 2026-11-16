package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.CancellationException
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import javax.inject.Inject

class ItemGridSizeChangeUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(itemId: Int, newWidth: Int, newHeight: Int) = runCatching {
        appsManager.updateGrid { currentGrid ->
            GridPacker.resizeAndPack(
                items = currentGrid,
                resizingItemId = itemId,
                newWidth = newWidth,
                newHeight = newHeight,
                columns = Constants.gridColumns
            )
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        android.util.Log.e("ItemGridSizeChangeUseCase", "Failed to change item size", e)
    }
}