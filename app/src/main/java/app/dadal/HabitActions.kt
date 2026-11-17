package app.dadal

import app.dadal.data.Habit
import app.dadal.data.TrackingType
import app.dadal.domain.HabitCalculator
import app.dadal.domain.Milestones
import app.dadal.domain.goalValue
import app.dadal.domain.isDayBased
import app.dadal.domain.isQuit
import java.time.LocalDate
import kotlin.math.max

/** Every write to check-ins goes through here, from the app, widgets and notifications alike. */
class HabitActions(private val c: AppContainer) {

    private fun todayEpoch() = LocalDate.now().toEpochDay()

    /** One tap on a check button. */
    suspend fun quickToggle(habitId: Long, day: Long = todayEpoch()) {
        val habit = c.repo.getHabit(habitId) ?: return
        val cur = c.repo.valueOf(habitId, day)
        val next = when {
            habit.isQuit && habit.tracking == TrackingType.YES_NO -> if (cur > 0) 0.0 else 1.0
            habit.isQuit -> cur + 1
            habit.tracking == TrackingType.YES_NO -> if (cur > 0) 0.0 else 1.0
            habit.tracking == TrackingType.COUNT -> if (cur >= habit.target) 0.0 else cur + 1
            else -> if (cur >= habit.target) 0.0 else habit.target
        }
        write(habit, day, next)
    }

    /** "Done" from a notification, alarm or widget: build habits reach their goal, quit habits are clean. */
    suspend fun markDone(habitId: Long, day: Long = todayEpoch()) {
        val habit = c.repo.getHabit(habitId) ?: return
        val value = if (habit.isQuit) 0.0 else max(c.repo.valueOf(habitId, day), habit.goalValue)
        write(habit, day, value)
    }

    suspend fun setValue(habitId: Long, day: Long, value: Double) {
        val habit = c.repo.getHabit(habitId) ?: return
        write(habit, day, value)
    }

    private suspend fun write(habit: Habit, day: Long, value: Double) {
        c.repo.setValue(habit.id, day, value)
        if (day == todayEpoch()) checkCelebrations(habit)
        c.outputs.refresh()
    }

    private suspend fun checkCelebrations(habit: Habit) {
        if (c.celebrations.subscriptionCount.value == 0) return // nobody is looking
        val today = todayEpoch()
        val snap = c.repo.snapshot()
        val settings = c.settings.current()
        val stats = HabitCalculator.stats(snap.data(habit), today)
        if (!habit.isQuit && stats.doneToday && Milestones.isMilestone(stats.current)) {
            val key = "m:${habit.id}:${stats.current}:$today"
            if (key !in settings.celebrated) {
                c.settings.addCelebrated(key)
                c.celebrations.emit(Celebration.Milestone(habit.name, habit.color, stats.current, stats.unit))
                return
            }
        }
        val datas = snap.active.map { snap.data(it) }
        val dueToday = datas.filter {
            it.habit.isDayBased && today >= it.startDay && !it.isPaused(today) && it.isScheduled(today)
        }
        if (dueToday.size >= 2 && dueToday.all { it.isSuccess(today) }) {
            val key = "p:$today"
            if (key !in settings.celebrated) {
                c.settings.addCelebrated(key)
                c.celebrations.emit(Celebration.PerfectDay(HabitCalculator.perfectSummary(datas, today).current))
            }
        }
    }
}
