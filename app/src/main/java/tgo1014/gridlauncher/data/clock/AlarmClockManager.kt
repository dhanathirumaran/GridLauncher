package tgo1014.gridlauncher.data.clock

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the next scheduled system alarm formatted for UI presentation.
 */
data class NextAlarmInfo(
    val triggerTimeMs: Long,
    val formattedTime: String,
    val dayLabel: String,
    val countdownLabel: String,
    val subtitle: String,
    val label: String = "ALARM"
)

/**
 * Monitors and formats the next upcoming user alarm from Android's AlarmManager.
 *
 * - Zero extra permissions required (uses standard public AlarmManager.getNextAlarmClock()).
 * - BroadcastReceiver listens for NEXT_ALARM_CLOCK_CHANGED, ACTION_TIME_TICK (1-min live updates),
 *   ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED, and ACTION_DATE_CHANGED.
 * - Computes localized 12h/24h time, day label ("Today", "Tomorrow", weekday),
 *   and real-time countdown ("in 7h 30m").
 */
@Singleton
class AlarmClockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _nextAlarm = MutableStateFlow<NextAlarmInfo?>(null)
    val nextAlarm = _nextAlarm.asStateFlow()

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                alarmReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        refresh()
    }

    /**
     * Refreshes the next alarm clock information from AlarmManager and updates the StateFlow.
     */
    @Synchronized
    fun refresh() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmClockInfo = runCatching { alarmManager?.nextAlarmClock }.getOrNull()

        if (alarmClockInfo == null) {
            _nextAlarm.value = null
            return
        }

        val triggerTimeMs = alarmClockInfo.triggerTime
        val now = System.currentTimeMillis()

        // If alarm trigger time has passed by more than 1 minute, clear it
        if (triggerTimeMs <= now - 60_000L) {
            _nextAlarm.value = null
            return
        }

        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        val formattedTime = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(triggerTimeMs))

        val dayLabel = computeDayLabel(triggerTimeMs, now)
        val countdownLabel = computeCountdownLabel(triggerTimeMs, now)
        val subtitle = "$dayLabel • $countdownLabel"

        _nextAlarm.value = NextAlarmInfo(
            triggerTimeMs = triggerTimeMs,
            formattedTime = formattedTime,
            dayLabel = dayLabel,
            countdownLabel = countdownLabel,
            subtitle = subtitle,
            label = "ALARM"
        )
    }

    companion object {
        /**
         * Computes the day label: "Today", "Tomorrow" (with canonical year-end and month-end rollover),
         * or the short day of week (e.g. "Wed").
         *
         * CALENDAR DAY BOUNDARY NORMALIZATION:
         * Previously, checking `nowCal.get(Calendar.MONTH) == Calendar.DECEMBER` without verifying
         * that today is December 31 caused any January 1 alarm to show "Tomorrow" for the entire month
         * of December (e.g., an alarm on Jan 1 showed "Tomorrow" on Dec 1).
         *
         * By normalizing both [nowMs] and [triggerTimeMs] to midnight (00:00:00.000) and adding
         * exactly 1 calendar day to [nowMidnight], we achieve a canonical date comparison that handles
         * month rollovers, leap years, daylight saving time (DST) shifts, and year boundaries seamlessly.
         */
        fun computeDayLabel(triggerTimeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
            val nowMidnight = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val tomorrowMidnight = (nowMidnight.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val alarmMidnight = Calendar.getInstance().apply {
                timeInMillis = triggerTimeMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return when {
                alarmMidnight.timeInMillis == nowMidnight.timeInMillis -> "Today"
                alarmMidnight.timeInMillis == tomorrowMidnight.timeInMillis -> "Tomorrow"
                else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(triggerTimeMs))
            }
        }

        /**
         * Computes human-readable remaining time countdown:
         * - "in < 1m"
         * - "in 45m"
         * - "in 7h 30m"
         * - "in 2d 5h"
         */
        fun computeCountdownLabel(triggerTimeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
            val diffMs = (triggerTimeMs - nowMs).coerceAtLeast(0)
            val totalMinutes = diffMs / 60_000L
            val days = totalMinutes / (24 * 60)
            val remMinutesAfterDays = totalMinutes % (24 * 60)
            val hours = remMinutesAfterDays / 60
            val minutes = remMinutesAfterDays % 60

            return when {
                days > 0 -> {
                    if (hours > 0) "in ${days}d ${hours}h" else "in ${days}d"
                }
                hours > 0 && minutes > 0 -> "in ${hours}h ${minutes}m"
                hours > 0 -> "in ${hours}h"
                minutes > 0 -> "in ${minutes}m"
                else -> "in < 1m"
            }
        }
    }
}
