package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import javax.inject.Inject

class ItemGridSizeChangeUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(itemId: Int, newWidth: Int, newHeight: Int) = runCatching {
        val currentGrid = appsManager.homeGridFlow.first()
        val packed = GridPacker.resizeAndPack(
            items = currentGrid,
            resizingItemId = itemId,
            newWidth = newWidth,
            newHeight = newHeight,
            columns = Constants.gridColumns
        )
        appsManager.setGrid(packed)
    }
}