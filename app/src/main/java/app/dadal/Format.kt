package app.dadal

import android.content.Context
import android.text.format.DateFormat
import app.dadal.data.Habit
import app.dadal.data.ScheduleType
import app.dadal.data.TrackingType
import app.dadal.data.ALL_DAYS
import app.dadal.data.WEEKDAYS
import app.dadal.data.WEEKENDS
import app.dadal.domain.StreakUnit
import app.dadal.domain.isQuit
import app.dadal.domain.schedule
import java.time.LocalDate
import java.util.Locale

fun formatNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

fun formatTime(context: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (DateFormat.is24HourFormat(context)) String.format(Locale.ROOT, "%02d:%02d", h, m)
    else String.format(Locale.ROOT, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

fun streakText(context: Context, count: Int, unit: StreakUnit): String {
    val res = when (unit) {
        StreakUnit.DAYS -> R.plurals.days_count
        StreakUnit.WEEKS -> R.plurals.weeks_count
        StreakUnit.MONTHS -> R.plurals.months_count
        StreakUnit.TIMES -> R.plurals.times_count
    }
    return context.resources.getQuantityString(res, count, count)
}

fun scheduleText(context: Context, habit: Habit): String {
    if (habit.isQuit) return context.getString(R.string.repeat_every_day)
    return when (habit.schedule) {
        ScheduleType.DAILY -> context.getString(R.string.repeat_every_day)
        ScheduleType.SPECIFIC_DAYS -> when (habit.scheduleDays) {
            ALL_DAYS -> context.getString(R.string.repeat_every_day)
            WEEKDAYS -> context.getString(R.string.preset_weekdays)
            WEEKENDS -> context.getString(R.string.preset_weekends)
            else -> {
                val names = context.resources.getStringArray(R.array.weekdays_two)
                (0..6).filter { (habit.scheduleDays shr it) and 1 == 1 }.joinToString(" ") { names[it] }
            }
        }
        ScheduleType.TIMES_PER_WEEK -> context.resources.getQuantityString(R.plurals.times_a_week, habit.scheduleCount, habit.scheduleCount)
        ScheduleType.TIMES_PER_MONTH -> context.resources.getQuantityString(R.plurals.times_a_month, habit.scheduleCount, habit.scheduleCount)
        ScheduleType.EVERY_N_DAYS -> context.resources.getQuantityString(R.plurals.every_n_days, habit.scheduleCount, habit.scheduleCount)
    }
}

fun goalText(context: Context, habit: Habit): String? = when {
    habit.tracking == TrackingType.YES_NO -> null
    habit.isQuit -> context.getString(R.string.limit_value, formatNum(habit.target), habit.unit).trim()
    else -> context.getString(R.string.goal_value, formatNum(habit.target), habit.unit).trim()
}

fun monthYear(context: Context, date: LocalDate, short: Boolean = false): String {
    val months = context.resources.getStringArray(if (short) R.array.months_compact else R.array.months_full)
    return context.getString(if (short) R.string.month_year_short else R.string.month_year, months[date.monthValue - 1], date.year)
}

fun shortDate(context: Context, date: LocalDate): String {
    val months = context.resources.getStringArray(R.array.months_short)
    return context.getString(R.string.day_month, date.dayOfMonth, months[date.monthValue - 1])
}
