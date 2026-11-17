package app.dadal.ui.edit

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dadal.R
import app.dadal.data.ALL_DAYS
import app.dadal.data.Habit
import app.dadal.data.HabitType
import app.dadal.data.MAX_REMINDERS
import app.dadal.data.ReminderStyle
import app.dadal.data.ScheduleType
import app.dadal.data.TrackingType
import app.dadal.data.WEEKDAYS
import app.dadal.data.WEEKENDS
import app.dadal.formatTime
import app.dadal.reminders.Notifications
import app.dadal.scheduleText
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.DadalTextField
import app.dadal.ui.components.DotsPreview
import app.dadal.ui.components.GroupCard
import app.dadal.ui.components.Pill
import app.dadal.ui.components.PrimaryButton
import app.dadal.ui.components.SectionLabel
import app.dadal.ui.components.Segmented
import app.dadal.ui.components.SheetHeader
import app.dadal.ui.components.TimeDialog
import app.dadal.ui.components.surfaceCard
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitIcons
import app.dadal.ui.theme.HabitPalette

enum class EditPage { MAIN, ICON, REPEAT, SPECIFIC_DAYS, TIMES_WEEK, TIMES_MONTH, EVERY_N, REMINDERS, CATEGORIES }

private fun EditPage.parent(): EditPage? = when (this) {
    EditPage.MAIN -> null
    EditPage.SPECIFIC_DAYS, EditPage.TIMES_WEEK, EditPage.TIMES_MONTH, EditPage.EVERY_N -> EditPage.REPEAT
    else -> EditPage.MAIN
}

private val PATTERN_DAILY = listOf(2, 2, 2, 2, 2, 2, 2)
private val PATTERN_SPECIFIC = listOf(2, 2, 2, 2, 2, 0, 0)
private val PATTERN_WEEK = listOf(2, 1, 2, 1, 2, 1, 1)
private val PATTERN_MONTH = listOf(2, 2, 1, 2, 1, 1, 2)
private val PATTERN_EVERY_N = listOf(2, 0, 0, 2, 0, 0, 2)

@Composable
fun EditScreen(habitId: Long?, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val c = LocalContainer.current
    val vm: EditViewModel = viewModel(key = "edit-$habitId") { EditViewModel(c, habitId) }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(EditPage.MAIN) }

    BackHandler { page.parent()?.let { page = it } ?: onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Dadal.Background)
            .statusBarsPadding()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Dadal.Surface.copy(alpha = 0.55f)),
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Dadal.TextFaint))
        if (!loaded) return@Box
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "edit-page",
        ) { p ->
            when (p) {
                EditPage.MAIN -> MainPage(vm, draft, target, categories.filter { it.id in draft.categoryIds }.joinToString { it.name }, onClose, { page = it }, onSaved)
                EditPage.ICON -> IconPage(draft, onBack = { page = EditPage.MAIN }) { key -> vm.update { it.copy(icon = key) }; page = EditPage.MAIN }
                EditPage.REPEAT -> RepeatPage(draft, vm, { page = EditPage.MAIN }) { page = it }
                EditPage.SPECIFIC_DAYS -> SpecificDaysPage(draft, vm) { page = EditPage.REPEAT }
                EditPage.TIMES_WEEK -> StepperPage(
                    stringResource(R.string.repeat_times_week), draft, 1..6, { n -> pluralStringResource(R.plurals.times_a_week, n, n) },
                    stringResource(R.string.times_week_explain), { page = EditPage.REPEAT },
                ) { n -> vm.update { it.copy(scheduleType = ScheduleType.TIMES_PER_WEEK, scheduleCount = n) } }
                EditPage.TIMES_MONTH -> StepperPage(
                    stringResource(R.string.repeat_times_month), draft, 1..28, { n -> pluralStringResource(R.plurals.times_a_month, n, n) },
                    stringResource(R.string.times_month_explain), { page = EditPage.REPEAT },
                ) { n -> vm.update { it.copy(scheduleType = ScheduleType.TIMES_PER_MONTH, scheduleCount = n) } }
                EditPage.EVERY_N -> StepperPage(
                    stringResource(R.string.repeat_every_few), draft, 2..30, { n -> pluralStringResource(R.plurals.every_n_days, n, n) },
                    stringResource(R.string.every_n_explain), { page = EditPage.REPEAT },
                ) { n -> vm.update { it.copy(scheduleType = ScheduleType.EVERY_N_DAYS, scheduleCount = n) } }
                EditPage.REMINDERS -> RemindersPage(draft, vm) { page = EditPage.MAIN }
                EditPage.CATEGORIES -> CategoriesPage(draft, vm, categories) { page = EditPage.MAIN }
            }
        }
    }
}

