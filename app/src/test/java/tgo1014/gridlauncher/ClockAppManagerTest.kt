package tgo1014.gridlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.data.clock.AlarmClockManager
import tgo1014.gridlauncher.data.clock.ClockAppManager
import java.util.Calendar

class ClockAppManagerTest {

    @Test
    fun isClockApp_detectsKnownClockPackages() {
        assertTrue(ClockAppManager.isClockApp("com.sec.android.app.clockpackage"))
        assertTrue(ClockAppManager.isClockApp("com.google.android.deskclock"))
        assertTrue(ClockAppManager.isClockApp("com.android.deskclock"))
        assertTrue(ClockAppManager.isClockApp("com.oneplus.deskclock"))
        assertTrue(ClockAppManager.isClockApp("com.coloros.alarmclock"))
        assertTrue(ClockAppManager.isClockApp("com.asus.deskclock"))
        assertTrue(ClockAppManager.isClockApp("com.htc.android.worldclock"))
        assertTrue(ClockAppManager.isClockApp("com.sonyericsson.organizer"))
    }

    @Test
    fun isClockApp_rejectsNonClockPackages() {
        assertFalse(ClockAppManager.isClockApp("com.google.android.youtube"))
        assertFalse(ClockAppManager.isClockApp("com.whatsapp"))
        assertFalse(ClockAppManager.isClockApp("com.google.android.calendar"))
        assertFalse(ClockAppManager.isClockApp("com.spotify.music"))
    }

    @Test
    fun computeDayLabel_todayAndTomorrow() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 10, 0, 0)
        }
        val nowMs = nowCal.timeInMillis

        // Alarm today at 11:30
        val alarmToday = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
        }.timeInMillis
        assertEquals("Today", AlarmClockManager.computeDayLabel(alarmToday, nowMs))

        // Alarm tomorrow at 07:00
        val alarmTomorrow = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals("Tomorrow", AlarmClockManager.computeDayLabel(alarmTomorrow, nowMs))
    }

    @Test
    fun computeDayLabel_yearEndRollover() {
        // December 31
        val newYearsEve = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 31, 22, 0, 0)
        }.timeInMillis

        // January 1 next year
        val newYearsDay = Calendar.getInstance().apply {
            set(2027, Calendar.JANUARY, 1, 7, 0, 0)
        }.timeInMillis

        assertEquals("Tomorrow", AlarmClockManager.computeDayLabel(newYearsDay, newYearsEve))
    }

    @Test
    fun computeCountdownLabel_variousDurations() {
        val now = 1_000_000_000L

        // Less than 1 minute
        assertEquals("in < 1m", AlarmClockManager.computeCountdownLabel(now + 30_000L, now))

        // Minutes only
        assertEquals("in 45m", AlarmClockManager.computeCountdownLabel(now + 45 * 60_000L, now))

        // Hours only
        assertEquals("in 2h", AlarmClockManager.computeCountdownLabel(now + 2 * 3600_000L, now))

        // Hours and minutes
        assertEquals("in 7h 30m", AlarmClockManager.computeCountdownLabel(now + (7 * 3600_000L + 30 * 60_000L), now))

        // Days and hours
        assertEquals("in 2d 5h", AlarmClockManager.computeCountdownLabel(now + (48 * 3600_000L + 5 * 3600_000L), now))

        // Days only
        assertEquals("in 3d", AlarmClockManager.computeCountdownLabel(now + (3 * 24 * 3600_000L), now))
    }
}
