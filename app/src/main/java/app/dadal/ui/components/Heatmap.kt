package app.dadal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dadal.R
import app.dadal.domain.DayCell
import app.dadal.domain.DayState
import app.dadal.domain.HabitData
import app.dadal.domain.cell
import app.dadal.domain.weekStartOf
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitPalette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/** Draws one grid cell in the habit's color. */
fun DrawScope.drawDayCell(cell: DayCell, x: Float, y: Float, size: Float, color: Color, fade: Float = 1f, isToday: Boolean = false) {
    val radius = CornerRadius(size * 0.28f)
    val base = color.copy(alpha = 0.11f * fade)
    when (cell.state) {
        DayState.FUTURE -> Unit
        DayState.FREE -> drawCircle(color.copy(alpha = 0.38f * fade), radius = size * 0.13f, center = Offset(x + size / 2, y + size / 2))
        DayState.PAUSED -> drawRoundRect(
            color.copy(alpha = 0.35f * fade),
            topLeft = Offset(x + size * 0.25f, y + size * 0.45f),
            size = Size(size * 0.5f, size * 0.1f),
            cornerRadius = CornerRadius(size * 0.05f),
        )
        DayState.DONE, DayState.PARTIAL -> {
            drawRoundRect(base, Offset(x, y), Size(size, size), radius)
            drawRoundRect(color.copy(alpha = (0.32f + 0.68f * cell.fraction) * fade), Offset(x, y), Size(size, size), radius)
        }
        else -> {
            drawRoundRect(if (isToday) color.copy(alpha = 0.24f * fade) else base, Offset(x, y), Size(size, size), radius)
        }
    }
}

/** Weeks-by-days heatmap filling the width, newest week on the right (home cards). */
@Composable
fun CompactHeatmap(data: HabitData, today: Long, modifier: Modifier = Modifier, pitch: Dp = 12.5.dp) {
    val color = HabitPalette.get(data.habit.color)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapRatio = 0.3f
        val cols = max(1, (maxWidth / pitch).toInt())
        val cell = maxWidth / (cols + (cols - 1) * gapRatio)
        val gap = cell * gapRatio
        Canvas(Modifier.fillMaxWidth().height(cell * 7 + gap * 6)) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val endWeek = weekStartOf(today)
            val fadeCols = 4
            for (c in 0 until cols) {
                val weekStart = endWeek - (cols - 1 - c) * 7L
                val fade = if (c < fadeCols) (c + 1f) / (fadeCols + 1f) else 1f
                for (r in 0 until 7) {
                    val day = weekStart + r
                    drawDayCell(data.cell(day, today), c * (cellPx + gapPx), r * (cellPx + gapPx), cellPx, color, fade, day == today)
                }
            }
        }
    }
}

/** Scrollable year heatmap with month and weekday labels (habit detail). */
@Composable
fun DetailHeatmap(data: HabitData, today: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val months = context.resources.getStringArray(R.array.months_short)
    val weekdays = context.resources.getStringArray(R.array.weekdays_short)
    val color = HabitPalette.get(data.habit.color)
    val cell = 15.dp
    val gap = 4.dp
    val labelH = 22.dp
    val weeksSinceStart = ((weekStartOf(today) - weekStartOf(data.startDay)) / 7 + 1).toInt()
    val weeks = weeksSinceStart.coerceIn(30, 160)
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val labelStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 11.sp, color = Dadal.TextMuted)

    Row(modifier) {
        Column(Modifier.padding(top = labelH, end = 6.dp)) {
            for (r in 0 until 7) {
                Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                    if (r % 2 == 1) Text(weekdays[r], style = labelStyle)
                }
                if (r < 6) Spacer(Modifier.height(gap))
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(Modifier.width(cell * weeks + gap * (weeks - 1)).height(labelH + cell * 7 + gap * 6)) {
                val cellPx = cell.toPx()
                val gapPx = gap.toPx()
                val top = labelH.toPx()
                val endWeek = weekStartOf(today)
                var lastMonth = -1
                var lastLabelX = -1000f
                for (c in 0 until weeks) {
                    val weekStart = endWeek - (weeks - 1 - c) * 7L
                    val x = c * (cellPx + gapPx)
                    val month = LocalDate.ofEpochDay(weekStart + 6).monthValue
                    if (month != lastMonth) {
                        if (x - lastLabelX > cellPx * 3.2f && c < weeks - 1) {
                            drawText(measurer, months[month - 1], topLeft = Offset(x, 0f), style = labelStyle)
                            lastLabelX = x
                        }
                        lastMonth = month
                    }
                    for (r in 0 until 7) {
                        val day = weekStart + r
                        drawDayCell(data.cell(day, today), x, top + r * (cellPx + gapPx), cellPx, color, 1f, day == today)
                    }
                }
            }
        }
    }
}

