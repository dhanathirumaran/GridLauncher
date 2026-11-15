package tgo1014.gridlauncher.ui.composables

import androidx.compose.runtime.compositionLocalOf
import tgo1014.gridlauncher.data.notification.NotificationSender

val LocalAppNotifications = compositionLocalOf<Map<String, List<NotificationSender>>> { emptyMap() }
