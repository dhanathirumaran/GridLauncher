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
        tgo1014.gridlauncher.data.smarttile.MediaPlaybackManager.sync()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        tgo1014.gridlauncher.data.smarttile.MediaPlaybackManager.sync()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        syncActiveNotifications()
        tgo1014.gridlauncher.data.smarttile.MediaPlaybackManager.sync()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        syncActiveNotifications()
        tgo1014.gridlauncher.data.smarttile.MediaPlaybackManager.sync()
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
                val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

                // Check for missed call
                val isMissedCategory = notif.category == Notification.CATEGORY_MISSED_CALL
                val titleLower = rawTitle?.lowercase().orEmpty()
                val textLower = rawText?.lowercase().orEmpty()
                val isMissedCall = isMissedCategory ||
                        titleLower == "missed call" ||
                        titleLower.startsWith("missed call") ||
                        textLower == "missed call" ||
                        (tgo1014.gridlauncher.data.phone.PhoneAppManager.isPhoneApp(pkg, this) && (titleLower.contains("missed") || textLower.contains("missed")))

                val senderName: String?
                if (isMissedCall) {
                    senderName = tgo1014.gridlauncher.data.phone.PhoneAppManager.extractCallerName(rawTitle, rawText)
                } else {
                    senderName = convTitle?.takeIf { it.isNotBlank() } ?: rawTitle?.takeIf { it.isNotBlank() }
                }

                if (senderName.isNullOrBlank()) continue

                // Filter out non-sender system alerts
                if (!isMissedCall && isIgnoredTitle(senderName)) continue

                val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
                val sender = NotificationSender(
                    name = senderName,
                    count = 1,
                    timestamp = postTime,
                    isMissedCall = isMissedCall
                )

                val targetPackages = mutableSetOf(pkg)
                if (isMissedCall) {
                    targetPackages.addAll(tgo1014.gridlauncher.data.phone.PhoneAppManager.getAllDialerPackages(this))
                }

                for (targetPkg in targetPackages) {
                    packageMap.getOrPut(targetPkg) { mutableListOf() }.add(sender)
                }
            }

            // Deduplicate senders per package and sort by most recent
            val finalizedMap = packageMap.mapValues { (_, senders) ->
                senders.groupBy { it.name }
                    .map { (name, list) ->
                        val hasMissedCall = list.any { it.isMissedCall }
                        NotificationSender(
                            name = name,
                            count = list.size,
                            timestamp = list.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
                            isMissedCall = hasMissedCall
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
