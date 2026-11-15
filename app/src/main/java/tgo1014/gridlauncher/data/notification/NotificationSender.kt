package tgo1014.gridlauncher.data.notification

import kotlinx.serialization.Serializable

@Serializable
data class NotificationSender(
    val name: String,
    val count: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val isMissedCall: Boolean = false
)
