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
 *
 * MAIN-THREAD JANK PREVENTION:
 * Previously, calling `pm.resolveActivity` and `pm.queryIntentActivities` (3 IPC Binder transactions)
 * per app tile inside Compose layout/composition caused massive main-thread frame drops during scrolling.
 * The results are now pre-warmed off the main thread and cached in an in-memory Set, providing
 * instantaneous O(1) lookups during Compose rendering.
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

    @Volatile
    private var cachedClockPackages: Set<String>? = null

    fun invalidateCache() {
        cachedClockPackages = null
    }

    fun prewarmCache(context: Context) {
        if (cachedClockPackages != null) return
        synchronized(this) {
            if (cachedClockPackages != null) return
            val set = HashSet(KNOWN_CLOCK_PACKAGES)
            runCatching {
                val pm = context.packageManager

                val showIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                pm.resolveActivity(showIntent, 0)?.activityInfo?.packageName?.let { set.add(it) }
                pm.queryIntentActivities(showIntent, 0).forEach { ri ->
                    ri.activityInfo?.packageName?.let { set.add(it) }
                }

                val setIntent = Intent(AlarmClock.ACTION_SET_ALARM)
                pm.queryIntentActivities(setIntent, 0).forEach { ri ->
                    ri.activityInfo?.packageName?.let { set.add(it) }
                }
            }
            cachedClockPackages = set
        }
    }

    /**
     * Checks if the package name is a clock app by package ID or by querying
     * system alarm intent handlers.
     */
    fun isClockApp(packageName: String, context: Context? = null): Boolean {
        if (packageName in KNOWN_CLOCK_PACKAGES) return true

        // Fast path: In-memory O(1) lookup
        cachedClockPackages?.let { return packageName in it }

        if (context != null) {
            prewarmCache(context)
            return cachedClockPackages?.contains(packageName) == true
        }
        return false
    }
}
