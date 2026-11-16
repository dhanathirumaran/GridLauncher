package tgo1014.gridlauncher.ui.models

import tgo1014.gridlauncher.domain.models.TileSettings

sealed class SettingsEvent {
    data object OnSettingsIconClicked : SettingsEvent()
    data object OnSettingsSheetDismissed : SettingsEvent()
    data class OnSettingsUpdated(val tileSettings: TileSettings) : SettingsEvent()
    data object OnOpenDefaultLauncherSettings : SettingsEvent()
    data object OnOpenNotificationAccessSettings : SettingsEvent()
    data object OnCalendarPermissionGranted : SettingsEvent()
    data object OnOpenAppSettings : SettingsEvent()
}