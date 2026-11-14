package tgo1014.gridlauncher.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class LauncherAppWidgetHost(
    context: Context,
    hostId: Int = APPWIDGET_HOST_ID,
) : AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context)
    }

    companion object {
        const val APPWIDGET_HOST_ID = 2048
    }
}
