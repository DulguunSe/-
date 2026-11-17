package app.dadal.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.dadal.MainActivity
import app.dadal.R
import app.dadal.container
import app.dadal.data.AppLanguage
import app.dadal.data.Snapshot
import app.dadal.domain.HabitCalculator
import app.dadal.domain.HabitData
import app.dadal.domain.HabitStats
import app.dadal.domain.PerfectSummary
import app.dadal.domain.isQuit
import app.dadal.ui.quick.QuickCheckActivity
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.HabitPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class WidgetData(
    val rows: List<Pair<HabitData, HabitStats>>,
    val perfect: PerfectSummary,
    val done: Int,
)

private fun widgetData(snapshot: Snapshot, today: LocalDate): WidgetData {
    val t = today.toEpochDay()
    val datas = snapshot.active.map { snapshot.data(it) }
    val rows = datas.map { it to HabitCalculator.stats(it, t) }
        .filter { (d, s) -> HabitCalculator.showToday(d, s) }
        .sortedBy { it.second.doneToday }
    return WidgetData(rows, HabitCalculator.perfectSummary(datas, t), rows.count { it.second.doneToday })
}

private val HabitIdKey = ActionParameters.Key<Long>("habit_id")
private val bg = ColorProvider(Dadal.Background)
private val surface = ColorProvider(Dadal.SurfaceHigh)
private val text = ColorProvider(Dadal.Text)
private val muted = ColorProvider(Dadal.TextMuted)

/** Tick a habit off straight from the home screen. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        context.container.actions.quickToggle(id)
    }
}

/** Today's habits with check buttons. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val ctx = AppLanguage.localized(context)
        provideContent {
            val flow = remember { combine(c.repo.snapshotFlow, c.today) { s, t -> widgetData(s, t) } }
            val data by flow.collectAsState(null)
            TodayContent(ctx, data)
        }
    }
}

@Composable
private fun TodayContent(ctx: Context, data: WidgetData?) {
    Column(
        GlanceModifier.fillMaxSize().background(bg).cornerRadius(26.dp).padding(14.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val streak = data?.perfect?.current ?: 0
            val lit = data?.perfect?.todayComplete == true
            Image(
                ImageProvider(R.drawable.ic_flame), null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(ColorProvider(if (lit) Dadal.Flame else Dadal.TextFaint)),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(streak.toString(), style = TextStyle(color = ColorProvider(if (lit) Dadal.Flame else Dadal.TextMuted), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                if (data == null) "" else "${data.done}/${data.rows.size}",
                style = TextStyle(color = muted, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            data == null -> Unit
            data.rows.isEmpty() -> Text(ctx.getString(R.string.widget_nothing), style = TextStyle(color = muted, fontSize = 14.sp))
            else -> LazyColumn {
                items(data.rows, itemId = { it.first.habit.id }) { (d, s) -> HabitWidgetRow(d, s) }
            }
        }
    }
}

@Composable
private fun HabitWidgetRow(d: HabitData, s: HabitStats) {
    val color = HabitPalette.get(d.habit.color)
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(30.dp).cornerRadius(9.dp).background(ColorProvider(color.copy(alpha = 0.22f))),
            contentAlignment = Alignment.Center,
        ) {
            Text(d.habit.name.take(1).uppercase(), style = TextStyle(color = ColorProvider(color), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(d.habit.name, maxLines = 1, style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            if (s.current > 0) Text("🔥 ${s.current}", style = TextStyle(color = ColorProvider(Dadal.Flame), fontSize = 11.sp))
        }
        val done = s.doneToday
        Box(
            GlanceModifier
                .size(36.dp)
                .cornerRadius(11.dp)
                .background(if (done && !d.habit.isQuit) ColorProvider(color) else surface)
                .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitIdKey to d.habit.id))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(if (d.habit.isQuit && !done) R.drawable.ic_close else R.drawable.ic_check), null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    ColorProvider(
                        when {
                            done && !d.habit.isQuit -> Color(0xE6000000)
                            d.habit.isQuit && done -> color
                            d.habit.isQuit -> Dadal.Slip
                            else -> Dadal.TextMuted
                        }
                    )
                ),
            )
        }
    }
}

/** A Duolingo-style streak flame: grey until today's habits are all done, then it lights up. */
class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val ctx = AppLanguage.localized(context)
        provideContent {
            val flow = remember { combine(c.repo.snapshotFlow, c.today) { s, t -> widgetData(s, t) } }
            val data by flow.collectAsState(null)
            val streak = data?.perfect?.current ?: 0
            val lit = data?.perfect?.todayComplete == true
            val left = data?.let { it.rows.size - it.done } ?: 0
            Column(
                GlanceModifier.fillMaxSize().background(bg).cornerRadius(26.dp).padding(10.dp)
                    .clickable(actionStartActivity<QuickCheckActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_flame), null,
                    modifier = GlanceModifier.size(44.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(if (lit) Dadal.Flame else Dadal.TextFaint)),
                )
                Text(streak.toString(), style = TextStyle(color = ColorProvider(if (lit) Dadal.Flame else Dadal.Text), fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text(
                    when {
                        data == null -> ""
                        lit -> ctx.getString(R.string.widget_done)
                        left > 0 -> ctx.resources.getQuantityString(R.plurals.widget_left, left, left)
                        else -> ctx.getString(R.string.widget_streak_label)
                    },
                    maxLines = 1,
                    style = TextStyle(color = muted, fontSize = 12.sp),
                )
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

/** Quick settings tile: shows today's progress, opens the quick check-in. */
class HabitTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        s.launch {
            val snap = container.repo.snapshot()
            val data = widgetData(snap, LocalDate.now())
            val tile = qsTile ?: return@launch
            val ctx = AppLanguage.localized(this@HabitTileService)
            tile.label = ctx.getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = if (data.rows.isEmpty()) "—" else ctx.getString(R.string.tile_progress, data.done, data.rows.size, data.perfect.current)
            }
            tile.icon = Icon.createWithResource(this@HabitTileService, R.drawable.ic_stat_dadal)
            tile.state = if (data.rows.isNotEmpty() && data.done == data.rows.size) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // only used below Android 14
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
