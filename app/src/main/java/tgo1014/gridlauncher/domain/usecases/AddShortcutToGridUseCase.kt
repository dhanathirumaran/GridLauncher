package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.firstOrNull
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPacker
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import javax.inject.Inject

class AddShortcutToGridUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(
        shortcutId: String,
        packageName: String,
        label: String,
        iconPath: String?,
        intentUri: String?
    ) = runCatching {
        val currentGrid = appsManager.homeGridFlow.firstOrNull().orEmpty().toMutableList()
        var maxId = currentGrid.maxOfOrNull { it.id } ?: -1
        val gridItem = GridItem(
            id = ++maxId,
            itemType = GridItemType.SHORTCUT,
            shortcutId = shortcutId,
            shortcutPackage = packageName,
            shortcutLabel = label,
            shortcutIconPath = iconPath,
            shortcutIntentUri = intentUri,
            width = 2,
            height = 2,
            x = 0,
            y = 0
        )
        currentGrid.add(gridItem)
        val compacted = GridPacker.compact(currentGrid)
        appsManager.setGrid(compacted)
    }
}
