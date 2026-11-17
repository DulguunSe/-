package app.dadal

import android.content.Context
import app.dadal.data.AppDatabase
import app.dadal.data.HabitRepository
import app.dadal.data.SettingsRepository
import app.dadal.reminders.Outputs
import app.dadal.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.build(context)
    val repo = HabitRepository(db)
    val settings = SettingsRepository(context)
    val scheduler = ReminderScheduler(context)
    val outputs = Outputs(context, this)
    val actions = HabitActions(this)
    val celebrations = MutableSharedFlow<Celebration>(extraBufferCapacity = 4)

    private val _today = MutableStateFlow(LocalDate.now())
    val today = _today.asStateFlow()

    fun refreshToday() {
        val now = LocalDate.now()
        if (_today.value != now) _today.value = now
    }

    suspend fun rescheduleAll() {
        scheduler.rescheduleAll(repo.allHabits(), settings.current())
    }

    @OptIn(FlowPreview::class)
    fun start() {
        appScope.launch { seedIfNeeded() }

        // Roll "today" over at midnight while the app is alive.
        appScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val next = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(1)
                delay(Duration.between(now, next).toMillis().coerceAtLeast(1000))
                refreshToday()
            }
        }

        // Keep the lock-screen notification, widgets and tile in sync with the data.
        appScope.launch {
            combine(repo.snapshotFlow, settings.settings, today) { s, st, t -> Triple(s, st, t) }
                .debounce(300)
                .collectLatest { (s, st, t) -> outputs.render(s, st, t) }
        }

        // Re-arm alarms whenever reminder times or alert settings change.
        appScope.launch {
            combine(repo.habits, settings.settings) { habits, st ->
                habits to st
            }.map { (habits, st) ->
                val key = habits.map { listOf(it.id, it.reminderTimes, it.reminderStyle, it.archived, it.scheduleType, it.scheduleDays) } +
                    listOf(st.dangerAlerts, st.dangerTime, st.lastChance)
                key to (habits to st)
            }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collectLatest { (_, pair) -> scheduler.rescheduleAll(pair.first, pair.second) }
        }
    }

    private suspend fun seedIfNeeded() {
        val st = settings.current()
        if (st.seeded) return
        if (repo.categoryCount() == 0) {
            repo.addCategory(context.getString(R.string.category_fitness), "bike")
            repo.addCategory(context.getString(R.string.category_health), "heart")
            repo.addCategory(context.getString(R.string.category_finances), "money")
        }
        settings.setSeeded()
    }
}

sealed interface Celebration {
    data class Milestone(val habitName: String, val color: Int, val count: Int, val unit: app.dadal.domain.StreakUnit) : Celebration
    data class PerfectDay(val streak: Int) : Celebration
}

val Context.container: AppContainer get() = (applicationContext as DadalApp).container
