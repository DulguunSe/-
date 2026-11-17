package app.dadal.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.dadal.container
import app.dadal.domain.HabitCalculator
import app.dadal.domain.isQuit
import app.dadal.data.ReminderStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Runs [block] off the main thread and keeps the receiver alive until it finishes. */
fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REMIND = "app.dadal.action.REMIND"
        const val ACTION_SNOOZE_FIRE = "app.dadal.action.SNOOZE_FIRE"
        const val ACTION_DANGER = "app.dadal.action.DANGER"
        const val ACTION_LAST_CHANCE = "app.dadal.action.LAST_CHANCE"
        const val ACTION_MIDNIGHT = "app.dadal.action.MIDNIGHT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        launchAsync {
            when (intent.action) {
                ACTION_REMIND, ACTION_SNOOZE_FIRE -> {
                    val id = intent.getLongExtra(Notifications.EXTRA_HABIT, -1)
                    val index = intent.getIntExtra(Notifications.EXTRA_INDEX, -1)
                    val habit = c.repo.getHabit(id) ?: return@launchAsync
                    // Arm tomorrow's (or the next scheduled day's) reminder first.
                    if (intent.action == ACTION_REMIND && index in habit.reminderTimes.indices) {
                        c.scheduler.scheduleReminder(habit, index, habit.reminderTimes[index])
                    }
                    if (habit.archived) return@launchAsync
                    val today = LocalDate.now().toEpochDay()
                    val stats = HabitCalculator.stats(c.repo.snapshot().data(habit), today)
                    if (stats.pausedToday) return@launchAsync
                    // Smart: stay quiet when it's already done or not needed today.
                    if (!habit.isQuit && (stats.doneToday || !stats.dueToday)) return@launchAsync
                    if (habit.reminderStyle == ReminderStyle.ALARM) Notifications.showAlarm(context, habit, stats)
                    else Notifications.showReminder(context, habit, stats)
                }
                ACTION_DANGER, ACTION_LAST_CHANCE -> {
                    val settings = c.settings.current()
                    c.scheduler.scheduleDanger(settings)
                    if (settings.dangerAlerts) {
                        Notifications.showDanger(context, c.repo.snapshot(), lastChance = intent.action == ACTION_LAST_CHANCE)
                    }
                }
                ACTION_MIDNIGHT -> {
                    c.refreshToday()
                    c.scheduler.scheduleMidnight()
                    c.outputs.refresh()
                }
            }
        }
    }
}

/** Buttons on notifications. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "app.dadal.action.DONE"
        const val ACTION_SNOOZE = "app.dadal.action.SNOOZE"
        const val ACTION_DISMISS = "app.dadal.action.DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        val id = intent.getLongExtra(Notifications.EXTRA_HABIT, -1)
        Notifications.cancelReminder(context, id)
        launchAsync {
            when (intent.action) {
                ACTION_DONE -> c.actions.markDone(id)
                ACTION_SNOOZE -> c.repo.getHabit(id)?.let { c.scheduler.snooze(it) }
                ACTION_DISMISS -> Unit
            }
        }
    }
}

/** Alarms are wiped by reboots and clock changes, so set them again. */
class SystemEventReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        val c = context.container
        launchAsync {
            c.refreshToday()
            c.rescheduleAll()
            c.outputs.refresh()
        }
    }
}
