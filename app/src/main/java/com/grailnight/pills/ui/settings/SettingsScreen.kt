package com.grailnight.pills.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grailnight.pills.AppContainer
import com.grailnight.pills.R
import com.grailnight.pills.data.Settings
import com.grailnight.pills.ui.AppViewModels
import com.grailnight.pills.ui.components.SectionTitle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = container.settings.settings

    fun setRepeat(minutes: Int) = update { it.copy(repeatMinutes = minutes) }

    fun setSnooze(minutes: Int) = update { it.copy(snoozeMinutes = minutes) }

    fun canNotify(): Boolean = container.notifier.canNotify()

    fun canScheduleExact(): Boolean = container.scheduler.canScheduleExact()

    fun sendTest() = container.notifier.showTest()

    private fun update(transform: (Settings) -> Settings) {
        container.settings.update(transform)
        container.appScope.launch { container.repository.rescheduleAll() }
    }
}

private val RepeatOptions = listOf(0, 10, 15, 30, 60)
private val SnoozeOptions = listOf(5, 10, 15, 30)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = AppViewModels.Factory)) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(viewModel.canNotify()) }
    var canExact by remember { mutableStateOf(viewModel.canScheduleExact()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = viewModel.canNotify()
        canExact = viewModel.canScheduleExact()
    }

    val openNotificationSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = viewModel.canNotify()
        if (!granted) openNotificationSettings()
    }

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = statusBar + 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        Card(stringResource(R.string.settings_reminders)) {
            OptionGroup(
                title = stringResource(R.string.settings_repeat),
                subtitle = stringResource(R.string.settings_repeat_hint),
                options = RepeatOptions,
                selected = settings.repeatMinutes,
                label = { if (it == 0) stringResource(R.string.settings_repeat_off) else stringResource(R.string.settings_minutes, it) },
                onSelect = viewModel::setRepeat,
            )
            OptionGroup(
                title = stringResource(R.string.settings_snooze),
                subtitle = stringResource(R.string.settings_snooze_hint),
                options = SnoozeOptions,
                selected = settings.snoozeMinutes,
                label = { stringResource(R.string.settings_minutes, it) },
                onSelect = viewModel::setSnooze,
            )
        }

        Card(stringResource(R.string.settings_permissions)) {
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_notifications),
                granted = canNotify,
                grantedText = stringResource(R.string.settings_granted),
                deniedText = stringResource(R.string.settings_notifications_off),
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings()
                    }
                },
            )
            PermissionRow(
                icon = Icons.Rounded.Alarm,
                title = stringResource(R.string.settings_exact),
                granted = canExact,
                grantedText = stringResource(R.string.settings_granted),
                deniedText = stringResource(R.string.settings_exact_off),
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    }
                },
            )
            OutlinedButton(onClick = viewModel::sendTest, enabled = canNotify, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_test))
            }
        }

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        Text(
            stringResource(R.string.settings_version, version.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionTitle(title)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun OptionGroup(
    title: String,
    subtitle: String,
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    onFix: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 4.dp).height(16.dp),
                )
                Text(
                    if (granted) grantedText else deniedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!granted) {
            FilledTonalButton(onClick = onFix) { Text(stringResource(R.string.settings_allow)) }
        }
    }
}
