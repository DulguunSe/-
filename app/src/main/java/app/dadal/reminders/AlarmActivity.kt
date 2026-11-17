package app.dadal.reminders

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.dadal.R
import app.dadal.container
import app.dadal.data.Habit
import app.dadal.domain.HabitCalculator
import app.dadal.domain.HabitStats
import app.dadal.domain.isQuit
import app.dadal.streakText
import app.dadal.ui.components.PrimaryButton
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.DadalTheme
import app.dadal.ui.theme.HabitIcons
import app.dadal.ui.theme.HabitPalette
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen alarm page shown over the lock screen when an alarm-style reminder rings. */
class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val habitId = intent.getLongExtra(Notifications.EXTRA_HABIT, -1)
        val c = container
        setContent {
            DadalTheme {
                var habit by remember { mutableStateOf<Habit?>(null) }
                var stats by remember { mutableStateOf(HabitStats()) }
                LaunchedEffect(habitId) {
                    val h = c.repo.getHabit(habitId)
                    if (h == null) { finish(); return@LaunchedEffect }
                    habit = h
                    stats = HabitCalculator.stats(c.repo.snapshot().data(h), LocalDate.now().toEpochDay())
                }
                BackHandler { dismiss(habitId) }
                habit?.let { h ->
                    AlarmContent(
                        h, stats,
                        onDone = {
                            Notifications.cancelReminder(this, habitId)
                            lifecycleScope.launch { c.actions.markDone(habitId); finish() }
                        },
                        onSnooze = {
                            Notifications.cancelReminder(this, habitId)
                            c.scheduler.snooze(h)
                            finish()
                        },
                        onDismiss = { dismiss(habitId) },
                    )
                }
            }
        }
    }

    private fun dismiss(habitId: Long) {
        Notifications.cancelReminder(this, habitId)
        finish()
    }
}

@Composable
private fun AlarmContent(habit: Habit, stats: HabitStats, onDone: () -> Unit, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val color = HabitPalette.get(habit.color)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Dadal.Background, Dadal.Background)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 64.sp, fontWeight = FontWeight.Light, color = Dadal.Text,
            )
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(150.dp).scale(pulse).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(HabitIcons.get(habit.icon), null, tint = color, modifier = Modifier.size(76.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(habit.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (habit.description.isNotBlank()) {
                Text(habit.description, style = MaterialTheme.typography.bodyLarge, color = Dadal.TextMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            if (stats.current > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Dadal.Flame)
                    Text(
                        stringResource(if (habit.isQuit) R.string.alarm_quit_streak else R.string.alarm_keep_streak, streakText(context, stats.current, stats.unit)),
                        color = Dadal.Flame, style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Text(stringResource(R.string.alarm_start_streak), color = Dadal.TextMuted, style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!habit.isQuit) PrimaryButton(stringResource(R.string.action_done), onDone, color = color)
            PrimaryButton(stringResource(R.string.action_snooze), onSnooze, color = Dadal.SurfaceHighest)
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_dismiss), color = Dadal.TextMuted, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
