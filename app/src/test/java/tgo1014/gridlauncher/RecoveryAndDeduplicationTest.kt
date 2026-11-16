package tgo1014.gridlauncher

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.data.AppsManagerDataSourceImpl
import tgo1014.gridlauncher.data.SettingsRepositoryImpl
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.composables.getCalendarDateBadge
import java.util.Calendar

class RecoveryAndDeduplicationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `recoverTileSettings extracts valid fields from partial JSON`() {
        val partialJson = """
            {
                "cornerRadius": 16,
                "tintAlpha": 0.35,
                "isAppLabelsHidden": true,
                "unexpectedField": 123
            }
        """.trimIndent()

        val settings = SettingsRepositoryImpl.recoverTileSettings(partialJson, json, null)
        assertEquals(16, settings.cornerRadius)
        assertEquals(0.35f, settings.tintAlpha, 0.001f)
        assertTrue(settings.isAppLabelsHidden)
        // Default fields preserved
        assertTrue(settings.isTileFlipEnabled)
    }

    @Test
    fun `recoverTileSettings returns fallback on completely unparseable input`() {
        val corrupted = "{ broken json !! 123 "
        val fallback = TileSettings(cornerRadius = 24)
        val settings = SettingsRepositoryImpl.recoverTileSettings(corrupted, json, fallback)
        assertEquals(24, settings.cornerRadius)
    }

    @Test
    fun `recoverGridItems recovers intact items from JSON with corrupted elements`() {
        val validItem = """{"id":1,"app":{"name":"TestApp","packageName":"com.test.app","isSystemApp":false},"itemType":"APP","width":1,"height":1,"x":0,"y":0}"""
        val invalidItem = """{"id":2,"itemType":"UNKNOWN_CORRUPTED_TYPE"}"""
        val arrayJson = "[$validItem, $invalidItem]"

        val recovered = AppsManagerDataSourceImpl.recoverGridItems(arrayJson, json)
        assertEquals(1, recovered.size)
        assertEquals("com.test.app", recovered[0].app.packageName)
        assertEquals("TestApp", recovered[0].app.name)
    }

    @Test
    fun `recoverGridItems returns empty list for non-array string`() {
        val nonArray = "not a json array"
        val recovered = AppsManagerDataSourceImpl.recoverGridItems(nonArray, json)
        assertTrue(recovered.isEmpty())
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

    @Test
    fun `TileSettings defaults to no wallpaper and transparency disabled`() {
        val settings = TileSettings()
        org.junit.Assert.assertNull(settings.wallpaperPath)
        org.junit.Assert.assertFalse(settings.isTransparencyEnabled)
        assertEquals(30, settings.blurRadius)
        assertEquals(0.10f, settings.tintAlpha, 0.001f)
    }

    @Test
    fun `recoverTileSettings restores wallpaperPath and enables transparency`() {
        val jsonWithoutWallpaper = """{"blurRadius": 30}"""
        val recoveredWithout = SettingsRepositoryImpl.recoverTileSettings(jsonWithoutWallpaper, json, null)
        org.junit.Assert.assertFalse(recoveredWithout.isTransparencyEnabled)

        val jsonWithWallpaper = """{"wallpaperPath": "/data/user/0/wallpaper.png", "blurRadius": 25}"""
        val recoveredWith = SettingsRepositoryImpl.recoverTileSettings(jsonWithWallpaper, json, null)
        assertTrue(recoveredWith.isTransparencyEnabled)
        assertEquals("/data/user/0/wallpaper.png", recoveredWith.wallpaperPath)
        assertEquals(25, recoveredWith.blurRadius)
    }

    @Test
    fun `legacy grid with LOCK_SCREEN deserializes properly and can be filtered out`() {
        val legacyJson = """
            [
                {"id": 1, "width": 2, "height": 2, "itemType": "SMART_TILE", "smartTileType": "LOCK_SCREEN"},
                {"id": 2, "width": 2, "height": 2, "itemType": "SMART_TILE", "smartTileType": "FLASHLIGHT"}
            ]
        """.trimIndent()
        val items = json.decodeFromString<List<tgo1014.gridlauncher.ui.models.GridItem>>(legacyJson)
        assertEquals(2, items.size)
        @Suppress("DEPRECATION")
        val filtered = items.filterNot { it.smartTileType == tgo1014.gridlauncher.ui.models.SmartTileType.LOCK_SCREEN }
        assertEquals(1, filtered.size)
        assertEquals(tgo1014.gridlauncher.ui.models.SmartTileType.FLASHLIGHT, filtered.first().smartTileType)
    }
}
