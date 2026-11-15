package tgo1014.gridlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.data.AppsManagerDataSourceImpl
import tgo1014.gridlauncher.data.SettingsRepositoryImpl
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.usecases.wallpaper.StoreWallpaperPickedUseCase
import tgo1014.gridlauncher.ui.composables.getCalendarDateBadge
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.GridItemType
import java.util.Calendar

class RecoveryAndDeduplicationTest {

    @Test
    fun `recoverTileSettings extracts valid transparency fields from partial JSON`() {
        val partialJson = """
            {
                "weatherTransparency": 0.85,
                "batteryTransparency": 0.3,
                "stepCounterTransparency": "invalid_float",
                "unexpectedField": 123
            }
        """.trimIndent()

        val settings = SettingsRepositoryImpl.recoverTileSettings(partialJson)
        assertEquals(0.85f, settings.weatherTransparency, 0.001f)
        assertEquals(0.3f, settings.batteryTransparency, 0.001f)
        // Invalid or missing keys safely fall back to default 0.6f
        assertEquals(0.6f, settings.stepCounterTransparency, 0.001f)
        assertEquals(0.6f, settings.calendarTransparency, 0.001f)
    }

    @Test
    fun `recoverTileSettings returns default on completely unparseable input`() {
        val corrupted = "{ broken json !! 123 "
        val settings = SettingsRepositoryImpl.recoverTileSettings(corrupted)
        assertEquals(0.6f, settings.weatherTransparency, 0.001f)
        assertEquals(0.6f, settings.batteryTransparency, 0.001f)
    }

    @Test
    fun `recoverGridItems recovers intact items from JSON with corrupted elements`() {
        // One valid GridItem JSON and one corrupted item inside an array
        val validItem = """{"app":{"name":"TestApp","packageName":"com.test.app","isSystemApp":false},"itemType":"APP","colSpan":1,"rowSpan":1}"""
        val corruptedItem = """{"corrupted":true, broken}"""
        val arrayJson = "[$validItem, $corruptedItem]"

        val recovered = AppsManagerDataSourceImpl.recoverGridItems(arrayJson)
        assertEquals(1, recovered.size)
        assertEquals("com.test.app", recovered[0].app.packageName)
        assertEquals("TestApp", recovered[0].app.name)
    }

    @Test
    fun `recoverGridItems returns empty list for non-array string`() {
        val nonArray = "not a json array"
        val recovered = AppsManagerDataSourceImpl.recoverGridItems(nonArray)
        assertTrue(recovered.isEmpty())
    }

    @Test
    fun `calculateInSampleSize scales down large dimensions appropriately`() {
        // When image is smaller than required, sample size is 1
        assertEquals(1, StoreWallpaperPickedUseCase.calculateInSampleSize(800, 600, 1080, 1920))

        // When image is exactly required, sample size is 1
        assertEquals(1, StoreWallpaperPickedUseCase.calculateInSampleSize(1080, 1920, 1080, 1920))

        // When image is 4x display dimensions (4320x7680), sample size should scale by powers of 2
        val sampleSize = StoreWallpaperPickedUseCase.calculateInSampleSize(4320, 7680, 1080, 1920)
        assertTrue(sampleSize >= 4)
    }

    @Test
    fun `getCalendarDateBadge returns uppercase day and valid date`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
        }
        val (dayOfWeek, dayOfMonth) = getCalendarDateBadge(cal)
        assertNotNull(dayOfWeek)
        assertEquals("15", dayOfMonth)
        assertEquals(dayOfWeek, dayOfWeek.uppercase())
    }
}
