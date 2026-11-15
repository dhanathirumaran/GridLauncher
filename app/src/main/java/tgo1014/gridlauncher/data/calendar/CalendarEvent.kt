package tgo1014.gridlauncher.data.calendar

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean = false,
    val color: Int? = null,
    val formattedTime: String = "",
)
