package tgo1014.gridlauncher.domain

import org.junit.Before
import org.junit.Test
import tgo1014.gridlauncher.FakeAppsManager
import tgo1014.gridlauncher.domain.usecases.AddWidgetToGridUseCase
import tgo1014.gridlauncher.runTurbineTest
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType

class AddWidgetToGridUseCaseTest {

    lateinit var appsManager: FakeAppsManager
    lateinit var useCase: AddWidgetToGridUseCase

    @Before
    fun init() {
        appsManager = FakeAppsManager()
        useCase = AddWidgetToGridUseCase(appsManager)
    }

    @Test
    fun insertWidgetWhenGridIsEmpty() = runTurbineTest {
        val gridFlow = appsManager.homeGridFlow.testIn(it.backgroundScope)
        assert(gridFlow.awaitItem().isEmpty())
        useCase(
            appWidgetId = 42,
            packageName = "com.spotify.music",
            className = "com.spotify.music.WidgetProvider",
            width = 4,
            height = 2
        )
        val items = gridFlow.awaitItem()
        assert(items.isNotEmpty())
        val widgetItem = items.first()
        assert(widgetItem.itemType == GridItemType.WIDGET)
        assert(widgetItem.appWidgetId == 42)
        assert(widgetItem.width == 4)
        assert(widgetItem.height == 2)
        assert(widgetItem.x == 0 && widgetItem.y == 0)
    }

    @Test
    fun findSlotForWideWidget() = runTurbineTest {
        val gridFlow = appsManager.homeGridFlow.testIn(it.backgroundScope)
        val startGrid = listOf(
            GridItem(id = 1, width = 2, height = 2, x = 0, y = 0),
            GridItem(id = 2, width = 2, height = 2, x = 2, y = 0),
            GridItem(id = 3, width = 2, height = 2, x = 4, y = 0),
        )
        appsManager.setGrid(startGrid)
        assert(gridFlow.awaitItem().isEmpty())
        useCase(
            appWidgetId = 99,
            packageName = "com.google.android.calendar",
            className = "com.google.android.calendar.WidgetProvider",
            width = 4,
            height = 2
        )
        val newGrid = gridFlow.expectMostRecentItem()
        val calendarWidget = newGrid.find { it.appWidgetId == 99 }
        assert(calendarWidget != null)
        assert(calendarWidget!!.y >= 2)
    }
}
