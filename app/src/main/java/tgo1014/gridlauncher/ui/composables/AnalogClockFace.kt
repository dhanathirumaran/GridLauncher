package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimalist monochrome analog clock face rendered on a Compose Canvas.
 *
 * - Battery-friendly: LaunchedEffect loop syncs every second while visible,
 *   automatically cancelling when the home screen is navigated away from.
 * - Renders a circular outer dial ring, 12 hour ticks (major ticks at 12, 3, 6, 9),
 *   dynamic hour and minute hands computed from device time, and a center hardware pin.
 */
@Composable
fun AnalogClockFace(
    clockColor: Color,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = Calendar.getInstance()
            val nowMs = System.currentTimeMillis()
            val delayMs = 1000L - (nowMs % 1000L)
            delay(delayMs)
        }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (minOf(size.width, size.height) / 2f) - 1.5.dp.toPx()
        if (radius <= 0) return@Canvas

        val center = Offset(cx, cy)

        // 1. Dial background & ring
        drawCircle(
            color = clockColor.copy(alpha = 0.08f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = clockColor.copy(alpha = 0.22f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 2. 12 Hour Ticks
        val tickOuterR = radius - 1.5.dp.toPx()
        for (i in 0 until 12) {
            val angleRad = Math.toRadians((i * 30.0)).toFloat()
            val isMajor = i % 3 == 0 // 12, 3, 6, 9
            val tickLength = if (isMajor) 4.5.dp.toPx() else 2.5.dp.toPx()
            val tickInnerR = tickOuterR - tickLength

            val sinA = sin(angleRad)
            val cosA = cos(angleRad)

            val pStart = Offset(cx + tickOuterR * sinA, cy - tickOuterR * cosA)
            val pEnd = Offset(cx + tickInnerR * sinA, cy - tickInnerR * cosA)

            drawLine(
                color = clockColor.copy(alpha = if (isMajor) 0.50f else 0.28f),
                start = pStart,
                end = pEnd,
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 3. Hands calculation
        val hours = currentTime.get(Calendar.HOUR)
        val minutes = currentTime.get(Calendar.MINUTE)
        val seconds = currentTime.get(Calendar.SECOND)

        val hourAngle = Math.toRadians(((hours % 12 + minutes / 60f + seconds / 3600f) * 30.0)).toFloat()
        val minuteAngle = Math.toRadians(((minutes + seconds / 60f) * 6.0)).toFloat()

        // Hour Hand
        val hourR = radius * 0.52f
        val hourEnd = Offset(
            cx + hourR * sin(hourAngle),
            cy - hourR * cos(hourAngle)
        )
        drawLine(
            color = clockColor,
            start = center,
            end = hourEnd,
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Minute Hand
        val minuteR = radius * 0.74f
        val minuteEnd = Offset(
            cx + minuteR * sin(minuteAngle),
            cy - minuteR * cos(minuteAngle)
        )
        drawLine(
            color = clockColor,
            start = center,
            end = minuteEnd,
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Center Pin
        drawCircle(
            color = clockColor,
            radius = 2.5.dp.toPx(),
            center = center
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = 1.dp.toPx(),
            center = center
        )
    }
}
