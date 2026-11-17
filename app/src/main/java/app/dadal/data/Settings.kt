package app.dadal.data

import android.content.Context
import android.os.Build
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class SortMode { DEFAULT, NAME, STREAK }

/** Time of the "last chance" streak alert: 22:30. */
const val LAST_CHANCE_TIME = 22 * 60 + 30

data class AppSettings(
    val homeView: Int = 0,
    val sortMode: SortMode = SortMode.DEFAULT,
    val hideDone: Boolean = false,
    val lockScreenProgress: Boolean = true,
    val dangerAlerts: Boolean = true,
    val dangerTime: Int = 20 * 60,
    val lastChance: Boolean = true,
    val seeded: Boolean = false,
    val celebrated: Set<String> = emptySet(),
    val notificationPromptShown: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val HOME_VIEW = intPreferencesKey("home_view")
        val SORT = stringPreferencesKey("sort")
        val HIDE_DONE = booleanPreferencesKey("hide_done")
        val LOCK_SCREEN = booleanPreferencesKey("lock_screen_progress")
        val DANGER = booleanPreferencesKey("danger_alerts")
        val DANGER_TIME = intPreferencesKey("danger_time")
        val LAST_CHANCE = booleanPreferencesKey("last_chance")
        val SEEDED = booleanPreferencesKey("seeded")
        val CELEBRATED = stringSetPreferencesKey("celebrated")
        val NOTIF_PROMPT = booleanPreferencesKey("notification_prompt_shown")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AppSettings(
                homeView = p[Keys.HOME_VIEW] ?: 0,
                sortMode = p[Keys.SORT]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.DEFAULT,
                hideDone = p[Keys.HIDE_DONE] ?: false,
                lockScreenProgress = p[Keys.LOCK_SCREEN] ?: true,
                dangerAlerts = p[Keys.DANGER] ?: true,
                dangerTime = p[Keys.DANGER_TIME] ?: (20 * 60),
                lastChance = p[Keys.LAST_CHANCE] ?: true,
                seeded = p[Keys.SEEDED] ?: false,
                celebrated = p[Keys.CELEBRATED] ?: emptySet(),
                notificationPromptShown = p[Keys.NOTIF_PROMPT] ?: false,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setHomeView(value: Int) = context.dataStore.edit { it[Keys.HOME_VIEW] = value }
    suspend fun setSort(value: SortMode) = context.dataStore.edit { it[Keys.SORT] = value.name }
    suspend fun setHideDone(value: Boolean) = context.dataStore.edit { it[Keys.HIDE_DONE] = value }
    suspend fun setLockScreen(value: Boolean) = context.dataStore.edit { it[Keys.LOCK_SCREEN] = value }
    suspend fun setDangerAlerts(value: Boolean) = context.dataStore.edit { it[Keys.DANGER] = value }
    suspend fun setDangerTime(value: Int) = context.dataStore.edit { it[Keys.DANGER_TIME] = value }
    suspend fun setLastChance(value: Boolean) = context.dataStore.edit { it[Keys.LAST_CHANCE] = value }
    suspend fun setSeeded() = context.dataStore.edit { it[Keys.SEEDED] = true }
    suspend fun setNotificationPromptShown() = context.dataStore.edit { it[Keys.NOTIF_PROMPT] = true }

    suspend fun addCelebrated(key: String) = context.dataStore.edit {
        val current = it[Keys.CELEBRATED] ?: emptySet()
        it[Keys.CELEBRATED] = (current.toList() + key).takeLast(400).toSet()
    }
}

/**
 * In-app language. Android 13+ stores it per app in the system; older versions get it from AppCompat
 * (activities) and from [localized] (notifications and widgets, which have no activity).
 */
object AppLanguage {
    private const val PREFS = "dadal_prefs"
    private const val KEY = "lang"

    fun get(context: Context): String {
        val fromSystem = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (fromSystem.isNotEmpty()) return fromSystem.substringBefore(',').substringBefore('-')
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    }

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    fun localized(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (tag.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config)
    }
}
