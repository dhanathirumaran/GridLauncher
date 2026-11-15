package tgo1014.gridlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import tgo1014.gridlauncher.data.smarttile.WeatherManager

class WeatherManagerTest {

    @Test
    fun calculateNextRainSummary_whenRainingNow_detectsStopTime() {
        val times = listOf(
            "2026-11-15T13:00",
            "2026-11-15T14:00",
            "2026-11-15T15:00"
        )
        val probs = listOf(70, 50, 10)
        val precips = listOf(1.5, 0.8, 0.0)

        val result = WeatherManager.calculateNextRainSummary(times, probs, precips)
        assertEquals("Raining now • stops ~3 PM", result)
    }

    @Test
    fun calculateNextRainSummary_whenRainingContinously_returnsRainingNow() {
        val times = listOf(
            "2026-11-15T13:00",
            "2026-11-15T14:00"
        )
        val probs = listOf(80, 85)
        val precips = listOf(2.0, 3.5)

        val result = WeatherManager.calculateNextRainSummary(times, probs, precips)
        assertEquals("Raining now", result)
    }

    @Test
    fun calculateNextRainSummary_whenRainLaterToday_formatsHourAndProbability() {
        val times = listOf(
            "2026-11-15T12:00",
            "2026-11-15T13:00",
            "2026-11-15T14:00",
            "2026-11-15T15:00"
        )
        val probs = listOf(0, 5, 20, 75)
        val precips = listOf(0.0, 0.0, 0.0, 1.2)

        val result = WeatherManager.calculateNextRainSummary(times, probs, precips)
        assertEquals("Rain at 3 PM (75%)", result)
    }

    @Test
    fun calculateNextRainSummary_whenRainTomorrow_includesTomorrow() {
        val times = listOf(
            "2026-11-15T22:00",
            "2026-11-15T23:00",
            "2026-11-16T08:00"
        )
        val probs = listOf(0, 10, 60)
        val precips = listOf(0.0, 0.0, 0.8)

        val result = WeatherManager.calculateNextRainSummary(times, probs, precips)
        assertEquals("Rain tomorrow, 8 AM (60%)", result)
    }

    @Test
    fun calculateNextRainSummary_whenNoRain_returnsNoRainExpected() {
        val times = listOf(
            "2026-11-15T12:00",
            "2026-11-15T13:00"
        )
        val probs = listOf(0, 15)
        val precips = listOf(0.0, 0.0)

        val result = WeatherManager.calculateNextRainSummary(times, probs, precips)
        assertEquals("No rain expected", result)
    }

    @Test
    fun calculateNextRainSummary_whenEmptyData_returnsNoRainExpected() {
        val result = WeatherManager.calculateNextRainSummary(emptyList(), emptyList(), emptyList())
        assertEquals("No rain expected", result)
    }
}
