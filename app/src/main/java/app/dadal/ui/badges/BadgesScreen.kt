package app.dadal.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dadal.AppContainer
import app.dadal.R
import app.dadal.domain.Badge
import app.dadal.domain.BadgeCalculator
import app.dadal.domain.BadgeId
import app.dadal.domain.HabitCalculator
import app.dadal.domain.Milestones
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.IconBadge
import app.dadal.ui.components.surfaceCard
import app.dadal.ui.home.HabitRow
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitIcons
import app.dadal.ui.theme.HabitPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BadgesState(val badges: List<Badge> = emptyList(), val rows: List<HabitRow> = emptyList())

class BadgesViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<BadgesState> = combine(c.repo.snapshotFlow, c.repo.notes, c.today) { snap, notes, today ->
        val t = today.toEpochDay()
        val datas = snap.habits.map { snap.data(it) }
        val stats = datas.map { HabitCalculator.stats(it, t) }
        val rows = datas.indices.filter { !datas[it].habit.archived }.map { HabitRow(datas[it], stats[it]) }
        BadgesState(BadgeCalculator.compute(datas, stats, notes.size, t), rows.sortedByDescending { it.stats.best })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BadgesState())
}

private fun BadgeId.title() = when (this) {
    BadgeId.FIRST_STEP -> R.string.badge_first_step
    BadgeId.WEEK_STREAK -> R.string.badge_week
    BadgeId.MONTH_STREAK -> R.string.badge_month
    BadgeId.CENTURY -> R.string.badge_century
    BadgeId.YEAR -> R.string.badge_year
    BadgeId.PERFECT_DAY -> R.string.badge_perfect_day
    BadgeId.PERFECT_WEEK -> R.string.badge_perfect_week
    BadgeId.CHECKINS_100 -> R.string.badge_100
    BadgeId.CHECKINS_1000 -> R.string.badge_1000
    BadgeId.COLLECTOR -> R.string.badge_collector
    BadgeId.JOURNAL -> R.string.badge_journal
}

private fun BadgeId.desc() = when (this) {
    BadgeId.FIRST_STEP -> R.string.badge_first_step_desc
    BadgeId.WEEK_STREAK -> R.string.badge_week_desc
    BadgeId.MONTH_STREAK -> R.string.badge_month_desc
    BadgeId.CENTURY -> R.string.badge_century_desc
    BadgeId.YEAR -> R.string.badge_year_desc
    BadgeId.PERFECT_DAY -> R.string.badge_perfect_day_desc
    BadgeId.PERFECT_WEEK -> R.string.badge_perfect_week_desc
    BadgeId.CHECKINS_100 -> R.string.badge_100_desc
    BadgeId.CHECKINS_1000 -> R.string.badge_1000_desc
    BadgeId.COLLECTOR -> R.string.badge_collector_desc
    BadgeId.JOURNAL -> R.string.badge_journal_desc
}

private val badgeColors = mapOf(
    BadgeId.FIRST_STEP to 5, BadgeId.WEEK_STREAK to 1, BadgeId.MONTH_STREAK to 0, BadgeId.CENTURY to 2,
    BadgeId.YEAR to 3, BadgeId.PERFECT_DAY to 7, BadgeId.PERFECT_WEEK to 12, BadgeId.CHECKINS_100 to 9,
    BadgeId.CHECKINS_1000 to 13, BadgeId.COLLECTOR to 15, BadgeId.JOURNAL to 10,
)

@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val vm: BadgesViewModel = viewModel { BadgesViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val unlocked = state.badges.count { it.unlocked }

    LazyColumn(
        Modifier.fillMaxSize().background(Dadal.Background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.trophies), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.badges_unlocked, unlocked, state.badges.size), style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted)
                }
            }
        }
        items(state.badges.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { badge -> BadgeTile(badge, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (state.rows.isNotEmpty()) {
            item {
                Text(stringResource(R.string.streak_milestones), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
            items(state.rows, key = { it.habit.id }) { row ->
                val color = HabitPalette.get(row.habit.color)
                Row(Modifier.fillMaxWidth().surfaceCard(22.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(row.habit.icon, row.habit.color, size = 40.dp, corner = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val next = Milestones.next(row.stats.current)
                        if (next != null) {
                            Text(
                                stringResource(R.string.next_milestone, next - row.stats.current, next),
                                style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Milestones.ladder.forEach { m ->
                            val got = row.stats.best >= m
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (got) color else Dadal.SurfaceHigh)
                                    .border(1.dp, if (got) color else Dadal.OutlineSoft, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    m.toString(),
                                    style = TextStyle(fontFamily = Dadal.Mono, fontSize = if (m >= 100) 10.sp else 12.sp, fontWeight = FontWeight.Bold),
                                    color = if (got) androidx.compose.ui.graphics.Color(0xE6000000) else Dadal.TextFaint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier = Modifier) {
    val color = HabitPalette.get(badgeColors[badge.id] ?: 0)
    Column(
        modifier.surfaceCard(22.dp).padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(if (badge.unlocked) color.copy(alpha = 0.2f) else Dadal.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (badge.unlocked) HabitIcons.get(badge.id.icon) else Icons.Rounded.Lock,
                null,
                tint = if (badge.unlocked) color else Dadal.TextFaint,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(badge.id.title()), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, maxLines = 2)
        Text(stringResource(badge.id.desc()), style = MaterialTheme.typography.bodySmall, color = Dadal.TextMuted, textAlign = TextAlign.Center, maxLines = 3)
        if (!badge.unlocked) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Dadal.SurfaceHighest)) {
                Box(Modifier.fillMaxWidth(badge.shownProgress.toFloat() / badge.id.goal).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
            Text("${badge.shownProgress}/${badge.id.goal}", style = TextStyle(fontFamily = Dadal.Mono, fontSize = 11.sp), color = Dadal.TextMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
