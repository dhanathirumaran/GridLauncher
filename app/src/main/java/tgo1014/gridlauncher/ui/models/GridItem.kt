package tgo1014.gridlauncher.ui.models

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.domain.models.App

@Serializable
enum class GridItemType {
    APP,
    WIDGET,
    SHORTCUT,
    SMART_TILE
}

@Serializable
enum class SmartTileType {
    FINDER,
    FLASHLIGHT,
    /**
     * @deprecated The Lock Screen tile and its associated AccessibilityService were removed
     * because Indian UPI / banking apps (e.g. BHIM, Google Pay, PhonePe, YONO SBI) actively scan
     * AccessibilityManager and block launcher users if an accessibility service is registered.
     * Retained here solely for backwards-compatible JSON deserialization of legacy grids,
     * and automatically filtered out in UpdateAppListUseCase.
     */
    @Deprecated("Removed to prevent banking and UPI apps (e.g. BHIM) from detecting accessibility services")
    LOCK_SCREEN,
    STEP_COUNTER,
    BATTERY,
    CURRENCY,
    WEATHER,
    CALENDAR,
    MEDIA_PLAYER
}

@Serializable
data class GridItem(
    val id: Int = -1,
    val app: App = App(),
    val width: Int,
    val height: Int = width,
    val x: Int = 0,
    val y: Int = 0,
    val itemType: GridItemType = GridItemType.APP,
    val smartTileType: SmartTileType? = null,
    val appWidgetId: Int = -1,
    val widgetPackage: String? = null,
    val widgetProvider: String? = null,
    val shortcutId: String? = null,
    val shortcutPackage: String? = null,
    val shortcutLabel: String? = null,
    val shortcutIconPath: String? = null,
    val shortcutIntentUri: String? = null,
)