package tgo1014.gridlauncher.data.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.ViewGroup

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        clipToOutline = true
    }

    override fun getDescendantFocusability(): Int {
        return ViewGroup.FOCUS_AFTER_DESCENDANTS
    }
}