@Composable
private fun MainPage(
    vm: EditViewModel,
    draft: Habit,
    target: String,
    categoryNames: String,
    onClose: () -> Unit,
    go: (EditPage) -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val color = HabitPalette.get(draft.color)
    val quit = draft.type == HabitType.QUIT
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            SheetHeader(
                stringResource(if (vm.isNew) R.string.new_habit else R.string.edit_habit),
                onBack = onClose,
                backIcon = Icons.Rounded.Close,
                modifier = Modifier.padding(top = 12.dp),
            )
            IconHeader(draft) { go(EditPage.ICON) }

            SectionLabel(stringResource(R.string.name))
            DadalTextField(
                draft.name, { v -> vm.update { it.copy(name = v.take(60)) } },
                placeholder = stringResource(R.string.name_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            SectionLabel(stringResource(R.string.description))
            DadalTextField(
                draft.description, { v -> vm.update { it.copy(description = v.take(120)) } },
                placeholder = stringResource(R.string.description_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            SectionLabel(stringResource(R.string.color))
            HabitPalette.colors.chunked(7).forEachIndexed { row, list ->
                Row(Modifier.fillMaxWidth()) {
                    list.forEachIndexed { col, swatch ->
                        val index = row * 7 + col
                        val selected = index == draft.color
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .then(if (selected) Modifier.border(3.dp, swatch, RoundedCornerShape(18.dp)) else Modifier)
                                .padding(if (selected) 5.dp else 0.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(swatch)
                                .clickable { vm.update { it.copy(color = index) } },
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.habit_type))
            Segmented(
                listOf(stringResource(R.string.build_habit), stringResource(R.string.quit_habit)),
                if (quit) 1 else 0,
                { i ->
                    val newType = if (i == 1) HabitType.QUIT else HabitType.BUILD
                    vm.update { it.copy(type = newType) }
                    if (draft.tracking != TrackingType.YES_NO) {
                        vm.setTarget(if (newType == HabitType.QUIT) "3" else "8")
                    }
                },
            )
            Text(
                stringResource(if (quit) R.string.quit_habit_help else R.string.build_habit_help),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Dadal.TextMuted,
                textAlign = TextAlign.Center,
            )

            if (!quit) {
                SectionLabel(stringResource(R.string.repeat))
                GroupCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { go(EditPage.REPEAT) }.padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(scheduleText(context, draft), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        DotsPreview(patternFor(draft.scheduleType), color)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = Dadal.TextMuted)
                    }
                }
            }

            SectionLabel(stringResource(R.string.tracking))
            Segmented(
                listOf(stringResource(R.string.track_yes_no), stringResource(R.string.track_count), stringResource(R.string.track_measure)),
                draft.tracking.ordinal,
                { vm.setTracking(TrackingType.entries[it]) },
            )
            Text(
                stringResource(trackingHelp(draft)),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Dadal.TextMuted,
                textAlign = TextAlign.Center,
            )
            if (draft.tracking != TrackingType.YES_NO) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(if (quit) R.string.daily_limit else R.string.daily_goal))
                        DadalTextField(
                            target, vm::setTarget,
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = if (draft.tracking == TrackingType.MEASURE) KeyboardType.Decimal else KeyboardType.Number),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        SectionLabel(stringResource(R.string.unit))
                        DadalTextField(
                            draft.unit, { v -> vm.update { it.copy(unit = v.take(16)) } },
                            placeholder = stringResource(if (draft.tracking == TrackingType.MEASURE) R.string.unit_hint_measure else R.string.unit_hint_count),
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.reminder))
            GroupCard {
                Row(
                    Modifier.fillMaxWidth().clickable { go(EditPage.REMINDERS) }.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (draft.reminderTimes.isEmpty()) stringResource(R.string.no_reminders)
                        else draft.reminderTimes.sorted().joinToString("  ·  ") { formatTime(context, it) } +
                            if (draft.reminderStyle == ReminderStyle.ALARM) "  ⏰" else "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = Dadal.TextMuted)
                }
            }

            SectionLabel(stringResource(R.string.categories))
            GroupCard {
                Row(
                    Modifier.fillMaxWidth().clickable { go(EditPage.CATEGORIES) }.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        categoryNames.ifEmpty { stringResource(R.string.none) },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = Dadal.TextMuted)
                }
            }
            Spacer(Modifier.height(140.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Dadal.Background)))
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 16.dp),
        ) {
            PrimaryButton(stringResource(R.string.save), onClick = { vm.save(onSaved) }, enabled = draft.name.isNotBlank())
        }
    }
}

