package app.dadal.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dadal.MainActivity
import app.dadal.R
import app.dadal.data.AppLanguage
import app.dadal.data.AppSettings
import app.dadal.data.Habit
import app.dadal.data.Snapshot
import app.dadal.data.TrackingType
import app.dadal.domain.HabitCalculator
import app.dadal.domain.HabitStats
import app.dadal.domain.isQuit
import app.dadal.formatNum
import app.dadal.streakText
import app.dadal.ui.quick.QuickCheckActivity
import app.dadal.ui.theme.HabitPalette
import java.time.LocalDate

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_ALARMS = "alarms_v1"
    const val CH_STREAK = "streak_alerts"
    const val CH_PROGRESS = "progress"

    const val ID_PROGRESS = 1
    const val ID_DANGER = 2
    fun reminderId(habitId: Long) = 1000 + habitId.toInt()

    const val EXTRA_HABIT = "habit_id"
    const val EXTRA_INDEX = "index"
    const val EXTRA_OPEN_HABIT = "open_habit"

    fun createChannels(context: Context) {
        val ctx = AppLanguage.localized(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarms = NotificationChannel(CH_ALARMS, ctx.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_alarms_desc)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val reminders = NotificationChannel(CH_REMINDERS, ctx.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_reminders_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val streak = NotificationChannel(CH_STREAK, ctx.getString(R.string.channel_streak), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_streak_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val progress = NotificationChannel(CH_PROGRESS, ctx.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
            description = ctx.getString(R.string.channel_progress_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(alarms, reminders, streak, progress))
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    // ---- PendingIntents ----

    fun openAppIntent(context: Context, habitId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (habitId != null) putExtra(EXTRA_OPEN_HABIT, habitId)
        }
        return PendingIntent.getActivity(
            context, (habitId ?: 0L).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun quickCheckIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7, Intent(context, QuickCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(context: Context, action: String, habitId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, habitId.toInt(),
        Intent(context, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_HABIT, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarmScreenIntent(context: Context, habitId: Long): PendingIntent = PendingIntent.getActivity(
        context, habitId.toInt(),
        Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_HABIT, habitId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ---- Reminders ----

    private fun reminderLine(ctx: Context, habit: Habit, stats: HabitStats): String = when {
        habit.isQuit && stats.current > 0 -> ctx.getString(R.string.reminder_quit_streak, streakText(ctx, stats.current, stats.unit))
        habit.isQuit -> ctx.getString(R.string.reminder_quit)
        stats.current > 0 -> ctx.getString(R.string.reminder_keep_streak, streakText(ctx, stats.current, stats.unit))
        habit.tracking != TrackingType.YES_NO && stats.todayValue > 0 ->
            ctx.getString(R.string.reminder_progress, formatNum(stats.todayValue), formatNum(habit.target), habit.unit).trim()
        habit.description.isNotBlank() -> habit.description
        else -> ctx.getString(R.string.reminder_default)
    }

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, habit: Habit, stats: HabitStats) {
        if (!canPost(context)) return
        val ctx = AppLanguage.localized(context)
        val color = HabitPalette.get(habit.color).toArgb()
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dadal)
            .setColor(color)
            .setContentTitle(habit.name)
            .setContentText(reminderLine(ctx, habit, stats))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context, habit.id))
            .setAutoCancel(true)
            .apply {
                if (!habit.isQuit) addAction(R.drawable.ic_check, ctx.getString(R.string.action_done), actionIntent(context, ActionReceiver.ACTION_DONE, habit.id))
                addAction(R.drawable.ic_snooze, ctx.getString(R.string.action_snooze), actionIntent(context, ActionReceiver.ACTION_SNOOZE, habit.id))
            }
            .build()
        NotificationManagerCompat.from(context).notify(reminderId(habit.id), n)
    }

    /** Rings like an alarm (sound repeats) and opens a full-screen page over the lock screen. */
    @SuppressLint("MissingPermission")
    fun showAlarm(context: Context, habit: Habit, stats: HabitStats) {
        if (!canPost(context)) return
        val ctx = AppLanguage.localized(context)
        val color = HabitPalette.get(habit.color).toArgb()
        val fullScreen = alarmScreenIntent(context, habit.id)
        val n = NotificationCompat.Builder(context, CH_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_dadal)
            .setColor(color)
            .setContentTitle("⏰ " + habit.name)
            .setContentText(reminderLine(ctx, habit, stats))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(10 * 60_000L)
            .apply {
                if (!habit.isQuit) addAction(R.drawable.ic_check, ctx.getString(R.string.action_done), actionIntent(context, ActionReceiver.ACTION_DONE, habit.id))
                addAction(R.drawable.ic_snooze, ctx.getString(R.string.action_snooze), actionIntent(context, ActionReceiver.ACTION_SNOOZE, habit.id))
                addAction(0, ctx.getString(R.string.action_dismiss), actionIntent(context, ActionReceiver.ACTION_DISMISS, habit.id))
            }
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(reminderId(habit.id), n)
    }

    fun cancelReminder(context: Context, habitId: Long) =
        NotificationManagerCompat.from(context).cancel(reminderId(habitId))

    // ---- Streak in danger ----

    @SuppressLint("MissingPermission")
    fun showDanger(context: Context, snapshot: Snapshot, lastChance: Boolean) {
        if (!canPost(context)) return
        val ctx = AppLanguage.localized(context)
        val today = LocalDate.now().toEpochDay()
        val rows = snapshot.active.map { h -> h to HabitCalculator.stats(snapshot.data(h), today) }
        val atRisk = rows.filter { (h, s) -> !h.isQuit && s.streakEndsToday }.sortedByDescending { it.second.current }
        val pending = rows.filter { (h, s) -> !h.isQuit && s.dueToday && !s.doneToday && !s.pausedToday }
        if (atRisk.isEmpty() && (lastChance || pending.isEmpty())) return

        val title: String
        val text: String
        if (atRisk.isNotEmpty()) {
            val (h, s) = atRisk.first()
            val streak = streakText(ctx, s.current, s.unit)
            title = if (lastChance) ctx.getString(R.string.danger_last_chance_title, streak, h.name)
            else ctx.getString(R.string.danger_title, streak, h.name)
            val others = atRisk.size - 1
            text = when {
                lastChance -> ctx.getString(R.string.danger_last_chance_text)
                others > 0 -> ctx.resources.getQuantityString(R.plurals.danger_text_more, others, others)
                else -> ctx.getString(R.string.danger_text)
            }
        } else {
            title = ctx.resources.getQuantityString(R.plurals.pending_title, pending.size, pending.size)
            text = ctx.getString(R.string.pending_text)
        }
        val n = NotificationCompat.Builder(context, CH_STREAK)
            .setSmallIcon(R.drawable.ic_stat_dadal)
            .setColor(0xFFFF9F43.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .addAction(R.drawable.ic_check, ctx.getString(R.string.action_check_in), quickCheckIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_DANGER, n)
    }

    // ---- Lock screen progress ----

    @SuppressLint("MissingPermission")
    fun updateProgress(context: Context, snapshot: Snapshot, settings: AppSettings, today: LocalDate) {
        val nm = NotificationManagerCompat.from(context)
        if (!settings.lockScreenProgress || !canPost(context) || snapshot.active.isEmpty()) {
            nm.cancel(ID_PROGRESS); return
        }
        val ctx = AppLanguage.localized(context)
        val t = today.toEpochDay()
        val datas = snapshot.active.map { snapshot.data(it) }
        val rows = datas.map { it to HabitCalculator.stats(it, t) }.filter { (d, s) -> HabitCalculator.showToday(d, s) }
        val perfect = HabitCalculator.perfectSummary(datas, t)
        val done = rows.count { it.second.doneToday }
        val total = rows.size
        val pending = rows.filter { !it.second.doneToday }

        val title = when {
            total == 0 -> ctx.getString(R.string.progress_nothing_due)
            done == total -> ctx.getString(R.string.progress_all_done)
            else -> ctx.getString(R.string.progress_title, done, total)
        }
        val streakLine = ctx.getString(R.string.progress_streak, perfect.current)
        val text = if (pending.isEmpty()) streakLine
        else streakLine + " · " + pending.take(3).joinToString(", ") { it.first.habit.name }

        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        rows.sortedBy { it.second.doneToday }.take(6).forEach { (d, s) ->
            val mark = if (s.doneToday) "✓" else "○"
            val fire = if (s.current > 0) "   🔥 ${s.current}" else ""
            inbox.addLine("$mark  ${d.habit.name}$fire")
        }
        inbox.setSummaryText(streakLine)

        val builder = NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_dadal)
            .setColor(0xFF8B8BF7.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(inbox)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
        if (total > 0) builder.setProgress(total, done, false)
        pending.firstOrNull()?.let { (d, _) ->
            if (!d.habit.isQuit) {
                builder.addAction(
                    R.drawable.ic_check,
                    ctx.getString(R.string.action_done_named, d.habit.name),
                    actionIntent(context, ActionReceiver.ACTION_DONE, d.habit.id)
                )
            }
        }
        builder.addAction(R.drawable.ic_check, ctx.getString(R.string.action_check_in), quickCheckIntent(context))
        nm.notify(ID_PROGRESS, builder.build())
    }
}
