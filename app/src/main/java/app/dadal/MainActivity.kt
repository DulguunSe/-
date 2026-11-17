package app.dadal

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dadal.reminders.Notifications
import app.dadal.ui.LocalContainer
import app.dadal.ui.badges.BadgesScreen
import app.dadal.ui.components.CelebrationOverlay
import app.dadal.ui.detail.DetailScreen
import app.dadal.ui.edit.EditScreen
import app.dadal.ui.home.HomeScreen
import app.dadal.ui.settings.ArchivedScreen
import app.dadal.ui.settings.CategoriesScreen
import app.dadal.ui.settings.SettingsScreen
import app.dadal.ui.stats.StatsScreen
import app.dadal.ui.theme.Dadal
import app.dadal.ui.theme.DadalTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val openHabit = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            DadalTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    DadalNav(openHabit)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        container.refreshToday()
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifications.EXTRA_OPEN_HABIT, -1L) ?: -1L
        if (id > 0) openHabit.value = id
    }
}

@Composable
private fun DadalNav(openHabit: MutableStateFlow<Long?>) {
    val nav = rememberNavController()
    val c = androidx.compose.ui.platform.LocalContext.current.container
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    LaunchedEffect(Unit) { c.celebrations.collect { celebration = it } }
    LaunchedEffect(Unit) {
        openHabit.collect { id ->
            if (id != null) {
                nav.navigate("detail/$id") { launchSingleTop = true }
                openHabit.value = null
            }
        }
    }

    // Ask for notification permission once, on first launch (Android 13+).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        c.appScope.launch { c.outputs.refresh() }
    }
    LaunchedEffect(Unit) {
        val st = c.settings.current()
        if (!st.notificationPromptShown) {
            c.settings.setNotificationPromptShown()
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(Dadal.Background)) {
        NavHost(
            navController = nav,
            startDestination = "home",
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenHabit = { nav.navigate("detail/$it") },
                    onAdd = { nav.navigate("edit") },
                    onSettings = { nav.navigate("settings") },
                    onStats = { nav.navigate("stats") },
                    onBadges = { nav.navigate("badges") },
                    onArchived = { nav.navigate("archived") },
                )
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                DetailScreen(id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit?id=$id") })
            }
            composable(
                "edit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                enterTransition = { slideInVertically { it / 2 } + fadeIn() },
                exitTransition = { fadeOut() },
                popExitTransition = { slideOutVertically { it / 2 } + fadeOut() },
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                EditScreen(
                    habitId = id,
                    onClose = { nav.popBackStack() },
                    onSaved = { savedId ->
                        nav.popBackStack()
                        if (id == null) nav.navigate("detail/$savedId")
                    },
                )
            }
            composable("stats") { StatsScreen(onBack = { nav.popBackStack() }, onOpenHabit = { nav.navigate("detail/$it") }) }
            composable("badges") { BadgesScreen(onBack = { nav.popBackStack() }) }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onCategories = { nav.navigate("categories") },
                    onArchived = { nav.navigate("archived") },
                )
            }
            composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable("archived") { ArchivedScreen(onBack = { nav.popBackStack() }, onOpenHabit = { nav.navigate("detail/$it") }) }
        }
        celebration?.let { CelebrationOverlay(it) { celebration = null } }
    }
}
