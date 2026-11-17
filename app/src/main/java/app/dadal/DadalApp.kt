package app.dadal

import android.app.Application
import app.dadal.reminders.Notifications

class DadalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.start()
    }
}
