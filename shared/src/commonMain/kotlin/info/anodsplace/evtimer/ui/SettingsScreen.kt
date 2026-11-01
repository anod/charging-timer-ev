package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun SettingsScreen(
    notifyAt80: Boolean?,
    onNotifyAt80Change: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    presets: List<ChargingPreset>,
    onSavePreset: (ChargingPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMovePreset: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxSize()) {
        SettingsHeader(stringResource(Res.string.settings_title), onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PresetManagement(presets, onSavePreset, onDeletePreset, onMovePreset)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = notifyAt80 == true,
                    enabled = notifyAt80 != null,
                    role = Role.Switch,
                    onValueChange = onNotifyAt80Change
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.notify_at_80), modifier = Modifier.weight(1f))
                Switch(checked = notifyAt80 == true, onCheckedChange = null, enabled = notifyAt80 != null)
            }
            HorizontalDivider()
            Text(stringResource(Res.string.settings_about), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.app_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.settings_version, APP_VERSION))
            TextButton(onClick = { uriHandler.openUri("https://github.com/anod/charging-timer-ev") }) {
                Text(stringResource(Res.string.settings_source))
            }
            TextButton(onClick = {
                uriHandler.openUri("https://github.com/anod/charging-timer-ev/blob/main/docs/privacy.md")
            }) {
                Text(stringResource(Res.string.settings_privacy))
            }
            TextButton(onClick = onOpenLicenses) {
                Text(stringResource(Res.string.settings_oss_licenses))
            }
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
