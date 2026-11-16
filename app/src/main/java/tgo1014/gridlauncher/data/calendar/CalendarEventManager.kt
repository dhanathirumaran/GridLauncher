package tgo1014.gridlauncher.data.calendar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEventManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KNOWN_CALENDAR_PACKAGES = setOf(
            "com.google.android.calendar",
            "com.samsung.android.calendar",
            "com.android.calendar",
            "com.simplemobiletools.calendar",
            "com.simplemobiletools.calendar.pro",
            "com.appgenix.bizcal",
            "org.lineageos.etar",
            "ws.xsoh.etar",
            "com.microsoft.office.outlook",
            "com.digibites.calendar",
            "com.anydo"
        )

        fun isCalendarApp(packageName: String, context: Context? = null): Boolean {
            val lower = packageName.lowercase()
            if (lower in KNOWN_CALENDAR_PACKAGES) return true
            if (lower.endsWith(".calendar") || lower.endsWith(".calendar.pro")) return true
            if (context != null) {
                runCatching {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                    if (resolveInfo?.activityInfo?.packageName == packageName) return true
                }
            }
            return false
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events = _events.asStateFlow()

    private var rawTodayEvents = listOf<CalendarEvent>()

    private val _hasPermission = MutableStateFlow(hasPermission())
    val hasPermission = _hasPermission.asStateFlow()

    private var isObserverRegistered = false
    private var refreshJob: Job? = null
    private val queryVersion = AtomicLong(0)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            refresh()
        }
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK -> {
                    purgeExpiredEvents()
                }
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    refresh()
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(timeTickReceiver, filter)
        } catch (_: Exception) {}

        refresh()
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun refresh() {
        val granted = hasPermission()
        _hasPermission.value = granted
        if (!granted) {
            refreshJob?.cancel()
            _events.value = emptyList()
            rawTodayEvents = emptyList()
            return
        }

        registerObserverIfNeeded()

        val version = queryVersion.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = scope.launch {
            queryTodayEvents(version)
        }
    }

    private fun purgeExpiredEvents() {
        val now = System.currentTimeMillis()
        val active = rawTodayEvents.filter { it.isAllDay || it.endTime >= now }
        _events.value = active
    }

    private fun registerObserverIfNeeded() {
        if (!isObserverRegistered) {
            try {
                context.contentResolver.registerContentObserver(
                    CalendarContract.Events.CONTENT_URI,
                    true,
                    observer
                )
                isObserverRegistered = true
            } catch (e: Exception) {
                // Ignore if permission not granted yet
            }
        }
    }

    private fun queryTodayEvents(version: Long) {
        try {
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis

            calendar.apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endOfDay = calendar.timeInMillis

            // Expand query window by 24h to capture all-day events stored in UTC across any timezone
            val queryStart = startOfDay - 86_400_000L
            val queryEnd = endOfDay + 86_400_000L

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, queryStart)
            ContentUris.appendId(builder, queryEnd)
            val uri = builder.build()

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR
            )

            val eventList = mutableListOf<CalendarEvent>()
            val timeFormatter = DateFormat.getTimeFormat(context)

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val rawTitle = if (titleIdx >= 0) cursor.getString(titleIdx) else null
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else 0L
                    val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                    val color = if (colorIdx >= 0 && !cursor.isNull(colorIdx)) cursor.getInt(colorIdx) else null

                    // Determine if this instance belongs to "today" in local time
                    val isForToday = if (allDay) {
                        val localCal = Calendar.getInstance().apply { timeInMillis = now }
                        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            set(localCal.get(Calendar.YEAR), localCal.get(Calendar.MONTH), localCal.get(Calendar.DATE), 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val todayUtcStart = todayUtc.timeInMillis
                        val todayUtcEnd = todayUtcStart + 86_400_000L
                        val effectiveEnd = if (end > begin) end else begin + 86_400_000L
                        begin < todayUtcEnd && effectiveEnd > todayUtcStart
                    } else {
                        (begin in startOfDay..endOfDay) || (begin <= startOfDay && end >= startOfDay)
                    }

                    if (!isForToday) continue

                    val title = if (!rawTitle.isNullOrBlank()) rawTitle.trim() else "(No title)"
                    val formattedTime = if (allDay) {
                        "All Day"
                    } else {
                        timeFormatter.format(Date(begin))
                    }

                    eventList.add(
                        CalendarEvent(
                            id = id,
                            title = title,
                            startTime = begin,
                            endTime = end,
                            isAllDay = allDay,
                            color = color,
                            formattedTime = formattedTime
                        )
                    )
                }
            }

            val sorted = eventList.sortedWith(
                compareBy<CalendarEvent> { !it.isAllDay }.thenBy { it.startTime }
            )
            if (version == queryVersion.get() && scope.coroutineContext.isActive) {
                rawTodayEvents = sorted
                purgeExpiredEvents()
            }
        } catch (e: Exception) {
            // Guard against SecurityException or SQLite errors
        }
    }
}
