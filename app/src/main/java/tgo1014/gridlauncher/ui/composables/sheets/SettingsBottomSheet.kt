package tgo1014.gridlauncher.ui.composables.sheets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    tileSettings: TileSettings,
    isShowing: Boolean,
    isDefaultLauncher: Boolean = false,
    isNotificationAccessGranted: Boolean = false,
    isCalendarPermissionGranted: Boolean = false,
    onSettingsEvent: (SettingsEvent) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    LaunchedEffect(isShowing) {
        if (isShowing) sheetState.show() else sheetState.hide()
    }
    if (isShowing) {
        ModalBottomSheet(
            scrimColor = Color.Transparent,
            onDismissRequest = { onSettingsEvent(SettingsEvent.OnSettingsSheetDismissed) },
            sheetState = sheetState
        ) {
            SettingsBottomSheet(
                tileSettings = tileSettings,
                isDefaultLauncher = isDefaultLauncher,
                isNotificationAccessGranted = isNotificationAccessGranted,
                isCalendarPermissionGranted = isCalendarPermissionGranted,
                onSettingsEvent = onSettingsEvent,
            )
        }
    }
}

@Composable
private fun SettingsBottomSheet(
    tileSettings: TileSettings = TileSettings(),
    isDefaultLauncher: Boolean = false,
    isNotificationAccessGranted: Boolean = false,
    isCalendarPermissionGranted: Boolean = false,
    onSettingsEvent: (SettingsEvent) -> Unit = {},
) {
    var tintSlider by remember(tileSettings.tintAlpha) { mutableStateOf(tileSettings.tintAlpha) }
    var cornerSlider by remember(tileSettings.cornerRadius) { mutableStateOf(tileSettings.cornerRadius.toFloat()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Default Launcher")
                Text(
                    text = if (isDefaultLauncher) "GridLauncher is set as default" else "Not set as default launcher",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSettingsEvent(SettingsEvent.OnOpenDefaultLauncherSettings) }
            ) {
                Text(if (isDefaultLauncher) "Change" else "Set Default")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Notification Access")
                Text(
                    text = if (isNotificationAccessGranted) "Active — contact names shown on live tiles" else "Permission needed for live message tiles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSettingsEvent(SettingsEvent.OnOpenNotificationAccessSettings) }
            ) {
                Text(if (isNotificationAccessGranted) "Settings" else "Enable")
            }
        }
        val calendarPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onSettingsEvent(SettingsEvent.OnCalendarPermissionGranted)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Calendar Access")
                Text(
                    text = if (isCalendarPermissionGranted) "Active — today's events shown on calendar tiles" else "Permission needed for Apple-style calendar tile",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (!isCalendarPermissionGranted) {
                        calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                    } else {
                        onSettingsEvent(SettingsEvent.OnOpenAppSettings)
                    }
                }
            ) {
                Text(if (isCalendarPermissionGranted) "Granted" else "Enable")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tile Flipping Enabled:", modifier = Modifier.weight(1f))
            Switch(
                checked = tileSettings.isTileFlipEnabled,
                onCheckedChange = {
                    val newSettings = tileSettings.copy(isTileFlipEnabled = it)
                    onSettingsEvent(SettingsEvent.OnSettingsUpdated(newSettings))
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hide app labels:", modifier = Modifier.weight(1f))
            Switch(
                checked = tileSettings.isAppLabelsHidden,
                onCheckedChange = {
                    val newSettings = tileSettings.copy(isAppLabelsHidden = it)
                    onSettingsEvent(SettingsEvent.OnSettingsUpdated(newSettings))
                },
            )
        }
        // Transparent Glass Tiles Switch:
        // Decoupled from manual photo picking. Works natively with Android's windowShowWallpaper.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transparent Glass Tiles:", modifier = Modifier.weight(1f))
            Switch(
                checked = tileSettings.isTransparencyEnabled,
                onCheckedChange = {
                    val newSettings = tileSettings.copy(isTransparentTilesEnabled = it)
                    onSettingsEvent(SettingsEvent.OnSettingsUpdated(newSettings))
                }
            )
        }

        if (tileSettings.isTransparencyEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Glass Opacity / Tint:", modifier = Modifier.weight(1f))
                Text("${(tintSlider * 100).toInt()}%", modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = tintSlider,
                    onValueChange = { tintSlider = it },
                    onValueChangeFinished = {
                        onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(tintAlpha = tintSlider)))
                    },
                    valueRange = 0.0f..0.50f,
                    modifier = Modifier.weight(1.5f),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Corners:", modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            )
            Text(text = "${cornerSlider.toInt()}")
            Slider(
                value = cornerSlider,
                onValueChange = { cornerSlider = it },
                onValueChangeFinished = {
                    onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(cornerRadius = cornerSlider.toInt())))
                },
                valueRange = 0f..50f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = false, backgroundColor = 0xFFFFFFFF)
private fun Preview() = GridLauncherTheme {
    SettingsBottomSheet()
}