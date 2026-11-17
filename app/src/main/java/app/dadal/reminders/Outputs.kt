package app.dadal.reminders

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import app.dadal.AppContainer
import app.dadal.data.AppSettings
import app.dadal.data.Snapshot
import app.dadal.widget.HabitTileService
import app.dadal.widget.StreakWidget
import app.dadal.widget.TodayWidget
import java.time.LocalDate

/** Everything shown outside the app: lock-screen notification, home-screen widgets, quick settings tile. */
class Outputs(private val context: Context, private val container: AppContainer) {

    suspend fun refresh() {
        render(container.repo.snapshot(), container.settings.current(), LocalDate.now())
    }

    suspend fun render(snapshot: Snapshot, settings: AppSettings, today: LocalDate) {
        runCatching { Notifications.updateProgress(context, snapshot, settings, today) }
        runCatching { TodayWidget().updateAll(context) }
        runCatching { StreakWidget().updateAll(context) }
        runCatching { TileService.requestListeningState(context, ComponentName(context, HabitTileService::class.java)) }
    }
}
