package tgo1014.gridlauncher.data.clock

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class NextAlarmInfo(
    val triggerTimeMs: Long,
    val formattedTime: String,
    val dayLabel: String,
    val countdownLabel: String,
    val subtitle: String,
    val label: String = "ALARM"
)

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
            context.registerReceiver(alarmReceiver, filter)
        }
        refresh()
    }

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
        fun computeDayLabel(triggerTimeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val alarmCal = Calendar.getInstance().apply { timeInMillis = triggerTimeMs }

            val nowYear = nowCal.get(Calendar.YEAR)
            val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)

            val alarmYear = alarmCal.get(Calendar.YEAR)
            val alarmDay = alarmCal.get(Calendar.DAY_OF_YEAR)

            return when {
                alarmYear == nowYear && alarmDay == nowDay -> "Today"
                (alarmYear == nowYear && alarmDay == nowDay + 1) ||
                (alarmYear == nowYear + 1 && nowCal.get(Calendar.MONTH) == Calendar.DECEMBER && alarmCal.get(Calendar.MONTH) == Calendar.JANUARY && alarmDay == 1) -> "Tomorrow"
                else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(triggerTimeMs))
            }
        }

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