private fun patternFor(type: ScheduleType) = when (type) {
    ScheduleType.DAILY -> PATTERN_DAILY
    ScheduleType.SPECIFIC_DAYS -> PATTERN_SPECIFIC
    ScheduleType.TIMES_PER_WEEK -> PATTERN_WEEK
    ScheduleType.TIMES_PER_MONTH -> PATTERN_MONTH
    ScheduleType.EVERY_N_DAYS -> PATTERN_EVERY_N
}

private fun trackingHelp(d: Habit): Int = when {
    d.type == HabitType.QUIT && d.tracking == TrackingType.YES_NO -> R.string.track_help_quit_yes_no
    d.type == HabitType.QUIT && d.tracking == TrackingType.COUNT -> R.string.track_help_quit_count
    d.type == HabitType.QUIT -> R.string.track_help_quit_measure
    d.tracking == TrackingType.YES_NO -> R.string.track_help_yes_no
    d.tracking == TrackingType.COUNT -> R.string.track_help_count
    else -> R.string.track_help_measure
}

@Composable
private fun IconHeader(draft: Habit, onChange: () -> Unit) {
    val color = HabitPalette.get(draft.color)
    val decor = remember { HabitIcons.pickable.shuffled().take(26) }
    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
        // Faint wallpaper of icons behind the chosen one.
        Column(Modifier.fillMaxSize().alpha(0.32f), verticalArrangement = Arrangement.SpaceEvenly) {
            decor.chunked(7).take(4).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = if (r % 2 == 0) 0.dp else 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (_, icon) -> Icon(icon, null, tint = color, modifier = Modifier.size(28.dp)) }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Box(
                    Modifier.size(150.dp).clip(CircleShape).background(Color(0xFF1E1A1C)).background(color.copy(alpha = 0.14f)).clickable(onClick = onChange),
                    contentAlignment = Alignment.Center,
                ) { Icon(HabitIcons.get(draft.icon), null, tint = color, modifier = Modifier.size(72.dp)) }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp).size(40.dp).clip(CircleShape)
                        .background(Dadal.Background).padding(4.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Edit, null, tint = Color(0xE6000000), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.change_icon),
                color = color,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Dadal.Background.copy(alpha = 0.8f)).clickable(onClick = onChange).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun IconPage(draft: Habit, onBack: () -> Unit, onPick: (String) -> Unit) {
    val color = HabitPalette.get(draft.color)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SheetHeader(stringResource(R.string.choose_icon), onBack, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.padding(top = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            contentPadding = PaddingValues(bottom = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(HabitIcons.pickable, key = { it.first }) { (key, icon) ->
                val selected = key == draft.icon
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) color.copy(alpha = 0.25f) else Dadal.SurfaceHigh)
                        .border(1.dp, if (selected) color else Dadal.OutlineSoft, RoundedCornerShape(18.dp))
                        .clickable { onPick(key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, key, tint = if (selected) color else Dadal.Text, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun RepeatPage(draft: Habit, vm: EditViewModel, onBack: () -> Unit, go: (EditPage) -> Unit) {
    val color = HabitPalette.get(draft.color)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        SheetHeader(stringResource(R.string.goal_and_streak), onBack, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.padding(top = 12.dp))
        SectionLabel(stringResource(R.string.repeat))
        GroupCard {
            val options = listOf(
                Triple(ScheduleType.DAILY, R.string.repeat_every_day, PATTERN_DAILY),
                Triple(ScheduleType.SPECIFIC_DAYS, R.string.repeat_specific_days, PATTERN_SPECIFIC),
                Triple(ScheduleType.TIMES_PER_WEEK, R.string.repeat_times_week, PATTERN_WEEK),
                Triple(ScheduleType.TIMES_PER_MONTH, R.string.repeat_times_month, PATTERN_MONTH),
                Triple(ScheduleType.EVERY_N_DAYS, R.string.repeat_every_few, PATTERN_EVERY_N),
            )
            options.forEachIndexed { i, (type, label, pattern) ->
                if (i > 0) HorizontalDivider(color = Dadal.Outline)
                val selected = draft.scheduleType == type
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) color.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable {
                            when (type) {
                                ScheduleType.DAILY -> {
                                    vm.update { it.copy(scheduleType = ScheduleType.DAILY, scheduleDays = ALL_DAYS) }; onBack()
                                }
                                ScheduleType.SPECIFIC_DAYS -> {
                                    vm.update { it.copy(scheduleType = type, scheduleDays = if (it.scheduleType == type) it.scheduleDays else WEEKDAYS) }
                                    go(EditPage.SPECIFIC_DAYS)
                                }
                                ScheduleType.TIMES_PER_WEEK -> {
                                    vm.update { it.copy(scheduleType = type, scheduleCount = if (it.scheduleType == type) it.scheduleCount else 3) }
                                    go(EditPage.TIMES_WEEK)
                                }
                                ScheduleType.TIMES_PER_MONTH -> {
                                    vm.update { it.copy(scheduleType = type, scheduleCount = if (it.scheduleType == type) it.scheduleCount else 10) }
                                    go(EditPage.TIMES_MONTH)
                                }
                                ScheduleType.EVERY_N_DAYS -> {
                                    vm.update { it.copy(scheduleType = type, scheduleCount = if (it.scheduleType == type) it.scheduleCount.coerceAtLeast(2) else 2) }
                                    go(EditPage.EVERY_N)
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotsPreview(pattern, color)
                    Spacer(Modifier.width(18.dp))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                        modifier = Modifier.weight(1f),
                    )
                    if (selected && type == ScheduleType.DAILY) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = Color(0xE6000000), modifier = Modifier.size(18.dp))
                        }
                    } else if (type != ScheduleType.DAILY) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = if (selected) color else Dadal.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecificDaysPage(draft: Habit, vm: EditViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val color = HabitPalette.get(draft.color)
    val letters = context.resources.getStringArray(R.array.weekdays_letter)
    val days = draft.scheduleDays
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        SheetHeader(stringResource(R.string.repeat_specific_days), onBack, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.padding(top = 12.dp))
        SectionLabel(stringResource(R.string.which_days))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { i ->
                val on = (days shr i) and 1 == 1
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (on) color else Color.Transparent)
                        .border(1.dp, if (on) color else Dadal.Outline, CircleShape)
                        .clickable {
                            val next = days xor (1 shl i)
                            if (next != 0) vm.update { it.copy(scheduleDays = next) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letters[i], style = MaterialTheme.typography.titleMedium, color = if (on) Color(0xE6000000) else Dadal.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.preset_weekdays), { vm.update { it.copy(scheduleDays = WEEKDAYS) } }, selected = days == WEEKDAYS, accent = color)
            Pill(stringResource(R.string.preset_weekends), { vm.update { it.copy(scheduleDays = WEEKENDS) } }, selected = days == WEEKENDS, accent = color)
            Pill(stringResource(R.string.repeat_every_day), { vm.update { it.copy(scheduleDays = ALL_DAYS) } }, selected = days == ALL_DAYS, accent = color)
        }
        SectionLabel(stringResource(R.string.how_it_counts))
        Column(Modifier.fillMaxWidth().surfaceCard(26.dp).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (0..6).forEach { i ->
                    val on = (days shr i) and 1 == 1
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(letters[i], style = TextStyle(fontFamily = Dadal.Mono, fontSize = 13.sp), color = if (on) Dadal.Text else Dadal.TextFaint)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)))
                            else Box(Modifier.size(6.dp).clip(CircleShape).background(Dadal.TextFaint))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.specific_days_explain), style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp)) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(color))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.free_day_note), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
        }
        Row(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp, bottom = 40.dp)) {
            Icon(Icons.Rounded.NotificationsOff, null, tint = Dadal.TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.free_day_reminders), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
        }
    }
}

