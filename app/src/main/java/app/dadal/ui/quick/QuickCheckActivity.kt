package app.dadal.ui.quick

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.dadal.MainActivity
import app.dadal.R
import app.dadal.container
import app.dadal.domain.HabitCalculator
import app.dadal.ui.components.HabitCheckButton
import app.dadal.ui.components.IconBadge
import app.dadal.ui.components.StreakBadge
import app.dadal.ui.home.HabitRow
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.DadalTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A small pop-up to tick off today's habits from the quick settings tile or a notification. */
class QuickCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val c = container
        setContent {
            DadalTheme {
                val rows by remember {
                    c.repo.snapshotFlow.map { snap ->
                        val t = LocalDate.now().toEpochDay()
                        snap.active.map { h -> val d = snap.data(h); HabitRow(d, HabitCalculator.stats(d, t)) }
                            .filter { HabitCalculator.showToday(it.data, it.stats) }
                            .sortedBy { it.stats.doneToday }
                    }
                }.collectAsStateWithLifecycle(initialValue = emptyList())
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(30.dp))
                            .background(Dadal.Surface)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .padding(20.dp),
                    ) {
                        val done = rows.count { it.stats.doneToday }
                        Text(stringResource(R.string.quick_title), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (rows.isEmpty()) stringResource(R.string.progress_nothing_due)
                            else stringResource(R.string.progress_title, done, rows.size),
                            style = MaterialTheme.typography.bodyMedium, color = Dadal.TextMuted,
                        )
                        Spacer(Modifier.padding(6.dp))
                        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(rows, key = { it.habit.id }) { row ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(row.habit.icon, row.habit.color, size = 44.dp, corner = 14.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(row.habit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (row.stats.current > 0) StreakBadge(row.stats.current, fontSize = 12)
                                    }
                                    HabitCheckButton(
                                        row.habit, row.stats,
                                        onClick = { lifecycleScope.launch { c.actions.quickToggle(row.habit.id) } },
                                        size = 48.dp,
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                startActivity(Intent(this@QuickCheckActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }) { Text(stringResource(R.string.open_app)) }
                            TextButton(onClick = { finish() }) { Text(stringResource(R.string.close), color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}
