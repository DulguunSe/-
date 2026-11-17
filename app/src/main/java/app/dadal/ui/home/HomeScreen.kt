package app.dadal.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dadal.R
import app.dadal.data.Habit
import app.dadal.data.SortMode
import app.dadal.data.TrackingType
import app.dadal.domain.DayState
import app.dadal.domain.cell
import app.dadal.domain.isQuit
import app.dadal.monthYear
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.CompactHeatmap
import app.dadal.ui.components.EmptyState
import app.dadal.ui.components.HabitCheckButton
import app.dadal.ui.components.IconBadge
import app.dadal.ui.components.MiniMonth
import app.dadal.ui.components.Pill
import app.dadal.ui.components.PrimaryButton
import app.dadal.ui.components.StreakBadge
import app.dadal.ui.components.ValueDialog
import app.dadal.ui.components.habitCard
import app.dadal.ui.components.surfaceCard
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitIcons
import app.dadal.ui.theme.HabitPalette
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    onOpenHabit: (Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onBadges: () -> Unit,
    onArchived: () -> Unit,
) {
    val c = LocalContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var valueFor by remember { mutableStateOf<Pair<Habit, LocalDate>?>(null) }
    val today = state.today

    val onToggle: (Habit, LocalDate) -> Unit = { habit, date ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.quickToggle(habit.id, date.toEpochDay())
    }
    val onValue: (Habit, LocalDate) -> Unit = { habit, date ->
        if (habit.tracking != TrackingType.YES_NO) valueFor = habit to date
    }

    Box(Modifier.fillMaxSize().background(Dadal.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(state, vm, onSettings, onStats, onBadges, onAdd, onArchived)
            if (state.categories.isNotEmpty()) CategoryRow(state, vm)
            when {
                !state.loaded -> Unit
                state.activeCount == 0 -> EmptyState(
                    Icons.Rounded.Checklist,
                    stringResource(R.string.empty_title),
                    stringResource(R.string.empty_text),
                    Modifier.padding(top = 48.dp),
                ) { PrimaryButton(stringResource(R.string.empty_action), onAdd, Modifier.widthIn(max = 280.dp)) }
                state.rows.isEmpty() -> EmptyState(
                    Icons.Rounded.Check,
                    stringResource(R.string.empty_filtered_title),
                    stringResource(R.string.empty_filtered_text),
                    Modifier.padding(top = 48.dp),
                )
                state.view == 1 -> WeekListView(state, onOpenHabit, onToggle, onValue)
                state.view == 2 -> CompactMonthView(state, vm, onOpenHabit, onToggle)
                else -> CardsView(state, onOpenHabit, onToggle, onValue)
            }
        }
        if (state.activeCount > 0) {
            ViewSwitcher(state.view, vm::setView, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp))
        }
    }

    valueFor?.let { (habit, date) ->
        val row = state.rows.firstOrNull { it.habit.id == habit.id }
        ValueDialog(
            habit = habit,
            date = date,
            initial = row?.data?.value(date.toEpochDay()) ?: 0.0,
            onDismiss = { valueFor = null },
            onSave = { v -> vm.setValue(habit.id, date.toEpochDay(), v); valueFor = null },
        )
    }
}