@Composable
private fun StepperPage(
    title: String,
    draft: Habit,
    range: IntRange,
    label: @Composable (Int) -> String,
    explain: String,
    onBack: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val color = HabitPalette.get(draft.color)
    val value = draft.scheduleCount.coerceIn(range)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        SheetHeader(title, onBack, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().surfaceCard(26.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.Remove, null, { if (value > range.first) onChange(value - 1) }, size = 56.dp)
                Text(
                    value.toString(),
                    modifier = Modifier.width(120.dp),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Dadal.Mono, fontSize = 56.sp, fontWeight = FontWeight.Bold),
                    color = color,
                )
                CircleButton(Icons.Rounded.Add, null, { if (value < range.last) onChange(value + 1) }, size = 56.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(label(value), style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.padding(top = 20.dp, start = 4.dp, end = 4.dp)) {
            Icon(Icons.Rounded.Info, null, tint = Dadal.TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(explain, style = MaterialTheme.typography.bodyLarge, color = Dadal.TextMuted)
        }
        Spacer(Modifier.height(32.dp))
        PrimaryButton(stringResource(R.string.done), onClick = onBack, color = color)
    }
}

@Composable
private fun RemindersPage(draft: Habit, vm: EditViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val color = HabitPalette.get(draft.color)
    var picking by remember { mutableStateOf<Int?>(null) } // index, or -1 for new
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        SheetHeader(stringResource(R.string.reminder), onBack, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.padding(top = 12.dp))
        SectionLabel(stringResource(R.string.reminder_times))
        GroupCard {
            val times = draft.reminderTimes.sorted()
            times.forEachIndexed { i, minutes ->
                if (i > 0) HorizontalDivider(color = Dadal.Outline)
                Row(
                    Modifier.fillMaxWidth().clickable { picking = i }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatTime(context, minutes),
                        style = TextStyle(fontFamily = Dadal.Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    CircleButton(Icons.Rounded.Delete, stringResource(R.string.delete), {
                        vm.update { it.copy(reminderTimes = times.filterIndexed { j, _ -> j != i }) }
                    }, size = 40.dp, tint = Dadal.TextMuted)
                }
            }
            if (times.size < MAX_REMINDERS) {
                if (times.isNotEmpty()) HorizontalDivider(color = Dadal.Outline)
                Row(
                    Modifier.fillMaxWidth().clickable { picking = -1 }.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = color)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.add_reminder_time), style = MaterialTheme.typography.titleMedium, color = color)
                }
            }
        }

        SectionLabel(stringResource(R.string.reminder_style))
        listOf(
            Triple(ReminderStyle.NOTIFICATION, Icons.Rounded.Notifications, R.string.style_notification to R.string.style_notification_desc),
            Triple(ReminderStyle.ALARM, Icons.Rounded.Alarm, R.string.style_alarm to R.string.style_alarm_desc),
        ).forEach { (style, icon, texts) ->
            val selected = draft.reminderStyle == style
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(shape)
                    .background(if (selected) color.copy(alpha = 0.12f) else Dadal.Surface)
                    .border(1.5.dp, if (selected) color else Dadal.OutlineSoft, shape)
                    .clickable { vm.update { it.copy(reminderStyle = style) } }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(texts.first), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(texts.second), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
                }
                if (selected) Icon(Icons.Rounded.Check, null, tint = color)
            }
        }

        if (draft.reminderStyle == ReminderStyle.ALARM && !Notifications.canUseFullScreen(context) && Build.VERSION.SDK_INT >= 34) {
            Row(
                Modifier.fillMaxWidth().surfaceCard(20.dp).clickable {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                    }
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = Dadal.Flame)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.full_screen_needed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = Dadal.TextMuted)
            }
        }

        Row(Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 40.dp)) {
            Icon(Icons.Rounded.Info, null, tint = Dadal.TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.reminder_smart_note), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
        }
    }

    picking?.let { index ->
        val times = draft.reminderTimes.sorted()
        TimeDialog(
            initialMinutes = times.getOrNull(index) ?: (9 * 60),
            onDismiss = { picking = null },
            onPick = { minutes ->
                val next = if (index >= 0) times.mapIndexed { j, t -> if (j == index) minutes else t } else times + minutes
                vm.update { it.copy(reminderTimes = next.distinct().sorted()) }
                picking = null
            },
        )
    }
}

