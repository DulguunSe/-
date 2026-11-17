package app.dadal.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dadal.AppContainer
import app.dadal.R
import app.dadal.domain.HabitCalculator
import app.dadal.domain.PerfectSummary
import app.dadal.domain.dayIndexOf
import app.dadal.domain.isDayBased
import app.dadal.domain.isQuit
import app.dadal.domain.isSuccess
import app.dadal.domain.weekStartOf
import app.dadal.shortDate
import app.dadal.streakText
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.EmptyState
import app.dadal.ui.components.IconBadge
import app.dadal.ui.components.StreakBadge
import app.dadal.ui.components.surfaceCard
import app.dadal.ui.home.HabitRow
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

data class StatsState(
    val loaded: Boolean = false,
    val rows: List<HabitRow> = emptyList(),
    val doneToday: Int = 0,
    val shownToday: Int = 0,
    val totalCheckins: Int = 0,
    val avgConsistency: Float = 0f,
    val perfect: PerfectSummary = PerfectSummary(),
    /** Week start → successful check-ins, oldest first. */
    val weekly: List<Pair<LocalDate, Int>> = emptyList(),
    /** Completion rate by weekday (Mon..Sun), null when nothing was due. */
    val weekday: List<Float?> = List(7) { null },
)

class StatsViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<StatsState> = combine(c.repo.snapshotFlow, c.today) { snap, today ->
        val t = today.toEpochDay()
        val rows = snap.active.map { h -> val d = snap.data(h); HabitRow(d, HabitCalculator.stats(d, t)) }
        val allDatas = snap.habits.map { snap.data(it) }
        val shown = rows.filter { HabitCalculator.showToday(it.data, it.stats) }

        val endWeek = weekStartOf(t)
        val weekly = (11 downTo 0).map { i ->
            val ws = endWeek - i * 7L
            val count = allDatas.filter { !it.habit.isQuit }.sumOf { d ->
                d.values.count { (day, v) -> day in ws..(ws + 6) && day <= t && d.habit.isSuccess(v) }
            }
            LocalDate.ofEpochDay(ws) to count
        }

        val due = IntArray(7)
        val done = IntArray(7)
        rows.map { it.data }.filter { !it.habit.isQuit && it.habit.isDayBased }.forEach { d ->
            var day = max(d.startDay, t - 84)
            while (day < t) {
                if (!d.isPaused(day) && d.isScheduled(day)) {
                    val idx = dayIndexOf(day)
                    due[idx]++
                    if (d.isSuccess(day)) done[idx]++
                }
                day++
            }
        }

        StatsState(
            loaded = true,
            rows = rows.sortedByDescending { it.stats.consistency },
            doneToday = shown.count { it.stats.doneToday },
            shownToday = shown.size,
            totalCheckins = allDatas.filter { !it.habit.isQuit }.sumOf { d -> d.values.count { (day, v) -> day <= t && d.habit.isSuccess(v) } },
            avgConsistency = if (rows.isEmpty()) 0f else rows.map { it.stats.consistency }.average().toFloat(),
            perfect = HabitCalculator.perfectSummary(rows.map { it.data }, t),
            weekly = weekly,
            weekday = (0..6).map { if (due[it] == 0) null else done[it].toFloat() / due[it] },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())
}

