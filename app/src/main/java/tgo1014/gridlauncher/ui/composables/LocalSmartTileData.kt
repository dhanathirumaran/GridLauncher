package tgo1014.gridlauncher.ui.composables

import androidx.compose.runtime.compositionLocalOf
import tgo1014.gridlauncher.data.smarttile.BatteryTileState
import tgo1014.gridlauncher.data.smarttile.CurrencyState
import tgo1014.gridlauncher.data.smarttile.MediaPlaybackState
import tgo1014.gridlauncher.data.smarttile.StepCounterState
import tgo1014.gridlauncher.data.smarttile.WeatherState

data class SmartTileData(
    val flashlightOn: Boolean = false,
    val flashlightAvailable: Boolean = true,
    val stepCounterState: StepCounterState = StepCounterState(),
    val batteryState: BatteryTileState = BatteryTileState(),
    val currencyState: CurrencyState = CurrencyState(),
    val weatherState: WeatherState = WeatherState(),
    val calendarEvents: List<tgo1014.gridlauncher.data.calendar.CalendarEvent> = emptyList(),
    val isCalendarPermissionGranted: Boolean = false,
    val mediaPlaybackState: MediaPlaybackState = MediaPlaybackState(),
    val nextAlarm: tgo1014.gridlauncher.data.clock.NextAlarmInfo? = null
)

val LocalSmartTileData = compositionLocalOf { SmartTileData() }
