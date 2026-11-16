package tgo1014.gridlauncher.data.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * Detects whether an application is a Clock / Alarm app.
 *
 * Checks against known OEM clock package identifiers (Samsung, Google Deskclock,
 * AOSP, OnePlus, ColorOS, Asus, HTC, Sony) and falls back to PackageManager queries
 * for AlarmClock.ACTION_SHOW_ALARMS and ACTION_SET_ALARM intents.
 */
object ClockAppManager {

    val KNOWN_CLOCK_PACKAGES = setOf(
        "com.sec.android.app.clockpackage",       // Samsung Clock
        "com.google.android.deskclock",            // Google Clock
        "com.android.deskclock",                   // AOSP / LineageOS / Xiaomi Clock
        "com.oneplus.deskclock",                   // OnePlus Clock
        "com.coloros.alarmclock",                  // Oppo / Realme Clock
        "com.asus.deskclock",                      // Asus Clock
        "com.htc.android.worldclock",              // HTC Clock
        "com.sonyericsson.organizer"               // Sony Clock
    )

    /**
     * Checks if the package name is a clock app by package ID or by querying
     * system alarm intent handlers.
     */
    fun isClockApp(packageName: String, context: Context? = null): Boolean {
        if (packageName in KNOWN_CLOCK_PACKAGES) return true
        if (context != null) {
            return runCatching {
                val pm = context.packageManager

                val showIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                val resolvedShow = pm.resolveActivity(showIntent, 0)
                if (resolvedShow?.activityInfo?.packageName == packageName) return@runCatching true
                val showActivities = pm.queryIntentActivities(showIntent, 0)
                if (showActivities.any { it.activityInfo?.packageName == packageName }) return@runCatching true

                val setIntent = Intent(AlarmClock.ACTION_SET_ALARM)
                val setActivities = pm.queryIntentActivities(setIntent, 0)
                setActivities.any { it.activityInfo?.packageName == packageName }
            }.getOrDefault(false)
        }
        return false
    }
}