@Composable
private fun CategoriesPage(draft: Habit, vm: EditViewModel, categories: List<app.dadal.data.Category>, onBack: () -> Unit) {
    val color = HabitPalette.get(draft.color)
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)) {
        SheetHeader(stringResource(R.string.categories), onBack, Icons.AutoMirrored.Rounded.ArrowBack, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        if (categories.isNotEmpty()) {
            GroupCard {
                categories.forEachIndexed { i, cat ->
                    if (i > 0) HorizontalDivider(color = Dadal.Outline)
                    val checked = cat.id in draft.categoryIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.update { it.copy(categoryIds = if (checked) it.categoryIds - cat.id else it.categoryIds + cat.id) } }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HabitIcons.get(cat.icon), null, tint = Dadal.Text)
                        Spacer(Modifier.width(14.dp))
                        Text(cat.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = color))
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.new_category))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DadalTextField(
                newName, { newName = it.take(24) },
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.category_name_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.width(10.dp))
            CircleButton(Icons.Rounded.Add, stringResource(R.string.add), {
                vm.addCategory(newName, "label"); newName = ""
            }, size = 56.dp, container = if (newName.isBlank()) Dadal.SurfaceHigh else color, tint = if (newName.isBlank()) Dadal.TextMuted else Color(0xE6000000))
        }
        Spacer(Modifier.height(40.dp))
    }
}