@Composable
private fun TopBar(
    state: HomeState,
    vm: HomeViewModel,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onBadges: () -> Unit,
    onAdd: () -> Unit,
    onArchived: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleButton(Icons.Rounded.Settings, stringResource(R.string.settings), onSettings)
        // Duolingo-style streak counter: consecutive perfect days.
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (state.perfect.current > 0) Dadal.Flame.copy(alpha = 0.14f) else Dadal.SurfaceHigh)
                .clickable(onClick = onStats)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreakBadge(state.perfect.current, active = state.perfect.todayComplete == true, fontSize = 16)
        }
        Spacer(Modifier.weight(1f))
        CircleButton(Icons.Rounded.EmojiEvents, stringResource(R.string.trophies), onBadges)
        Box {
            CircleButton(Icons.Rounded.FilterList, stringResource(R.string.sort_filter), { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Dadal.SurfaceHigh) {
                Text(stringResource(R.string.sort_by), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Dadal.TextMuted, style = MaterialTheme.typography.labelLarge)
                listOf(
                    SortMode.DEFAULT to R.string.sort_default,
                    SortMode.NAME to R.string.sort_name,
                    SortMode.STREAK to R.string.sort_streak,
                ).forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { RadioButton(selected = state.sort == mode, onClick = null) },
                        onClick = { vm.setSort(mode); menu = false },
                    )
                }
                HorizontalDivider(color = Dadal.Outline)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hide_done)) },
                    leadingIcon = { Checkbox(checked = state.hideDone, onCheckedChange = null) },
                    onClick = { vm.setHideDone(!state.hideDone) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.archived_habits)) },
                    leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                    onClick = { menu = false; onArchived() },
                )
            }
        }
        CircleButton(Icons.Rounded.BarChart, stringResource(R.string.statistics), onStats)
        CircleButton(Icons.Rounded.Add, stringResource(R.string.new_habit), onAdd, container = Dadal.Primary, tint = Color.White)
    }
}

@Composable
private fun CategoryRow(state: HomeState, vm: HomeViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item {
            Pill(
                text = stringResource(R.string.all),
                onClick = { vm.selectCategory(null) },
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                selected = state.selectedCategory == null,
            )
        }
        item { Box(Modifier.size(6.dp).clip(CircleShape).background(Dadal.TextFaint)) }
        items(state.categories, key = { it.id }) { cat ->
            Pill(
                text = cat.name,
                onClick = { vm.selectCategory(if (state.selectedCategory == cat.id) null else cat.id) },
                icon = HabitIcons.get(cat.icon),
                selected = state.selectedCategory == cat.id,
            )
        }
    }
}

