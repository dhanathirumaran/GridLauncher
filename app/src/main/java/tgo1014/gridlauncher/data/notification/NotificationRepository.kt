package tgo1014.gridlauncher.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor() {

    companion object {
        private val _sharedNotifications = MutableStateFlow<Map<String, List<NotificationSender>>>(emptyMap())
        val sharedNotifications = _sharedNotifications.asStateFlow()

        fun setNotifications(packageNotifications: Map<String, List<NotificationSender>>) {
            _sharedNotifications.value = packageNotifications
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return context.packageName in enabledListeners
        }
    }

    val notifications = sharedNotifications

    fun updateNotifications(packageNotifications: Map<String, List<NotificationSender>>) {
        setNotifications(packageNotifications)
    }

    fun isAccessGranted(context: Context): Boolean = isNotificationAccessGranted(context)
}
