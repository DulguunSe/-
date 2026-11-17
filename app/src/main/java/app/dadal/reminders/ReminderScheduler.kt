package app.dadal.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.dadal.data.AppSettings
import app.dadal.data.Habit
import app.dadal.data.LAST_CHANCE_TIME
import app.dadal.data.MAX_REMINDERS
import app.dadal.data.ReminderStyle
import app.dadal.domain.isScheduledOn
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun rescheduleAll(habits: List<Habit>, settings: AppSettings) {
        habits.forEach { habit ->
            cancelHabit(habit.id)
            if (!habit.archived) scheduleHabit(habit)
        }
        scheduleDanger(settings)
        scheduleMidnight()
    }

    fun scheduleHabit(habit: Habit) {
        habit.reminderTimes.take(MAX_REMINDERS).forEachIndexed { index, minutes -> scheduleReminder(habit, index, minutes) }
    }

    fun scheduleReminder(habit: Habit, index: Int, minutes: Int) {
        val at = nextTrigger(habit, minutes) ?: return
        val pi = broadcast(ReminderReceiver.ACTION_REMIND, (habit.id * 10 + index).toInt(), habit.id, index, PendingIntent.FLAG_UPDATE_CURRENT)!!
        setAlarm(at, pi, alarmClock = habit.reminderStyle == ReminderStyle.ALARM)
    }

    fun cancelHabit(habitId: Long) {
        for (i in 0 until MAX_REMINDERS) {
            broadcast(ReminderReceiver.ACTION_REMIND, (habitId * 10 + i).toInt(), habitId, i, PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it); it.cancel()
            }
        }
        broadcast(ReminderReceiver.ACTION_SNOOZE_FIRE, habitId.toInt(), habitId, -1, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it); it.cancel()
        }
    }

    fun snooze(habit: Habit, minutes: Int = 10) {
        val pi = broadcast(ReminderReceiver.ACTION_SNOOZE_FIRE, habit.id.toInt(), habit.id, -1, PendingIntent.FLAG_UPDATE_CURRENT)!!
        setAlarm(System.currentTimeMillis() + minutes * 60_000L, pi, alarmClock = habit.reminderStyle == ReminderStyle.ALARM)
    }

    fun scheduleDanger(settings: AppSettings) {
        listOf(ReminderReceiver.ACTION_DANGER, ReminderReceiver.ACTION_LAST_CHANCE).forEach { action ->
            broadcast(action, 1, -1, -1, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it); it.cancel() }
        }
        if (!settings.dangerAlerts) return
        nextAtMinutes(settings.dangerTime)?.let {
            setAlarm(it, broadcast(ReminderReceiver.ACTION_DANGER, 1, -1, -1, PendingIntent.FLAG_UPDATE_CURRENT)!!, alarmClock = false)
        }
        if (settings.lastChance && LAST_CHANCE_TIME > settings.dangerTime) {
            nextAtMinutes(LAST_CHANCE_TIME)?.let {
                setAlarm(it, broadcast(ReminderReceiver.ACTION_LAST_CHANCE, 1, -1, -1, PendingIntent.FLAG_UPDATE_CURRENT)!!, alarmClock = false)
            }
        }
    }

    /** Refreshes the lock-screen notification and widgets just after midnight. */
    fun scheduleMidnight() {
        val at = LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay().plusSeconds(30).toMillis()
        val pi = broadcast(ReminderReceiver.ACTION_MIDNIGHT, 2, -1, -1, PendingIntent.FLAG_UPDATE_CURRENT)!!
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun nextTrigger(habit: Habit, minutes: Int): Long? {
        val now = LocalDateTime.now()
        for (i in 0..14) {
            val date = now.toLocalDate().plusDays(i.toLong())
            if (!habit.isScheduledOn(date)) continue
            val time = date.atTime(minutes / 60, minutes % 60)
            if (time.isAfter(now)) return time.toMillis()
        }
        return null
    }

    private fun nextAtMinutes(minutes: Int): Long? {
        val now = LocalDateTime.now()
        var time = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        if (!time.isAfter(now)) time = time.plusDays(1)
        return time.toMillis()
    }

    private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun setAlarm(at: Long, pi: PendingIntent, alarmClock: Boolean) {
        try {
            if (canScheduleExact()) {
                if (alarmClock) {
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifications.openAppIntent(context)), pi)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun broadcast(action: String, requestCode: Int, habitId: Long, index: Int, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(Notifications.EXTRA_HABIT, habitId)
            .putExtra(Notifications.EXTRA_INDEX, index)
        return PendingIntent.getBroadcast(context, requestCode, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
