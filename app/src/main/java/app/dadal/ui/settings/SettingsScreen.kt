package app.dadal.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dadal.BuildConfig
import app.dadal.R
import app.dadal.data.AppLanguage
import app.dadal.data.AppSettings
import app.dadal.data.LAST_CHANCE_TIME
import app.dadal.formatTime
import app.dadal.reminders.Notifications
import app.dadal.ui.LocalContainer
import app.dadal.ui.components.ChoiceDialog
import app.dadal.ui.components.CircleButton
import app.dadal.ui.components.ConfirmDialog
import app.dadal.ui.components.GroupCard
import app.dadal.ui.components.SectionLabel
import app.dadal.ui.components.SettingRow
import app.dadal.ui.components.TimeDialog
import app.dadal.ui.theme.Dadal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit, onArchived: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    var languageDialog by remember { mutableStateOf(false) }
    var dangerTimeDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { c.repo.exportTo(it) } }.isSuccess
            }
            Toast.makeText(context, if (ok) R.string.backup_saved else R.string.backup_failed, Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    // Re-read permission states when coming back from system settings.
    val notificationsOk = remember(tick) { Notifications.canPost(context) }
    val fullScreenOk = remember(tick) { Notifications.canUseFullScreen(context) }
    val exactOk = remember(tick) { c.scheduler.canScheduleExact() }
    val batteryOk = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val isXiaomi = remember { Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco") }
    val lang = remember(tick) { AppLanguage.get(context) }

    LazyColumn(
        Modifier.fillMaxSize().background(Dadal.Background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
    ) {
        item {
            Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
            }
        }

        item {
            SectionLabel(stringResource(R.string.general))
            GroupCard {
                SettingRow(
                    stringResource(R.string.language),
                    subtitle = when (lang) {
                        "en" -> "English"
                        "mn" -> "Монгол"
                        else -> stringResource(R.string.language_system)
                    },
                    leading = { RowIcon(Icons.Rounded.Translate) },
                    onClick = { languageDialog = true },
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.lock_screen_widgets))
            GroupCard {
                SwitchRow(
                    Icons.Rounded.Lock,
                    stringResource(R.string.lock_screen_streak),
                    stringResource(R.string.lock_screen_streak_desc),
                    settings.lockScreenProgress,
                ) { v -> scope.launch { c.settings.setLockScreen(v) } }
                HorizontalDivider(color = Dadal.Outline)
                SettingRow(
                    stringResource(R.string.add_widget),
                    subtitle = stringResource(R.string.add_widget_desc),
                    leading = { RowIcon(Icons.Rounded.Widgets) },
                    trailing = null,
                )
            }
        }

        item {
            SectionLabel(stringResource(R.string.streak_protection))
            GroupCard {
                SwitchRow(
                    Icons.Rounded.LocalFireDepartment,
                    stringResource(R.string.danger_alerts),
                    stringResource(R.string.danger_alerts_desc),
                    settings.dangerAlerts,
                ) { v -> scope.launch { c.settings.setDangerAlerts(v) } }
                if (settings.dangerAlerts) {
                    HorizontalDivider(color = Dadal.Outline)
                    SettingRow(
                        stringResource(R.string.danger_time),
                        subtitle = formatTime(context, settings.dangerTime),
                        leading = { RowIcon(Icons.Rounded.Schedule) },
                        onClick = { dangerTimeDialog = true },
                    )
                    HorizontalDivider(color = Dadal.Outline)
                    SwitchRow(
                        Icons.Rounded.Alarm,
                        stringResource(R.string.last_chance),
                        stringResource(R.string.last_chance_desc, formatTime(context, LAST_CHANCE_TIME)),
                        settings.lastChance,
                    ) { v -> scope.launch { c.settings.setLastChance(v) } }
                }
            }
        }

        item {
            SectionLabel(stringResource(R.string.reliability))
            Text(
                stringResource(R.string.reliability_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = Dadal.TextMuted,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
            GroupCard {
                StatusRow(Icons.Rounded.Notifications, stringResource(R.string.perm_notifications), notificationsOk) {
                    if (Build.VERSION.SDK_INT >= 33 && !notificationsOk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openAppNotificationSettings(context)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    HorizontalDivider(color = Dadal.Outline)
                    StatusRow(Icons.Rounded.Alarm, stringResource(R.string.perm_full_screen), fullScreenOk) {
                        open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                    }
                }
                if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                    HorizontalDivider(color = Dadal.Outline)
                    StatusRow(Icons.Rounded.Schedule, stringResource(R.string.perm_exact), false) {
                        open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
                HorizontalDivider(color = Dadal.Outline)
                StatusRow(Icons.Rounded.BatteryChargingFull, stringResource(R.string.perm_battery), batteryOk) {
                    requestIgnoreBattery(context)
                }
                if (isXiaomi) {
                    HorizontalDivider(color = Dadal.Outline)
                    SettingRow(
                        stringResource(R.string.perm_autostart),
                        subtitle = stringResource(R.string.perm_autostart_desc),
                        leading = { RowIcon(Icons.Rounded.PhoneAndroid) },
                        onClick = { openXiaomiAutostart(context) },
                    )
                }
            }
        }

        item {
            SectionLabel(stringResource(R.string.data))
            GroupCard {
                SettingRow(stringResource(R.string.manage_categories), leading = { RowIcon(Icons.Rounded.Category) }, onClick = onCategories)
                HorizontalDivider(color = Dadal.Outline)
                SettingRow(stringResource(R.string.archived_habits), leading = { RowIcon(Icons.Rounded.Archive) }, onClick = onArchived)
                HorizontalDivider(color = Dadal.Outline)
                SettingRow(
                    stringResource(R.string.export_backup),
                    subtitle = stringResource(R.string.export_backup_desc),
                    leading = { RowIcon(Icons.Rounded.Upload) },
                    onClick = { exportLauncher.launch("dadal-backup-${LocalDate.now()}.json") },
                )
                HorizontalDivider(color = Dadal.Outline)
                SettingRow(
                    stringResource(R.string.import_backup),
                    subtitle = stringResource(R.string.import_backup_desc),
                    leading = { RowIcon(Icons.Rounded.Download) },
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
        }

        item {
            Text(
                stringResource(R.string.about_line, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = Dadal.TextFaint,
                modifier = Modifier.padding(top = 28.dp, start = 12.dp),
            )
        }
    }

    if (languageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = listOf("" to stringResource(R.string.language_system), "en" to "English", "mn" to "Монгол"),
            selected = lang,
            onDismiss = { languageDialog = false },
            onPick = { tag ->
                languageDialog = false
                AppLanguage.set(context, tag)
                Notifications.createChannels(context)
            },
        )
    }
    if (dangerTimeDialog) {
        TimeDialog(settings.dangerTime, onDismiss = { dangerTimeDialog = false }) { minutes ->
            dangerTimeDialog = false
            scope.launch { c.settings.setDangerTime(minutes.coerceAtMost(LAST_CHANCE_TIME - 1)) }
        }
    }
    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            text = stringResource(R.string.import_confirm_text),
            confirm = stringResource(R.string.import_replace),
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)!!.use { c.repo.importFrom(it) } }
                    }
                    result.onSuccess {
                        c.rescheduleAll()
                        c.outputs.refresh()
                        Toast.makeText(context, context.resources.getQuantityString(R.plurals.backup_restored, it, it), Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(context, R.string.backup_invalid, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Dadal.SurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Dadal.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title,
        subtitle = subtitle,
        leading = { RowIcon(icon) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Dadal.Primary, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
            )
        },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, ok: Boolean, onFix: () -> Unit) {
    SettingRow(
        title,
        subtitle = stringResource(if (ok) R.string.status_ok else R.string.status_fix),
        leading = { RowIcon(icon) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    null,
                    tint = if (ok) Dadal.Success else Dadal.Flame,
                )
            }
        },
        onClick = onFix,
    )
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { openAppDetails(context) }
}

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openAppNotificationSettings(context: Context) {
    open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    val intent = if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
    open(context, intent)
}

private fun openXiaomiAutostart(context: Context) {
    val intent = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { openAppDetails(context) }
}
