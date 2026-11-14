package tgo1014.gridlauncher.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration

class LauncherAppWidgetHost(
    context: Context,
    hostId: Int = APPWIDGET_HOST_ID,
) : AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val darkContext = context.createConfigurationContext(config)
        return LauncherAppWidgetHostView(darkContext)
    }

    companion object {
        const val APPWIDGET_HOST_ID = 2048
    }
}
