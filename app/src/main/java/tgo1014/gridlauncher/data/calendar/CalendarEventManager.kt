package tgo1014.gridlauncher.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEventManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        fun isCalendarApp(packageName: String): Boolean {
            val lower = packageName.lowercase()
            return lower in listOf(
                "com.google.android.calendar",
                "com.samsung.android.calendar",
                "com.android.calendar",
                "com.simplemobiletools.calendar",
                "com.simplemobiletools.calendar.pro",
                "com.appgenix.bizcal",
                "org.lineageos.etar",
                "ws.xsoh.etar"
            ) || lower.contains("calendar")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events = _events.asStateFlow()

    private val _hasPermission = MutableStateFlow(hasPermission())
    val hasPermission = _hasPermission.asStateFlow()

    private var isObserverRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            refresh()
        }
    }

    init {
        refresh()
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refresh() {
        val granted = hasPermission()
        _hasPermission.value = granted
        if (!granted) {
            _events.value = emptyList()
            return
        }

        registerObserverIfNeeded()

        scope.launch {
            queryTodayEvents()
        }
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

    private fun queryTodayEvents() {
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

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startOfDay)
            ContentUris.appendId(builder, endOfDay)
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
            val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

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

                    // Only show ongoing or upcoming events for today
                    if (end < now && !allDay) continue

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

            _events.value = eventList.sortedWith(
                compareBy<CalendarEvent> { it.isAllDay }.thenBy { it.startTime }
            )
        } catch (e: Exception) {
            // Guard against SecurityException or SQLite errors
        }
    }
}
