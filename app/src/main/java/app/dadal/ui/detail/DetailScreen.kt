package app.dadal.ui.detail

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dadal.R
import app.dadal.data.TrackingType
import app.dadal.domain.HabitStats
import app.dadal.domain.isQuit
import app.dadal.formatNum
import app.dadal.monthYear
import app.dadal.scheduleText
import app.dadal.shortDate
import app.dadal.streakText
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.ChoiceDialog
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.ConfirmDialog
import app.dadal.ui.components.DetailHeatmap
import app.dadal.ui.components.IconBadge
import app.dadal.ui.components.MonthCalendar
import app.dadal.ui.components.NoteDialog
import app.dadal.ui.components.Pill
import app.dadal.ui.components.ValueDialog
import app.dadal.ui.components.habitCard
import app.dadal.ui.components.surfaceCard
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitPalette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun DetailScreen(habitId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val c = LocalContainer.current
    val vm: DetailViewModel = viewModel(key = "detail-$habitId") { DetailViewModel(c, habitId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    val data = state.data ?: return Box(Modifier.fillMaxSize().background(Dadal.Background))
    val habit = data.habit
    val color = HabitPalette.get(habit.color)
    val stats = state.stats
    val today = state.today
    val t = today.toEpochDay()

    var monthIndex by rememberSaveable { mutableStateOf(0) } // 0 = this month, 1 = last month…
    val month = YearMonth.from(today).minusMonths(monthIndex.toLong())
    var noteDay by remember { mutableStateOf<Long?>(null) }
    var valueDay by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pauseDialog by remember { mutableStateOf(false) }
    val noteDays = state.notes.map { it.day }.toSet()
    val pausedNow = data.pauseOn(t)

    LazyColumn(
        Modifier.fillMaxSize().background(Dadal.Background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                IconBadge(habit.icon, habit.color, size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (habit.description.isNotBlank()) {
                        Text(habit.description, style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Rounded.Edit, stringResource(R.string.edit), onEdit, size = 46.dp)
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleButton(Icons.Rounded.Settings, stringResource(R.string.more), { menu = true }, size = 46.dp)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Dadal.SurfaceHigh) {
                        if (pausedNow != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.resume)) },
                                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                                onClick = { menu = false; vm.resume() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pause_habit)) },
                                leadingIcon = { Icon(Icons.Rounded.Pause, null) },
                                onClick = { menu = false; pauseDialog = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(if (habit.archived) R.string.unarchive else R.string.archive)) },
                            leadingIcon = { Icon(if (habit.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                            onClick = { menu = false; vm.archive(!habit.archived); if (!habit.archived) onBack() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = Dadal.Slip) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Dadal.Slip) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        }

        item {
            Box(Modifier.fillMaxWidth().habitCard(habit.color, 30.dp).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)) {
                DetailHeatmap(data, t)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    streakText(context, stats.current, stats.unit),
                    onClick = {},
                    icon = Icons.Rounded.LocalFireDepartment,
                    selected = true,
                    accent = color,
                    textStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Pill(
                    scheduleText(context, habit),
                    onClick = onEdit,
                    icon = Icons.Rounded.Repeat,
                    selected = true,
                    accent = color,
                    textStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f, fill = false),
                )
                CircleButton(
                    if (habit.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(R.string.favorite),
                    { vm.setFavorite(!habit.favorite) },
                    size = 46.dp,
                    tint = if (habit.favorite) Dadal.Slip else Dadal.Text,
                )
            }
        }

        if (pausedNow != null) {
            item {
                val until = pausedNow.endDay
                Row(
                    Modifier.fillMaxWidth().surfaceCard(22.dp).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Pause, null, tint = color)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (until == null) stringResource(R.string.paused_indefinitely)
                        else stringResource(R.string.paused_until, shortDate(context, LocalDate.ofEpochDay(until))),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Pill(stringResource(R.string.resume), onClick = { vm.resume() }, selected = true, accent = color)
                }
            }
        }

        if (habit.tracking != TrackingType.YES_NO) {
            item { TodayValueCard(habit.color, stats, habit.target, habit.unit, habit.isQuit, onMinus = {
                vm.setValue(t, (stats.todayValue - 1).coerceAtLeast(0.0))
            }, onPlus = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.setValue(t, stats.todayValue + 1)
            }, onEdit = { valueDay = t }) }
        }

        item { StatTiles(stats) }

        item {
            Column(Modifier.fillMaxWidth().surfaceCard(30.dp).padding(12.dp)) {
                MonthCalendar(
                    data, t, month, noteDays,
                    onTap = { day ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (habit.tracking == TrackingType.YES_NO) vm.toggle(day) else valueDay = day
                    },
                    onLongPress = { day -> noteDay = day },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Pill(
                            monthYear(context, month.atDay(1), short = true),
                            onClick = { monthMenu = true },
                            textStyle = TextStyle(fontFamily = Dadal.Mono, fontSize = 15.sp),
                            trailingIcon = Icons.Rounded.KeyboardArrowDown,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }, containerColor = Dadal.SurfaceHigh) {
                            (0 until 24).forEach { i ->
                                DropdownMenuItem(
                                    text = { Text(monthYear(context, YearMonth.from(today).minusMonths(i.toLong()).atDay(1))) },
                                    onClick = { monthIndex = i; monthMenu = false },
                                )
                            }
                        }
                    }
                    Pill(stringResource(R.string.today), onClick = { monthIndex = 0 })
                    CircleButton(Icons.Rounded.ChevronLeft, null, { monthIndex++ }, size = 46.dp)
                    CircleButton(Icons.Rounded.ChevronRight, null, { if (monthIndex > 0) monthIndex-- }, size = 46.dp, tint = if (monthIndex > 0) Dadal.Text else Dadal.TextFaint)
                }
                Text(
                    stringResource(R.string.hold_for_note),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dadal.TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        if (state.notes.isEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().surfaceCard(30.dp).clickable { noteDay = t }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.EditNote, null, tint = color)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.no_notes), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.notes_placeholder), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.add_note), tint = color)
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    CircleButton(Icons.Rounded.Add, stringResource(R.string.add_note), { noteDay = t }, size = 42.dp, tint = color)
                }
            }
            items(state.notes, key = { it.day }) { note ->
                Column(Modifier.fillMaxWidth().surfaceCard(22.dp).clickable { noteDay = note.day }.padding(18.dp)) {
                    Text(
                        shortDate(context, LocalDate.ofEpochDay(note.day)).uppercase(),
                        style = TextStyle(fontFamily = Dadal.Mono, fontSize = 12.sp, letterSpacing = 1.5.sp),
                        color = color,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(note.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    noteDay?.let { day ->
        val existing = state.notes.firstOrNull { it.day == day }
        NoteDialog(
            date = LocalDate.ofEpochDay(day),
            initial = existing?.text ?: "",
            onDismiss = { noteDay = null },
            onSave = { vm.saveNote(day, it); noteDay = null },
            onDelete = existing?.let { { vm.deleteNote(day); noteDay = null } },
        )
    }
    valueDay?.let { day ->
        ValueDialog(
            habit, LocalDate.ofEpochDay(day), data.value(day),
            onDismiss = { valueDay = null },
            onSave = { vm.setValue(day, it); valueDay = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_habit_title),
            text = stringResource(R.string.delete_habit_text, habit.name),
            confirm = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
        )
    }
    if (pauseDialog) {
        ChoiceDialog(
            title = stringResource(R.string.pause_for),
            options = listOf(
                3 to stringResource(R.string.pause_3_days),
                7 to stringResource(R.string.pause_1_week),
                14 to stringResource(R.string.pause_2_weeks),
                30 to stringResource(R.string.pause_1_month),
                0 to stringResource(R.string.pause_until_resume),
            ),
            selected = null,
            onDismiss = { pauseDialog = false },
            onPick = { days -> pauseDialog = false; vm.pause(days.takeIf { it > 0 }) },
        )
    }
}

@Composable
private fun TodayValueCard(
    colorIndex: Int,
    stats: HabitStats,
    target: Double,
    unit: String,
    quit: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onEdit: () -> Unit,
) {
    val color = HabitPalette.get(colorIndex)
    Row(
        Modifier.fillMaxWidth().habitCard(colorIndex, 24.dp).clickable(onClick = onEdit).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.today).uppercase(), style = TextStyle(fontFamily = Dadal.Mono, fontSize = 12.sp, letterSpacing = 1.5.sp), color = Dadal.TextMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatNum(stats.todayValue), style = TextStyle(fontFamily = Dadal.Mono, fontSize = 30.sp, fontWeight = FontWeight.Bold), color = color)
                Text(
                    " / ${formatNum(target)} $unit".trimEnd(),
                    style = TextStyle(fontFamily = Dadal.Mono, fontSize = 16.sp),
                    color = Dadal.TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                stringResource(if (quit) R.string.daily_limit else R.string.daily_goal),
                style = MaterialTheme.typography.bodyMedium,
                color = Dadal.TextMuted,
            )
        }
        CircleButton(Icons.Rounded.Remove, null, onMinus, size = 48.dp)
        Spacer(Modifier.width(10.dp))
        CircleButton(Icons.Rounded.Add, null, onPlus, size = 48.dp, container = color.copy(alpha = 0.25f), tint = color)
    }
}

@Composable
private fun StatTiles(stats: HabitStats) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            stringResource(R.string.stat_best) to streakText(context, stats.best, stats.unit),
            stringResource(R.string.stat_consistency) to "${(stats.consistency * 100).roundToInt()}%",
            stringResource(R.string.stat_total) to stats.total.toString(),
        ).forEach { (label, value) ->
            Column(Modifier.weight(1f).surfaceCard(22.dp).padding(14.dp)) {
                Text(label.uppercase(), style = TextStyle(fontFamily = Dadal.Mono, fontSize = 10.sp, letterSpacing = 0.6.sp), color = Dadal.TextMuted, maxLines = 2, minLines = 2)
                Spacer(Modifier.height(6.dp))
                Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