@Composable
private fun CardsView(
    state: HomeState,
    onOpen: (Long) -> Unit,
    onToggle: (Habit, LocalDate) -> Unit,
    onValue: (Habit, LocalDate) -> Unit,
) {
    val t = state.today.toEpochDay()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.rows, key = { it.habit.id }) { row ->
            val habit = row.habit
            Column(
                Modifier
                    .fillMaxWidth()
                    .habitCard(habit.color)
                    .clickable { onOpen(habit.id) }
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(habit.icon, habit.color)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                habit.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (row.stats.current > 0) {
                                Spacer(Modifier.width(8.dp))
                                StreakBadge(row.stats.current, active = row.stats.doneToday || habit.isQuit)
                            }
                        }
                        val sub = when {
                            row.stats.pausedToday -> stringResource(R.string.paused)
                            habit.description.isNotBlank() -> habit.description
                            else -> null
                        }
                        if (sub != null) {
                            Text(sub, style = MaterialTheme.typography.bodyLarge, color = Dadal.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    HabitCheckButton(
                        habit, row.stats,
                        onClick = { onToggle(habit, state.today) },
                        onLongClick = { onValue(habit, state.today) },
                    )
                }
                Spacer(Modifier.height(18.dp))
                CompactHeatmap(row.data, t)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekListView(
    state: HomeState,
    onOpen: (Long) -> Unit,
    onToggle: (Habit, LocalDate) -> Unit,
    onValue: (Habit, LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val days = (4 downTo 0).map { state.today.minusDays(it.toLong()) }
    val weekdays = context.resources.getStringArray(R.array.weekdays_two)
    val months = context.resources.getStringArray(R.array.months_short)
    val t = state.today.toEpochDay()
    val cellSize = 34.dp
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(stringResource(R.string.last_5_days), onClick = {}, textStyle = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "${months[days.first().monthValue - 1]} ${days.first().dayOfMonth} — ${months[days.last().monthValue - 1]} ${days.last().dayOfMonth}".uppercase(),
                    style = TextStyle(fontFamily = Dadal.Mono, fontSize = 13.sp, letterSpacing = 2.sp),
                    color = Dadal.TextMuted,
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().surfaceCard(26.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.habits_count, state.rows.size, state.rows.size).uppercase(),
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = Dadal.Mono, fontSize = 13.sp, letterSpacing = 2.sp),
                        color = Dadal.TextMuted,
                    )
                    days.forEach { d ->
                        val isToday = d == state.today
                        Column(Modifier.width(cellSize + 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(weekdays[d.dayOfWeek.value - 1], style = TextStyle(fontFamily = Dadal.Mono, fontSize = 13.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal), color = if (isToday) Dadal.Text else Dadal.TextMuted)
                            Text(d.dayOfMonth.toString(), style = TextStyle(fontFamily = Dadal.Mono, fontSize = 17.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal), color = if (isToday) Dadal.Text else Dadal.TextMuted)
                        }
                    }
                }
                state.rows.forEach { row ->
                    val habit = row.habit
                    val color = HabitPalette.get(habit.color)
                    HorizontalDivider(color = Dadal.Outline)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(habit.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(habit.icon, habit.color, size = 38.dp, corner = 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(habit.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        days.forEach { d ->
                            val e = d.toEpochDay()
                            val cell = row.data.cell(e, t)
                            Box(Modifier.width(cellSize + 6.dp), contentAlignment = Alignment.Center) {
                                val shape = RoundedCornerShape(11.dp)
                                Box(
                                    Modifier
                                        .size(cellSize)
                                        .clip(shape)
                                        .background(
                                            when (cell.state) {
                                                DayState.DONE -> color
                                                DayState.PARTIAL -> color.copy(alpha = 0.3f + 0.5f * cell.fraction)
                                                DayState.FREE, DayState.PAUSED -> Color.Transparent
                                                else -> color.copy(alpha = 0.12f)
                                            }
                                        )
                                        .then(if (d == state.today && cell.state != DayState.DONE) Modifier.border(1.5.dp, color.copy(alpha = 0.6f), shape) else Modifier)
                                        .combinedClickable(onClick = { onToggle(habit, d) }, onLongClick = { onValue(habit, d) }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (cell.state) {
                                        DayState.FREE -> Box(Modifier.size(5.dp).clip(CircleShape).background(Dadal.TextFaint))
                                        DayState.PAUSED -> Box(Modifier.width(10.dp).height(2.dp).background(Dadal.TextFaint))
                                        DayState.DONE -> Icon(Icons.Rounded.Check, null, tint = Color(0xCC000000), modifier = Modifier.size(18.dp))
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMonthView(
    state: HomeState,
    vm: HomeViewModel,
    onOpen: (Long) -> Unit,
    onToggle: (Habit, LocalDate) -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val t = state.today.toEpochDay()
    Column {
        Box(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Pill(
                monthYear(context, state.month.atDay(1)),
                onClick = { menu = true },
                trailingIcon = Icons.Rounded.KeyboardArrowDown,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Dadal.SurfaceHigh) {
                (0 until 12).map { YearMonth.from(state.today).minusMonths(it.toLong()) }.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(monthYear(context, m.atDay(1))) },
                        onClick = { vm.setMonth(m); menu = false },
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.rows, key = { it.habit.id }) { row ->
                val habit = row.habit
                Column(
                    Modifier.fillMaxWidth().surfaceCard(22.dp).clickable { onOpen(habit.id) }.padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitCheckButton(
                            habit, row.stats,
                            onClick = { onToggle(habit, state.today) },
                            size = 30.dp,
                            shape = CircleShape,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(habit.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    MiniMonth(row.data, t, state.month)
                }
            }
        }
    }
}

@Composable
private fun ViewSwitcher(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(Dadal.SurfaceHigh.copy(alpha = 0.96f))
            .border(1.dp, Dadal.OutlineSoft, shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Icons.Rounded.ViewAgenda to R.string.view_cards,
            Icons.Rounded.Checklist to R.string.view_week,
            Icons.Rounded.GridView to R.string.view_month,
        ).forEachIndexed { i, (icon, label) ->
            Box(
                Modifier
                    .size(width = 72.dp, height = 56.dp)
                    .clip(shape)
                    .background(if (i == selected) Dadal.SurfaceHighest else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, stringResource(label), tint = if (i == selected) Dadal.Text else Dadal.TextMuted)
            }
        }
    }
}
