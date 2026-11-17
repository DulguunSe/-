package app.dadal.domain

import app.dadal.data.Habit
import app.dadal.data.HabitType
import app.dadal.data.Pause
import app.dadal.data.ScheduleType
import app.dadal.data.TrackingType
import app.dadal.data.WEEKDAYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitCalculatorTest {
    // Tuesday 17 Nov 2026
    private val today = LocalDate.of(2026, 11, 17).toEpochDay()

    private fun habit(
        schedule: ScheduleType = ScheduleType.DAILY,
        type: HabitType = HabitType.BUILD,
        tracking: TrackingType = TrackingType.YES_NO,
        target: Double = 1.0,
        days: Int = 0b1111111,
        count: Int = 1,
        created: Long = today - 60,
    ) = Habit(
        id = 1, name = "h", type = type, tracking = tracking, target = target,
        scheduleType = schedule, scheduleDays = days, scheduleCount = count, createdDay = created,
    )

    private fun data(h: Habit, doneDays: Collection<Long>, value: Double = 1.0, pauses: List<Pause> = emptyList()) =
        HabitData(h, doneDays.associateWith { value }, pauses)

    @Test
    fun `daily streak counts back from today`() {
        val s = HabitCalculator.stats(data(habit(), (today - 4..today).toList()), today)
        assertEquals(5, s.current)
        assertTrue(s.doneToday)
    }

    @Test
    fun `unfinished today does not break the streak`() {
        val s = HabitCalculator.stats(data(habit(), (today - 3 until today).toList()), today)
        assertEquals(3, s.current)
        assertFalse(s.doneToday)
        assertTrue(s.streakEndsToday)
    }

    @Test
    fun `a missed day resets the streak and keeps the best`() {
        val done = (today - 20..today - 11).toList() + (today - 2..today).toList()
        val s = HabitCalculator.stats(data(habit(), done), today)
        assertEquals(3, s.current)
        assertEquals(10, s.best)
    }

    @Test
    fun `free days on specific-day habits never break the streak`() {
        // Weekdays only. Today is Tuesday; done Thu, Fri last week, Mon, Tue this week. Weekend skipped.
        val h = habit(schedule = ScheduleType.SPECIFIC_DAYS, days = WEEKDAYS)
        val done = listOf(today - 5, today - 4, today - 1, today)
        val s = HabitCalculator.stats(data(h, done), today)
        assertEquals(4, s.current)
    }

    @Test
    fun `paused days are skipped`() {
        val pause = Pause(habitId = 1, startDay = today - 5, endDay = today - 3)
        val done = listOf(today - 7, today - 6, today - 2, today - 1)
        val s = HabitCalculator.stats(data(habit(), done, pauses = listOf(pause)), today)
        assertEquals(4, s.current)
    }

    @Test
    fun `quit habit is clean by default and a slip resets it`() {
        val h = habit(type = HabitType.QUIT, created = today - 9)
        assertEquals(10, HabitCalculator.stats(data(h, emptyList()), today).current)
        val slipped = HabitCalculator.stats(data(h, listOf(today - 3)), today)
        assertEquals(3, slipped.current)
        val slippedToday = HabitCalculator.stats(data(h, listOf(today)), today)
        assertEquals(0, slippedToday.current)
    }

    @Test
    fun `count habit needs to reach the goal`() {
        val h = habit(tracking = TrackingType.COUNT, target = 8.0)
        val partial = HabitCalculator.stats(HabitData(h, mapOf(today to 5.0, today - 1 to 8.0), emptyList()), today)
        assertFalse(partial.doneToday)
        assertEquals(1, partial.current)
        assertEquals(0.625f, h.fraction(5.0), 0.001f)
    }

    @Test
    fun `weekly goal counts weeks in a row`() {
        val h = habit(schedule = ScheduleType.TIMES_PER_WEEK, count = 2, created = today - 40)
        val thisWeek = weekStartOf(today)
        val done = listOf(thisWeek - 7, thisWeek - 5, thisWeek - 14, thisWeek - 13, thisWeek - 21, thisWeek - 20)
        val s = HabitCalculator.stats(data(h, done), today)
        assertEquals(3, s.current)
        assertEquals(StreakUnit.WEEKS, s.unit)
        assertEquals(0, s.periodDone)
        assertTrue(s.dueToday)
    }

    @Test
    fun `every n days allows gaps up to n`() {
        val h = habit(schedule = ScheduleType.EVERY_N_DAYS, count = 3)
        val s = HabitCalculator.stats(data(h, listOf(today - 9, today - 6, today - 3)), today)
        assertEquals(3, s.current)
        assertTrue(s.dueToday)
        assertTrue(s.streakEndsToday)
        val broken = HabitCalculator.stats(data(h, listOf(today - 10, today - 2)), today)
        assertEquals(1, broken.current) // gap of 8 restarted the chain
        val lapsed = HabitCalculator.stats(data(h, listOf(today - 4)), today)
        assertEquals(0, lapsed.current) // deadline was yesterday
    }

    @Test
    fun `perfect day streak needs every daily habit`() {
        val a = data(habit().copy(id = 1), (today - 5..today).toList())
        val b = data(habit().copy(id = 2), (today - 2..today - 1).toList())
        val summary = HabitCalculator.perfectSummary(listOf(a, b), today)
        assertEquals(2, summary.current) // yesterday and the day before; today still open for b
        assertEquals(false, summary.todayComplete)
    }

    @Test
    fun `day index matches calendar`() {
        assertEquals(1, dayIndexOf(today)) // Tuesday
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), weekStartOf(today))
    }
}