@Composable
fun StatsScreen(onBack: () -> Unit, onOpenHabit: (Long) -> Unit) {
    val c = LocalContainer.current
    val vm: StatsViewModel = viewModel { StatsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val weekdayNames = context.resources.getStringArray(R.array.weekdays_full)

    LazyColumn(
        Modifier.fillMaxSize().background(Dadal.Background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.statistics), style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (state.loaded && state.rows.isEmpty()) {
            item { EmptyState(Icons.Rounded.Insights, stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_text)) }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.stat_perfect_streak), Modifier.weight(1f)) {
                    StreakBadge(state.perfect.current, fontSize = 26, active = state.perfect.current > 0)
                }
                StatTile(stringResource(R.string.stat_today), Modifier.weight(1f)) {
                    BigNumber("${state.doneToday}/${state.shownToday}")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.stat_checkins), Modifier.weight(1f)) { BigNumber(state.totalCheckins.toString()) }
                StatTile(stringResource(R.string.stat_avg_consistency), Modifier.weight(1f)) {
                    BigNumber("${(state.avgConsistency * 100).roundToInt()}%")
                }
            }
        }

        item {
            ChartCard(stringResource(R.string.chart_weekly)) {
                BarChart(
                    values = state.weekly.map { it.second.toFloat() },
                    labels = state.weekly.map { shortDate(context, it.first) },
                    valueText = { it.roundToInt().toString() },
                    axisLabelEvery = 4,
                )
            }
        }

        item {
            val rates = state.weekday
            ChartCard(stringResource(R.string.chart_weekday)) {
                BarChart(
                    values = rates.map { it ?: 0f },
                    labels = context.resources.getStringArray(R.array.weekdays_two).toList(),
                    valueText = { "${(it * 100).roundToInt()}%" },
                    axisLabelEvery = 1,
                    maxValue = 1f,
                    fullLabels = weekdayNames.toList(),
                )
            }
        }

        item {
            val insights = buildList {
                val rates = state.weekday
                val known = rates.withIndex().filter { it.value != null }
                if (known.size >= 2) {
                    val best = known.maxBy { it.value!! }
                    val worst = known.minBy { it.value!! }
                    add(stringResource(R.string.insight_best_day, weekdayNames[best.index], (best.value!! * 100).roundToInt()))
                    if (best.index != worst.index) add(stringResource(R.string.insight_worst_day, weekdayNames[worst.index], (worst.value!! * 100).roundToInt()))
                }
                state.rows.maxByOrNull { it.stats.consistency }?.takeIf { it.stats.consistency > 0 }?.let {
                    add(stringResource(R.string.insight_most_consistent, it.habit.name, (it.stats.consistency * 100).roundToInt()))
                }
                state.rows.maxByOrNull { it.stats.current }?.takeIf { it.stats.current > 0 }?.let {
                    add(stringResource(R.string.insight_longest, it.habit.name, streakText(context, it.stats.current, it.stats.unit)))
                }
                if (state.perfect.best > 0) add(stringResource(R.string.insight_perfect_best, state.perfect.best, state.perfect.perfectDays))
            }
            if (insights.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().surfaceCard(24.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.insights), style = MaterialTheme.typography.titleMedium)
                    insights.forEach { line ->
                        Row {
                            Text("•  ", color = Dadal.Primary)
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
                        }
                    }
                }
            }
        }

        item { Text(stringResource(R.string.habits), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        items(state.rows, key = { it.habit.id }) { row ->
            val color = HabitPalette.get(row.habit.color)
            Column(Modifier.fillMaxWidth().surfaceCard(22.dp).clickable { onOpenHabit(row.habit.id) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(row.habit.icon, row.habit.color, size = 40.dp, corner = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.best_streak_short, streakText(context, row.stats.best, row.stats.unit)),
                            style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted,
                        )
                    }
                    StreakBadge(row.stats.current)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(row.stats.consistency.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${(row.stats.consistency * 100).roundToInt()}%",
                        style = TextStyle(fontFamily = Dadal.Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = Dadal.Text,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.surfaceCard(22.dp).padding(16.dp)) {
        Text(label.uppercase(), style = TextStyle(fontFamily = Dadal.Mono, fontSize = 11.sp, letterSpacing = 1.2.sp), color = Dadal.TextMuted, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(text, style = TextStyle(fontFamily = Dadal.Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold), color = Dadal.Text)
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().surfaceCard(24.dp).padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.chart_tap_hint), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/**
 * Single-series bar chart: one hue, thin bars with 4dp rounded tops on a shared baseline, a recessive
 * baseline, value labels only on the highest bar and on the bar you tap.
 */
@Composable
private fun BarChart(
    values: List<Float>,
    labels: List<String>,
    valueText: (Float) -> String,
    axisLabelEvery: Int,
    maxValue: Float? = null,
    fullLabels: List<String>? = null,
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val color = Dadal.Primary
    val labelStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 11.sp, color = Dadal.TextMuted)
    val valueStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Dadal.Text)
    val maxIndex = values.indices.maxByOrNull { values[it] }
    val top = max(maxValue ?: 0f, values.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(values) {
                    detectTapGestures { pos ->
                        if (values.isEmpty()) return@detectTapGestures
                        val slot = size.width / values.size
                        val i = (pos.x / slot).toInt().coerceIn(0, values.size - 1)
                        selected = if (selected == i) null else i
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            val labelSpace = 22.dp.toPx()
            val valueSpace = 20.dp.toPx()
            val chartH = size.height - labelSpace - valueSpace
            val slot = size.width / values.size
            val gap = max(2.dp.toPx(), slot * 0.28f)
            val barW = slot - gap
            val baseY = valueSpace + chartH
            drawLine(Dadal.Outline, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / top) * chartH
                val x = i * slot + gap / 2
                val isSel = selected == i
                val alpha = if (selected == null || isSel) 1f else 0.35f
                if (h > 0f) {
                    val r = minOf(4.dp.toPx(), barW / 2, h)
                    val path = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = x, top = baseY - h, right = x + barW, bottom = baseY,
                                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                            )
                        )
                    }
                    drawPath(path, color.copy(alpha = alpha))
                } else {
                    drawRect(Dadal.TextFaint, topLeft = Offset(x, baseY - 2.dp.toPx()), size = Size(barW, 2.dp.toPx()))
                }
                if (isSel || (selected == null && i == maxIndex && v > 0f)) {
                    val txt = measurer.measure(valueText(v), valueStyle)
                    val tx = (x + barW / 2 - txt.size.width / 2).coerceIn(0f, size.width - txt.size.width)
                    drawText(txt, topLeft = Offset(tx, baseY - h - txt.size.height - 4.dp.toPx()))
                }
                if (i % axisLabelEvery == 0 || i == values.size - 1) {
                    val txt = measurer.measure(labels[i], labelStyle)
                    if (axisLabelEvery == 1 || i % axisLabelEvery == 0) {
                        val tx = (x + barW / 2 - txt.size.width / 2).coerceIn(0f, size.width - txt.size.width)
                        drawText(txt, topLeft = Offset(tx, baseY + 6.dp.toPx()))
                    }
                }
            }
        }
        selected?.let { i ->
            Text(
                "${(fullLabels ?: labels)[i]}: ${valueText(values[i])}",
                style = MaterialTheme.typography.bodyMedium,
                color = Dadal.Text,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
