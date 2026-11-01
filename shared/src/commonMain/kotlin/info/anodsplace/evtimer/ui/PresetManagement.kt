package info.anodsplace.evtimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.MAX_PRESET_NAME_CODE_POINTS
import info.anodsplace.evtimer.data.PresetNameError
import info.anodsplace.evtimer.data.normalizeAndValidatePresetName
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PresetManagement(
    presets: List<ChargingPreset>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit
) {
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    Text(stringResource(Res.string.settings_presets), style = MaterialTheme.typography.titleLarge)
    presets.forEachIndexed { index, preset ->
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { renamingId = preset.id }) {
                    Text(stringResource(Res.string.rename_preset))
                }
            }
            Row {
                TextButton(onClick = { onMove(preset.id, -1) }, enabled = index > 0) {
                    Text(stringResource(Res.string.move_up))
                }
                TextButton(onClick = { onMove(preset.id, 1) }, enabled = index < presets.lastIndex) {
                    Text(stringResource(Res.string.move_down))
                }
                TextButton(onClick = { deletingId = preset.id }, enabled = presets.size > 1) {
                    Text(stringResource(Res.string.delete))
                }
            }
            if (presets.size == 1) {
                Text(stringResource(Res.string.last_preset_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    presets.firstOrNull { it.id == renamingId }?.let { preset ->
        PresetRenameDialog(preset, presets,
            onDismiss = { renamingId = null },
            onRename = { onRename(preset.id, it); renamingId = null })
    }
    presets.firstOrNull { it.id == deletingId }?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(Res.string.delete_preset)) },
            text = { Text(stringResource(Res.string.delete_preset_confirmation, preset.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(preset.id); deletingId = null }) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}

@Composable
internal fun PresetRenameDialog(
    preset: ChargingPreset,
    presets: List<ChargingPreset>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
    val validation = normalizeAndValidatePresetName(name, presets.filterNot { it.id == preset.id }.map { it.name })
    val valid = name == preset.name || validation.isValid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_preset)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.preset_name)) },
                    singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth())
                if (!valid) {
                    val error = when (validation.error) {
                        PresetNameError.TOO_LONG -> stringResource(Res.string.preset_name_too_long, MAX_PRESET_NAME_CODE_POINTS)
                        PresetNameError.ALREADY_EXISTS -> stringResource(Res.string.preset_name_exists)
                        else -> stringResource(Res.string.preset_name_required)
                    }
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                presetEmojiSuggestions.forEach { suggestions ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { emoji ->
                            FilterChip(selected = false, onClick = { name += emoji }, label = { Text(emoji) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onRename(if (name == preset.name) preset.name else validation.name)
            }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}
