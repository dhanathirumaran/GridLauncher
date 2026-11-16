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
        assertEquals(0L, settings.wallpaperTimestamp)
        org.junit.Assert.assertFalse(settings.isTransparencyEnabled)
        assertEquals(30, settings.blurRadius)
        assertEquals(0.10f, settings.tintAlpha, 0.001f)
    }

    @Test
    fun `recoverTileSettings restores wallpaperPath and enables transparency`() {
        val jsonWithoutWallpaper = """{"blurRadius": 30}"""
        val recoveredWithout = SettingsRepositoryImpl.recoverTileSettings(jsonWithoutWallpaper, json, null)
        org.junit.Assert.assertFalse(recoveredWithout.isTransparencyEnabled)
        assertEquals(0L, recoveredWithout.wallpaperTimestamp)

        val jsonWithWallpaper = """{"wallpaperPath": "/data/user/0/wallpaper.png", "wallpaperTimestamp": 123456789, "blurRadius": 25}"""
        val recoveredWith = SettingsRepositoryImpl.recoverTileSettings(jsonWithWallpaper, json, null)
        assertTrue(recoveredWith.isTransparencyEnabled)
        assertEquals("/data/user/0/wallpaper.png", recoveredWith.wallpaperPath)
        assertEquals(123456789L, recoveredWith.wallpaperTimestamp)
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

    @Test
    fun `AlarmClockManager computeDayLabel handles month-end and year-end boundaries accurately`() {
        // Today is Dec 31, 2026 at 10:00 PM
        val dec31 = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 31, 22, 0, 0)
        }.timeInMillis

        // Alarm is Jan 1, 2027 at 7:00 AM (next morning)
        val jan1 = Calendar.getInstance().apply {
            set(2027, Calendar.JANUARY, 1, 7, 0, 0)
        }.timeInMillis

        // Today is Dec 1, 2026 at 10:00 AM
        val dec1 = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 1, 10, 0, 0)
        }.timeInMillis

        // On Dec 31, Jan 1 is Tomorrow
        val labelOnDec31 = tgo1014.gridlauncher.data.clock.AlarmClockManager.computeDayLabel(jan1, dec31)
        assertEquals("Tomorrow", labelOnDec31)

        // On Dec 1, Jan 1 is NOT Tomorrow (it is a month away, showing day of week e.g. Fri)
        val labelOnDec1 = tgo1014.gridlauncher.data.clock.AlarmClockManager.computeDayLabel(jan1, dec1)
        org.junit.Assert.assertNotEquals("Tomorrow", labelOnDec1)

        // Same day alarm is Today
        val labelToday = tgo1014.gridlauncher.data.clock.AlarmClockManager.computeDayLabel(dec1 + 3600_000L, dec1)
        assertEquals("Today", labelToday)
    }

    @Test
    fun `smart tiles are strictly deduplicated by tile type`() {
        val items = listOf(
            tgo1014.gridlauncher.ui.models.GridItem(id = 1, width = 2, itemType = tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE, smartTileType = tgo1014.gridlauncher.ui.models.SmartTileType.WEATHER),
            tgo1014.gridlauncher.ui.models.GridItem(id = 2, width = 2, itemType = tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE, smartTileType = tgo1014.gridlauncher.ui.models.SmartTileType.WEATHER),
            tgo1014.gridlauncher.ui.models.GridItem(id = 3, width = 2, itemType = tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE, smartTileType = tgo1014.gridlauncher.ui.models.SmartTileType.BATTERY),
            tgo1014.gridlauncher.ui.models.GridItem(id = 4, width = 2, itemType = tgo1014.gridlauncher.ui.models.GridItemType.SMART_TILE, smartTileType = tgo1014.gridlauncher.ui.models.SmartTileType.BATTERY)
        )
        val seenTypes = mutableSetOf<tgo1014.gridlauncher.ui.models.SmartTileType>()
        val deduped = items.filter { item ->
            item.smartTileType != null && seenTypes.add(item.smartTileType!!)
        }
        assertEquals(2, deduped.size)
        assertEquals(listOf(tgo1014.gridlauncher.ui.models.SmartTileType.WEATHER, tgo1014.gridlauncher.ui.models.SmartTileType.BATTERY), deduped.map { it.smartTileType })
    }
}
