package tgo1014.gridlauncher.data.smarttile

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object SamsungFinderHelper {

    fun launch(context: Context) {
        // 1. Try Samsung Galaxy Finder launch intent
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.samsung.android.app.galaxyfinder")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }

        // 2. Try explicit Galaxy Finder activity component
        val finderIntent = Intent().apply {
            component = ComponentName(
                "com.samsung.android.app.galaxyfinder",
                "com.samsung.android.app.galaxyfinder.GalaxyFinderActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = pm.resolveActivity(finderIntent, 0)
        if (resolved != null) {
            context.startActivity(finderIntent)
            return
        }

        // 3. Try Samsung One UI Search Activity
        val oneUiSearchIntent = Intent().apply {
            component = ComponentName(
                "com.sec.android.app.launcher",
                "com.sec.android.app.launcher.search.SearchActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolvedOneUi = pm.resolveActivity(oneUiSearchIntent, 0)
        if (resolvedOneUi != null) {
            context.startActivity(oneUiSearchIntent)
            return
        }

        // 4. Fallback to system search / web search
        runCatching {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
        }
    }
}
