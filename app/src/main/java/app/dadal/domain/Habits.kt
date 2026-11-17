package app.dadal.domain

import app.dadal.data.Habit
import app.dadal.data.HabitType
import app.dadal.data.Pause
import app.dadal.data.ScheduleType
import app.dadal.data.TrackingType
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Monday = 0 … Sunday = 6, straight from an epoch day (1970-01-01 was a Thursday). */
fun dayIndexOf(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt()
fun weekStartOf(epochDay: Long): Long = epochDay - dayIndexOf(epochDay)
fun LocalDate.dayIndex(): Int = dayOfWeek.value - 1

val Habit.isQuit: Boolean get() = type == HabitType.QUIT

/** What a single "done" tap writes. */
val Habit.goalValue: Double get() = if (tracking == TrackingType.YES_NO) 1.0 else target

/** Quit habits always count every day. */
val Habit.schedule: ScheduleType get() = if (isQuit) ScheduleType.DAILY else scheduleType

val Habit.isDayBased: Boolean get() = schedule == ScheduleType.DAILY || schedule == ScheduleType.SPECIFIC_DAYS

fun Habit.isScheduledOnIndex(dayIndex: Int): Boolean =
    schedule != ScheduleType.SPECIFIC_DAYS || ((scheduleDays shr dayIndex) and 1) == 1

fun Habit.isScheduledOn(date: LocalDate): Boolean = isScheduledOnIndex(date.dayIndex())

/** Build habits succeed by reaching the goal; quit habits succeed by staying at or under the limit. */
fun Habit.isSuccess(value: Double): Boolean = if (isQuit) {
    if (tracking == TrackingType.YES_NO) value <= 0.0 else value <= target + 1e-9
} else {
    if (tracking == TrackingType.YES_NO) value > 0.0 else value >= target - 1e-9
}

/** 0..1 fill level for grids and progress rings. */
fun Habit.fraction(value: Double): Float = when {
    isQuit -> if (isSuccess(value)) 1f else 0f
    tracking == TrackingType.YES_NO || target <= 0.0 -> if (value > 0.0) 1f else 0f
    else -> (value / target).toFloat().coerceIn(0f, 1f)
}

/** A habit plus its logged values and pauses. All days are epoch days. */
class HabitData(val habit: Habit, val values: Map<Long, Double>, val pauses: List<Pause>) {
    val startDay: Long = min(habit.createdDay, values.keys.minOrNull() ?: habit.createdDay)

    fun value(day: Long): Double = values[day] ?: 0.0

    fun isPaused(day: Long): Boolean =
        pauses.any { day >= it.startDay && (it.endDay == null || day <= it.endDay) }

    fun pauseOn(day: Long): Pause? =
        pauses.firstOrNull { day >= it.startDay && (it.endDay == null || day <= it.endDay) }

    fun isScheduled(day: Long): Boolean = habit.isScheduledOnIndex(dayIndexOf(day))

    fun isSuccess(day: Long): Boolean = habit.isSuccess(value(day))
}

enum class DayState { EMPTY, DONE, PARTIAL, MISSED, FREE, PAUSED, FUTURE, OUTSIDE }

data class DayCell(val state: DayState, val fraction: Float = 0f)

private val FUTURE_CELL = DayCell(DayState.FUTURE)
private val OUTSIDE_CELL = DayCell(DayState.OUTSIDE)
private val EMPTY_CELL = DayCell(DayState.EMPTY)
private val MISSED_CELL = DayCell(DayState.MISSED)
private val FREE_CELL = DayCell(DayState.FREE)
private val PAUSED_CELL = DayCell(DayState.PAUSED)
private val DONE_CELL = DayCell(DayState.DONE, 1f)

/** How one day looks on a grid or calendar. */
fun HabitData.cell(day: Long, today: Long): DayCell {
    if (day > today) return FUTURE_CELL
    if (day < startDay) return OUTSIDE_CELL
    val f = habit.fraction(value(day))
    if (isPaused(day)) return if (!habit.isQuit && f >= 1f) DONE_CELL else PAUSED_CELL
    if (!isScheduled(day)) {
        return when {
            f >= 1f -> DONE_CELL
            f > 0f -> DayCell(DayState.PARTIAL, f)
            else -> FREE_CELL
        }
    }
    return when {
        f >= 1f -> DONE_CELL
        f > 0f -> DayCell(DayState.PARTIAL, f)
        habit.isQuit -> MISSED_CELL
        day == today -> EMPTY_CELL
        habit.isDayBased -> MISSED_CELL
        else -> EMPTY_CELL
    }
}

enum class StreakUnit { DAYS, WEEKS, MONTHS, TIMES }

data class HabitStats(
    val current: Int = 0,
    val best: Int = 0,
    val unit: StreakUnit = StreakUnit.DAYS,
    /** 0..1 */
    val consistency: Float = 0f,
    /** Successful days, all time. */
    val total: Int = 0,
    val todayValue: Double = 0.0,
    val doneToday: Boolean = false,
    /** The habit asks for something today (independent of whether it is done). */
    val dueToday: Boolean = false,
    val pausedToday: Boolean = false,
    val periodDone: Int = 0,
    val periodGoal: Int = 1,
    /** Skipping today would break a running streak. */
    val streakEndsToday: Boolean = false,
)

object HabitCalculator {

    fun stats(d: HabitData, today: Long): HabitStats {
        val base = HabitStats(
            todayValue = d.value(today),
            doneToday = d.isSuccess(today),
            pausedToday = d.isPaused(today),
            total = totalSuccesses(d, today),
        )
        return when (d.habit.schedule) {
            ScheduleType.DAILY, ScheduleType.SPECIFIC_DAYS -> daily(d, today, base)
            ScheduleType.TIMES_PER_WEEK -> periodic(d, today, base, weekly = true)
            ScheduleType.TIMES_PER_MONTH -> periodic(d, today, base, weekly = false)
            ScheduleType.EVERY_N_DAYS -> everyN(d, today, base)
        }
    }

    private fun totalSuccesses(d: HabitData, today: Long): Int =
        if (d.habit.isQuit) {
            if (today < d.startDay) 0 else (d.startDay..today).count { !d.isPaused(it) && d.isSuccess(it) }
        } else {
            d.values.count { (day, v) -> day <= today && d.habit.isSuccess(v) }
        }

    private fun daily(d: HabitData, today: Long, base: HabitStats): HabitStats {
        var run = 0
        var best = 0
        var due = 0
        var done = 0
        var day = d.startDay
        while (day <= today) {
            if (!d.isPaused(day) && d.isScheduled(day)) {
                val ok = d.isSuccess(day)
                // An unfinished today is still open, except a slip on a quit habit, which counts right away.
                if (!(day == today && !ok && !d.habit.isQuit)) {
                    due++
                    if (ok) {
                        done++; run++; best = max(best, run)
                    } else {
                        run = 0
                    }
                }
            }
            day++
        }
        val dueToday = !base.pausedToday && d.isScheduled(today)
        return base.copy(
            current = run,
            best = best,
            unit = StreakUnit.DAYS,
            consistency = if (due == 0) 0f else done.toFloat() / due,
            dueToday = dueToday,
            periodDone = if (base.doneToday) 1 else 0,
            periodGoal = 1,
            streakEndsToday = !d.habit.isQuit && dueToday && !base.doneToday && run > 0,
        )
    }

    fun periodStart(day: Long, weekly: Boolean): Long =
        if (weekly) weekStartOf(day) else LocalDate.ofEpochDay(day).withDayOfMonth(1).toEpochDay()

    fun periodEnd(start: Long, weekly: Boolean): Long =
        if (weekly) start + 6 else LocalDate.ofEpochDay(start).let { it.withDayOfMonth(it.lengthOfMonth()) }.toEpochDay()

    private fun periodic(d: HabitData, today: Long, base: HabitStats, weekly: Boolean): HabitStats {
        val goal = max(1, d.habit.scheduleCount)
        var run = 0
        var best = 0
        var due = 0
        var done = 0
        var currentCount = 0
        var ps = periodStart(d.startDay, weekly)
        while (ps <= today) {
            val pe = periodEnd(ps, weekly)
            var count = 0
            var anyPaused = false
            var day = max(ps, d.startDay)
            val last = min(pe, today)
            while (day <= last) {
                if (d.isPaused(day)) anyPaused = true
                if (d.isSuccess(day)) count++
                day++
            }
            val isCurrent = today in ps..pe
            if (isCurrent) currentCount = count
            when {
                count >= goal -> {
                    due++; done++; run++; best = max(best, run)
                }
                isCurrent -> Unit // still open
                anyPaused || ps < d.startDay -> Unit // paused or partial first period: neutral
                else -> {
                    due++; run = 0
                }
            }
            ps = pe + 1
        }
        val need = goal - currentCount
        val daysLeft = (periodEnd(periodStart(today, weekly), weekly) - today + 1).toInt()
        return base.copy(
            current = run,
            best = best,
            unit = if (weekly) StreakUnit.WEEKS else StreakUnit.MONTHS,
            consistency = if (due == 0) 0f else done.toFloat() / due,
            dueToday = !base.pausedToday && need > 0,
            periodDone = currentCount,
            periodGoal = goal,
            streakEndsToday = !base.pausedToday && !base.doneToday && need > 0 && need >= daysLeft && run > 0,
        )
    }

    private fun everyN(d: HabitData, today: Long, base: HabitStats): HabitStats {
        val n = max(1, d.habit.scheduleCount).toLong()
        val completions = if (today < d.startDay) emptyList() else (d.startDay..today).filter { d.isSuccess(it) }
        fun gap(a: Long, b: Long): Long {
            var paused = 0
            var x = a + 1
            while (x < b) {
                if (d.isPaused(x)) paused++
                x++
            }
            return (b - a) - paused
        }
        var run = 0
        var best = 0
        var prev: Long? = null
        for (c in completions) {
            run = if (prev == null || gap(prev, c) <= n) run + 1 else 1
            best = max(best, run)
            prev = c
        }
        val last = completions.lastOrNull()
        val gapToToday = last?.let { gap(it, today) }
        val current = if (last == null || gapToToday!! > n) 0 else run
        val activeDays = if (today < d.startDay) 0 else (d.startDay..today).count { !d.isPaused(it) }
        val expected = max(1L, (activeDays + n - 1) / n)
        return base.copy(
            current = current,
            best = best,
            unit = StreakUnit.TIMES,
            consistency = (completions.size.toFloat() / expected).coerceAtMost(1f),
            dueToday = !base.pausedToday && (last == null || gapToToday!! >= n),
            periodDone = if (base.doneToday) 1 else 0,
            periodGoal = 1,
            streakEndsToday = !base.pausedToday && !base.doneToday && current > 0 && gapToToday == n,
        )
    }

    /** Should this habit show up in "today" lists (widget, quick check-in, lock screen)? */
    fun showToday(d: HabitData, s: HabitStats): Boolean =
        !d.habit.archived && !s.pausedToday && (s.dueToday || (s.doneToday && !d.habit.isQuit) || s.todayValue > 0)

    /**
     * The app-wide "perfect day" streak, shown like Duolingo's single streak: consecutive days on which
     * every day-based habit that was due got done. Days with nothing due don't count or break it.
     */
    fun perfectSummary(datas: List<HabitData>, today: Long): PerfectSummary {
        val dayBased = datas.filter { !it.habit.archived && it.habit.isDayBased }
        if (dayBased.isEmpty()) return PerfectSummary()
        val earliest = max(dayBased.minOf { it.startDay }, today - 3650)
        var run = 0
        var best = 0
        var count = 0
        var todayStatus: Boolean? = null
        var day = earliest
        while (day <= today) {
            val due = dayBased.filter { day >= it.startDay && !it.isPaused(day) && it.isScheduled(day) }
            if (due.isNotEmpty()) {
                val all = due.all { it.isSuccess(day) }
                if (day == today) todayStatus = all
                if (all) {
                    run++; count++; best = max(best, run)
                } else if (day != today) {
                    run = 0
                }
            }
            day++
        }
        return PerfectSummary(current = run, best = best, perfectDays = count, todayComplete = todayStatus)
    }
}

data class PerfectSummary(
    val current: Int = 0,
    val best: Int = 0,
    val perfectDays: Int = 0,
    /** null when nothing is due today. */
    val todayComplete: Boolean? = null,
)

object Milestones {
    val thresholds = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000)
    val ladder = listOf(7, 30, 100, 365)
    fun isMilestone(n: Int) = n in thresholds
    fun next(n: Int): Int? = thresholds.firstOrNull { it > n }
}
