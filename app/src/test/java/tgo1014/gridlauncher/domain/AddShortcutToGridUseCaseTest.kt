package tgo1014.gridlauncher.domain

import org.junit.Before
import org.junit.Test
import tgo1014.gridlauncher.FakeAppsManager
import tgo1014.gridlauncher.domain.usecases.AddShortcutToGridUseCase
import tgo1014.gridlauncher.runTurbineTest
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType

class AddShortcutToGridUseCaseTest {

    private lateinit var appsManager: FakeAppsManager
    private lateinit var useCase: AddShortcutToGridUseCase

    @Before
    fun init() {
        appsManager = FakeAppsManager()
        useCase = AddShortcutToGridUseCase(appsManager)
    }

    @Test
    fun insertShortcutWhenGridIsEmpty() = runTurbineTest {
        val gridFlow = appsManager.homeGridFlow.testIn(it.backgroundScope)
        assert(gridFlow.awaitItem().isEmpty())
        useCase(
            shortcutId = "shortcut_1",
            packageName = "com.android.chrome",
            label = "Google",
            iconPath = "/cache/google.png",
            intentUri = "https://google.com"
        )
        val items = gridFlow.awaitItem()
        assert(items.size == 1)
        val item = items.first()
        assert(item.itemType == GridItemType.SHORTCUT)
        assert(item.shortcutLabel == "Google")
        assert(item.width == 2 && item.height == 2)
        assert(item.x == 0 && item.y == 0)
    }

    @Test
    fun insertShortcutFillsFirstAvailableSlot() = runTurbineTest {
        val gridFlow = appsManager.homeGridFlow.testIn(it.backgroundScope)
        val startGrid = listOf(
            GridItem(id = 1, width = 2, height = 2, x = 0, y = 0),
            GridItem(id = 2, width = 2, height = 2, x = 4, y = 0),
            GridItem(id = 3, width = 2, height = 2, x = 6, y = 0),
        )
        appsManager.setGrid(startGrid)
        assert(gridFlow.awaitItem().isEmpty())
        useCase(
            shortcutId = "shortcut_2",
            packageName = "com.whatsapp",
            label = "Chat",
            iconPath = null,
            intentUri = null
        )
        val newGrid = gridFlow.expectMostRecentItem()
        val shortcut = newGrid.find { it.shortcutId == "shortcut_2" }
        assert(shortcut != null)
        assert(shortcut?.x == 2 && shortcut?.y == 0)
    }
}
