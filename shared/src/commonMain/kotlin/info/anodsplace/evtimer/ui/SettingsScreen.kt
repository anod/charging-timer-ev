package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.APP_VERSION
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun SettingsScreen(
    notifyAt80: Boolean?,
    onNotifyAt80Change: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPresets: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Res.drawable.ic_back), stringResource(Res.string.settings_back))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.settings_title),
                Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp),
                style = MaterialTheme.typography.headlineLarge
            )
            SettingsSection(stringResource(Res.string.settings_charging)) {
                SettingsRow(stringResource(Res.string.settings_presets), Res.drawable.ic_presets,
                    stringResource(Res.string.settings_presets_summary), onOpenPresets)
                SettingsRow(stringResource(Res.string.history_title), Res.drawable.ic_history,
                    stringResource(Res.string.settings_history_summary), onOpenHistory)
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.notify_at_80)) },
                    supportingContent = { Text(stringResource(Res.string.settings_notify_summary)) },
                    leadingContent = { SettingsIcon(Res.drawable.ic_notifications) },
                    trailingContent = {
                        Switch(checked = notifyAt80 == true, onCheckedChange = null, enabled = notifyAt80 != null)
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = notifyAt80 == true, enabled = notifyAt80 != null,
                        role = Role.Switch, onValueChange = onNotifyAt80Change
                    )
                )
            }
            SettingsSection(stringResource(Res.string.settings_about)) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.app_title)) },
                    supportingContent = { Text(stringResource(Res.string.settings_version, APP_VERSION)) },
                    leadingContent = { SettingsIcon(Res.drawable.ic_info) },
                    colors = settingsRowColors()
                )
                SettingsRow(stringResource(Res.string.settings_source), Res.drawable.ic_code,
                    onClick = { uriHandler.openUri("https://github.com/anod/charging-timer-ev") })
                SettingsRow(stringResource(Res.string.settings_privacy), Res.drawable.ic_privacy,
                    onClick = { uriHandler.openUri("https://github.com/anod/charging-timer-ev/blob/main/docs/privacy.md") })
                SettingsRow(stringResource(Res.string.settings_oss_licenses), Res.drawable.ic_document,
                    onClick = onOpenLicenses)
            }
        }
    }
}

@Composable
private fun settingsRowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
private fun SettingsIcon(icon: DrawableResource) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SettingsRow(
    title: String,
    icon: DrawableResource,
    supportingText: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supportingText?.let { { Text(it) } },
        leadingContent = { SettingsIcon(icon) },
        colors = settingsRowColors(),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 16.dp, top = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column { content() }
        }
    }
}

@Composable
fun PresetManagementScreen(
    presets: List<ChargingPreset>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onReorder: (List<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        SettingsHeader(stringResource(Res.string.settings_presets), onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(Res.string.settings_presets_help),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PresetManagement(presets, onRename, onDelete, onMove, onReorder, showTitle = false)
        }
    }
}

@Composable
internal fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(Res.drawable.ic_back), stringResource(Res.string.settings_back))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
