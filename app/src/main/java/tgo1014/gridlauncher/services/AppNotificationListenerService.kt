package tgo1014.gridlauncher.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import tgo1014.gridlauncher.data.notification.NotificationRepository
import tgo1014.gridlauncher.data.notification.NotificationSender

class AppNotificationListenerService : NotificationListenerService() {

    companion object {
        var isConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        syncActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        syncActiveNotifications()
    }

    private fun syncActiveNotifications() {
        try {
            val active = activeNotifications ?: emptyArray()
            val packageMap = mutableMapOf<String, MutableList<NotificationSender>>()

            for (sbn in active) {
                // Ignore ongoing notifications like background calls or media playback
                if (sbn.isOngoing) continue

                val pkg = sbn.packageName ?: continue
                val notif = sbn.notification ?: continue
                val extras = notif.extras ?: continue

                val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                val senderName = convTitle?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() }

                if (senderName.isNullOrBlank()) continue

                // Filter out non-sender system alerts
                if (isIgnoredTitle(senderName)) continue

                val sender = NotificationSender(
                    name = senderName,
                    count = 1,
                    timestamp = sbn.postTime
                )

                packageMap.getOrPut(pkg) { mutableListOf() }.add(sender)
            }

            // Deduplicate senders per package and sort by most recent
            val finalizedMap = packageMap.mapValues { (_, senders) ->
                senders.groupBy { it.name }
                    .map { (name, list) ->
                        NotificationSender(
                            name = name,
                            count = list.size,
                            timestamp = list.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                        )
                    }
                    .sortedByDescending { it.timestamp }
            }

            NotificationRepository.setNotifications(finalizedMap)
        } catch (e: Exception) {
            // Guard against SecurityException or IPC errors during system teardown
        }
    }

    private fun isIgnoredTitle(title: String): Boolean {
        val lower = title.lowercase()
        return lower in listOf(
            "whatsapp",
            "whatsapp web",
            "checking for new messages",
            "backup in progress",
            "messages",
            "telegram",
            "signal"
        ) || lower.endsWith("new messages")
    }
}