/** A small month grid for the compact home view: 7 columns, a row per week. */
@Composable
fun MiniMonth(data: HabitData, today: Long, month: YearMonth, modifier: Modifier = Modifier) {
    val color = HabitPalette.get(data.habit.color)
    val first = month.atDay(1).toEpochDay()
    val last = month.atEndOfMonth().toEpochDay()
    val gridStart = weekStartOf(first)
    val rows = ((last - gridStart) / 7 + 1).toInt()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapRatio = 0.45f
        val cell = maxWidth / (7 + 6 * gapRatio)
        val gap = cell * gapRatio
        Canvas(Modifier.fillMaxWidth().height(cell * rows + gap * (rows - 1))) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            for (w in 0 until rows) for (d in 0 until 7) {
                val day = gridStart + w * 7 + d
                if (day < first || day > last) continue
                drawDayCell(data.cell(day, today), d * (cellPx + gapPx), w * (cellPx + gapPx), cellPx, color, 1f, day == today)
            }
        }
    }
}

/** Month calendar on the habit page. Tap to toggle a day, press and hold for a note. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendar(
    data: HabitData,
    today: Long,
    month: YearMonth,
    noteDays: Set<Long>,
    onTap: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val weekdays = context.resources.getStringArray(R.array.weekdays_short)
    val color = HabitPalette.get(data.habit.color)
    val first = month.atDay(1).toEpochDay()
    val last = month.atEndOfMonth().toEpochDay()
    val gridStart = weekStartOf(first)
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    style = TextStyle(fontFamily = Dadal.Mono, fontSize = 14.sp, color = Dadal.TextMuted),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (w in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = gridStart + w * 7 + d
                    val inMonth = day in first..last
                    val cell = data.cell(day, today)
                    val isToday = day == today
                    val shape = RoundedCornerShape(16.dp)
                    val fill = when (cell.state) {
                        DayState.DONE -> color
                        DayState.PARTIAL -> color.copy(alpha = 0.25f + 0.4f * cell.fraction)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        cell.state == DayState.DONE -> Color(0xE6000000)
                        day > today -> Dadal.TextFaint
                        !inMonth -> Dadal.TextFaint
                        cell.state == DayState.FREE || cell.state == DayState.PAUSED -> Dadal.TextMuted
                        else -> Dadal.Text
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(shape)
                            .background(fill.copy(alpha = if (inMonth) fill.alpha else fill.alpha * 0.4f))
                            .then(if (isToday) Modifier.border(2.dp, color, shape) else Modifier)
                            .combinedClickable(
                                enabled = day <= today,
                                onClick = { onTap(day) },
                                onLongClick = { onLongPress(day) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                            style = TextStyle(
                                fontFamily = Dadal.Mono,
                                fontSize = 16.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = textColor,
                        )
                        if (day in noteDays) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = (-5).dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (cell.state == DayState.DONE) Color(0xCC000000) else color)
                            )
                        }
                        if (cell.state == DayState.PAUSED && inMonth) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = (-6).dp)
                                    .width(12.dp)
                                    .height(2.dp)
                                    .background(Dadal.TextMuted)
                            )
                        }
                    }
                }
            }
        }
    }
}
