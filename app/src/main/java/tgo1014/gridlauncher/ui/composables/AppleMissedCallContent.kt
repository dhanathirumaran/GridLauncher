package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import tgo1014.gridlauncher.data.notification.NotificationSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppleMissedCallContent(
    iconModel: Any?,
    liveNotifications: List<NotificationSender>,
    textColor: Color,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
) {
    val appleRed = Color(0xFFFF3B30)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconModifier = Modifier.size(36.dp)
        if (isPreview) {
            Image(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = iconModifier,
            )
        } else {
            AsyncImage(
                model = iconModel,
                contentDescription = null,
                modifier = iconModifier,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val totalCallsCount = liveNotifications.sumOf { it.count }
            val isSingleCaller = liveNotifications.size == 1

            // Header Row: Red dot indicator + "MISSED CALL" label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(appleRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val headerTitle = if (totalCallsCount > 1) {
                    "MISSED CALL ($totalCallsCount)"
                } else {
                    "MISSED CALL"
                }
                Text(
                    text = headerTitle,
                    color = appleRed,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSingleCaller) {
                val caller = liveNotifications.first()
                Text(
                    text = caller.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = formatRelativeTime(caller.timestamp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            } else {
                // Multiple different callers: list up to 2
                val topCallers = liveNotifications.take(2)
                topCallers.forEach { caller ->
                    val callerSuffix = if (caller.count > 1) " (${caller.count})" else ""
                    val timeStr = formatRelativeTime(caller.timestamp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${caller.name}$callerSuffix",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeStr,
                            maxLines = 1,
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
                if (liveNotifications.size > 2) {
                    Text(
                        text = "+${liveNotifications.size - 2} more",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
