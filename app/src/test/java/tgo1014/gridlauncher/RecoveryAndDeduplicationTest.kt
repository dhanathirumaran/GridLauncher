package tgo1014.gridlauncher

import android.graphics.BitmapFactory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.data.AppsManagerDataSourceImpl
import tgo1014.gridlauncher.data.SettingsRepositoryImpl
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.domain.usecases.wallpaper.StoreWallpaperPickedUseCase
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
    fun `calculateInSampleSize scales down large dimensions appropriately`() {
        val smallOptions = BitmapFactory.Options().apply {
            outWidth = 800
            outHeight = 600
        }
        assertEquals(1, StoreWallpaperPickedUseCase.calculateInSampleSize(smallOptions, 1080, 1920))

        val exactOptions = BitmapFactory.Options().apply {
            outWidth = 1080
            outHeight = 1920
        }
        assertEquals(1, StoreWallpaperPickedUseCase.calculateInSampleSize(exactOptions, 1080, 1920))

        val largeOptions = BitmapFactory.Options().apply {
            outWidth = 4320
            outHeight = 7680
        }
        val sampleSize = StoreWallpaperPickedUseCase.calculateInSampleSize(largeOptions, 1080, 1920)
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

    @Test
    fun `TileSettings defaults to transparent tiles enabled without wallpaperPath`() {
        val settings = TileSettings()
        assertTrue(settings.isTransparentTilesEnabled)
        assertTrue(settings.isTransparencyEnabled)
        org.junit.Assert.assertNull(settings.wallpaperPath)
    }

    @Test
    fun `recoverTileSettings restores isTransparentTilesEnabled correctly`() {
        val jsonWithFalse = """{"isTransparentTilesEnabled": false}"""
        val recoveredFalse = SettingsRepositoryImpl.recoverTileSettings(jsonWithFalse, json, null)
        org.junit.Assert.assertFalse(recoveredFalse.isTransparentTilesEnabled)
        org.junit.Assert.assertFalse(recoveredFalse.isTransparencyEnabled)

        val jsonWithTrue = """{"isTransparentTilesEnabled": true}"""
        val recoveredTrue = SettingsRepositoryImpl.recoverTileSettings(jsonWithTrue, json, null)
        assertTrue(recoveredTrue.isTransparentTilesEnabled)
        assertTrue(recoveredTrue.isTransparencyEnabled)
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
    fun `recoverTileSettings nullifies legacy wallpaperPath for system wallpaper passthrough`() {
        val legacyJson = """{"wallpaperPath": "/data/user/0/app/files/wallpaper.png"}"""
        val recovered = SettingsRepositoryImpl.recoverTileSettings(legacyJson, json, null)
        org.junit.Assert.assertNull(recovered.wallpaperPath)
        org.junit.Assert.assertNull(recovered.wallpaperFile)
    }
}
