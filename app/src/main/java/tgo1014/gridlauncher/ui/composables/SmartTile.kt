package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.data.smarttile.BatteryTileState
import tgo1014.gridlauncher.data.smarttile.CurrencyState
import tgo1014.gridlauncher.data.smarttile.StepCounterState
import tgo1014.gridlauncher.data.smarttile.WeatherState
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SmartTileType
import tgo1014.gridlauncher.ui.theme.flipRandomly
import tgo1014.gridlauncher.ui.theme.modifyIf
import tgo1014.gridlauncher.ui.theme.tileEditMode
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartTile(
    item: GridItem,
    modifier: Modifier = Modifier,
    tileSettings: TileSettings = TileSettings(),
    isEditMode: Boolean = false,
    flashlightOn: Boolean = false,
    flashlightAvailable: Boolean = true,
    stepCounterState: StepCounterState = StepCounterState(),
    batteryState: BatteryTileState = BatteryTileState(),
    currencyState: CurrencyState = CurrencyState(),
    weatherState: WeatherState = WeatherState(),
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemLongClicked: (item: GridItem) -> Unit = {},
) {
    val shape = RoundedCornerShape(tileSettings.cornerRadius)
    val contentColor = if (tileSettings.isTransparencyEnabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val type = item.smartTileType ?: SmartTileType.FINDER

    // Determine title, icon, telemetry, and subtext based on SmartTileType
    val (title, icon, telemetry, subtext) = when (type) {
        SmartTileType.FINDER -> {
            Quad("Finder", SmartTileIcons.Search, null, null)
        }
        SmartTileType.FLASHLIGHT -> {
            val status = when {
                !flashlightAvailable -> "Unavailable"
                flashlightOn -> "On"
                else -> "Off"
            }
            Quad("Flashlight", SmartTileIcons.Flashlight, status, null)
        }
        SmartTileType.LOCK_SCREEN -> {
            Quad("Lock Screen", SmartTileIcons.Lock, null, null)
        }
        SmartTileType.STEP_COUNTER -> {
            when {
                !stepCounterState.isSupported -> {
                    Quad("Walk", SmartTileIcons.Walk, "No Sensor", "unsupported")
                }
                !stepCounterState.hasPermission -> {
                    Quad("Walk", SmartTileIcons.Walk, "Permission", "tap to grant")
                }
                else -> {
                    val formatted = String.format(Locale.US, "%,d", stepCounterState.steps)
                    Quad("Walk", SmartTileIcons.Walk, formatted, "steps today")
                }
            }
        }
        SmartTileType.BATTERY -> {
            if (batteryState.isConnectedToBluetoothDevice && batteryState.bluetoothDeviceBattery != null) {
                val btTitle = batteryState.bluetoothDeviceName ?: "Accessory"
                Quad(btTitle, SmartTileIcons.Headphone, "${batteryState.bluetoothDeviceBattery}%", "Connected")
            } else {
                val chargingText = when {
                    batteryState.isCharging && batteryState.chargingWatts > 0 -> "${batteryState.chargingWatts}W Charging"
                    batteryState.isCharging -> "Charging"
                    else -> null
                }
                Quad("Battery", SmartTileIcons.Battery, "${batteryState.phoneLevel}%", chargingText)
            }
        }
        SmartTileType.CURRENCY -> {
            val rateText = if (currencyState.rate == "--.--") "--.--" else "₹${currencyState.rate}"
            val sub = when {
                currencyState.isError && currencyState.isStale -> "1 USD (Offline)"
                currencyState.isError -> "Offline"
                else -> "1 USD"
            }
            Quad("USD / INR", SmartTileIcons.Currency, rateText, sub)
        }
        SmartTileType.WEATHER -> {
            val weatherIcon = when {
                weatherState.isLocationMissing -> SmartTileIcons.Search
                weatherState.weatherCode >= 50 -> SmartTileIcons.WeatherCloud
                else -> SmartTileIcons.WeatherSun
            }
            val sub = when {
                weatherState.isLocationMissing -> "No Location"
                weatherState.isError -> "Offline"
                else -> weatherState.condition
            }
            Quad("Weather", weatherIcon, weatherState.temperature, sub)
        }
    }

    // Highlighting background for flashlight when ON
    val tileBackground = if (!tileSettings.isTransparencyEnabled) {
        if (type == SmartTileType.FLASHLIGHT && flashlightOn) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .modifyIf(!isEditMode && tileSettings.isTileFlipEnabled) {
                flipRandomly()
            }
            .tileEditMode(isEditMode)
            .clip(shape)
            .background(tileBackground)
            .combinedClickable(
                onClick = { onItemClicked(item) },
                onLongClick = { onItemLongClicked(item) }
            )
            .then(modifier)
    ) {
        when {
            // 2x1 Wide tile (or 4x1)
            item.width >= 2 && item.height == 1 -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(26.dp)
                        )
                        if (telemetry != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = telemetry,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 1x1 Small square tile
            item.width == 1 && item.height == 1 -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (telemetry != null && (type == SmartTileType.BATTERY || type == SmartTileType.WEATHER)) {
                        Text(
                            text = telemetry,
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Standard 2x2 or larger square/tall tiles
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Center content: Icon and live telemetry
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(36.dp)
                        )
                        if (telemetry != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = telemetry,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (subtext != null) {
                            Text(
                                text = subtext,
                                color = contentColor.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Windows Phone bottom-start label
                    if (!tileSettings.isAppLabelsHidden) {
                        Text(
                            text = title,
                            color = contentColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(8.dp)
                                .padding(horizontal = (tileSettings.cornerRadius * 0.5).dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
