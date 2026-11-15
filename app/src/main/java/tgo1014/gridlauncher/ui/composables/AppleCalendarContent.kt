package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.data.calendar.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AppleCalendarContent(
    calendarEvents: List<CalendarEvent>,
    isPermissionGranted: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    maxEvents: Int = 2
) {
    val (dayOfWeek, dayOfMonth) = remember(calendarEvents, System.currentTimeMillis() / 60_000L) {
        getCalendarDateBadge()
    }

    val appleRed = Color(0xFFFF3B30)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Apple Date Badge on the Left
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(48.dp)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = dayOfWeek,
                color = appleRed,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
            Text(
                text = dayOfMonth,
                color = textColor,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Right side: Events list or status
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.Center
        ) {
            when {
                !isPermissionGranted -> {
                    Text(
                        text = "Calendar Access",
                        color = textColor,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to show today's schedule",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                calendarEvents.isEmpty() -> {
                    Text(
                        text = "No more events today",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                else -> {
                    val eventsToShow = calendarEvents.take(maxEvents)
                    eventsToShow.forEach { event ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            val barColor = event.color?.let { Color(it) } ?: appleRed
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(barColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = event.formattedTime,
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (calendarEvents.size > maxEvents) {
                        Text(
                            text = "+${calendarEvents.size - maxEvents} more",
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(start = 11.5.dp, top = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getCalendarDateBadge(calendar: Calendar = Calendar.getInstance()): Pair<String, String> {
    val dow = SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time).uppercase()
    val dom = SimpleDateFormat("d", Locale.getDefault()).format(calendar.time)
    return dow to dom
}

fun getCalendarDateBadge(timeInMillis: Long): Pair<String, String> {
    val cal = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
    return getCalendarDateBadge(cal)
}
