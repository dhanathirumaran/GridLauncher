package tgo1014.gridlauncher.domain

import org.junit.Before
import org.junit.Test
import tgo1014.gridlauncher.FakeAppsManager
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.usecases.ResolveGridCollisionUseCase
import tgo1014.gridlauncher.runTurbineTest
import tgo1014.gridlauncher.ui.models.GridItem

class ResolveGridCollisionUseCaseTest {

    lateinit var appsManager: FakeAppsManager
    lateinit var useCase: ResolveGridCollisionUseCase

    @Before
    fun init() {
        appsManager = FakeAppsManager()
        useCase = ResolveGridCollisionUseCase(appsManager)
    }

    @Test
    fun pushDownConflictingTileOnMove() = runTurbineTest {
        val tile1 = GridItem(id = 1, app = App("A"), width = 2, height = 2, x = 0, y = 0)
        val tile2 = GridItem(id = 2, app = App("B"), width = 2, height = 2, x = 2, y = 0)
        appsManager.setGrid(listOf(tile1, tile2))

        // Move tile2 to (0, 0), directly conflicting with tile1
        useCase(targetItemId = 2, newX = 0, newY = 0)

        val updated = appsManager.homeGridFlow.value
        val movedTile2 = updated.first { it.id == 2 }
        val pushedTile1 = updated.first { it.id == 1 }

        assert(movedTile2.x == 0 && movedTile2.y == 0)
        assert(pushedTile1.x == 0 && pushedTile1.y >= 2)
    }

    @Test
    fun pushDownOnResizeExpanding() = runTurbineTest {
        val tile1 = GridItem(id = 1, app = App("A"), width = 2, height = 2, x = 0, y = 0)
        val tile2 = GridItem(id = 2, app = App("B"), width = 2, height = 2, x = 0, y = 2)
        appsManager.setGrid(listOf(tile1, tile2))

        // Resize tile1 from 2x2 to 2x4, expanding into tile2's row y=2
        useCase(targetItemId = 1, newX = 0, newY = 0, newWidth = 2, newHeight = 4)

        val updated = appsManager.homeGridFlow.value
        val resizedTile1 = updated.first { it.id == 1 }
        val pushedTile2 = updated.first { it.id == 2 }

        assert(resizedTile1.height == 4)
        assert(pushedTile2.y >= 4)
    }
}
