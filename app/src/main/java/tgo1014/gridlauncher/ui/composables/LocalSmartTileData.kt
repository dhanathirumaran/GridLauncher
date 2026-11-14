package tgo1014.gridlauncher.ui.composables

import androidx.compose.runtime.staticCompositionLocalOf
import tgo1014.gridlauncher.data.smarttile.BatteryTileState
import tgo1014.gridlauncher.data.smarttile.WeatherState

data class SmartTileData(
    val flashlightOn: Boolean = false,
    val stepCount: Int = 0,
    val batteryState: BatteryTileState = BatteryTileState(),
    val currencyRate: String = "84.40",
    val weatherState: WeatherState = WeatherState()
)

val LocalSmartTileData = staticCompositionLocalOf { SmartTileData() }
