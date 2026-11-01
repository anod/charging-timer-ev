package info.anodsplace.evtimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.MAX_PRESET_NAME_CODE_POINTS
import info.anodsplace.evtimer.data.PresetNameError
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.data.formatPower
import info.anodsplace.evtimer.data.normalizeAndValidatePresetName
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun PresetManagement(
    presets: List<ChargingPreset>,
    onSave: (ChargingPreset) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.settings_presets), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f))
        TextButton(onClick = { addingId = Uuid.random().toString() }, enabled = presets.isNotEmpty()) {
            Text(stringResource(Res.string.add))
        }
    }
    presets.forEachIndexed { index, preset ->
        val fixed = preset.id == ChargingRepositoryImpl.CUSTOM_ID
        val name = if (fixed) stringResource(Res.string.default_preset) else preset.name
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { editingId = preset.id }) { Text(stringResource(Res.string.edit)) }
            }
            Text(stringResource(Res.string.preset_values,
                formatPower(preset.settings.batteryCapacity), formatPower(preset.settings.chargingPower),
                formatLocaleNumber(preset.settings.maxPercent, 0, 2)),
                style = MaterialTheme.typography.bodyMedium)
            if (fixed) {
                Text(stringResource(Res.string.fixed_preset), style = MaterialTheme.typography.bodySmall)
            } else {
                Row {
                    TextButton(onClick = { onMove(preset.id, -1) }, enabled = index > 1) {
                        Text(stringResource(Res.string.move_up))
                    }
                    TextButton(onClick = { onMove(preset.id, 1) }, enabled = index < presets.lastIndex) {
                        Text(stringResource(Res.string.move_down))
                    }
                    TextButton(onClick = { deletingId = preset.id }) {
                        Text(stringResource(Res.string.delete))
                    }
                }
            }
        }
    }
    val editing = presets.firstOrNull { it.id == editingId }
    val newId = addingId
    if (editing != null || newId != null) {
        val initial = editing ?: ChargingPreset(requireNotNull(newId), "", ChargingSettings.DEFAULT)
        PresetEditor(
            initial = initial,
            isNew = editing == null,
            presets = presets,
            onDismiss = { editingId = null; addingId = null },
            onSave = { onSave(it); editingId = null; addingId = null }
        )
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
internal fun PresetEditor(
    initial: ChargingPreset,
    isNew: Boolean,
    presets: List<ChargingPreset>,
    onDismiss: () -> Unit,
    onSave: (ChargingPreset) -> Unit
) {
    val fixed = initial.id == ChargingRepositoryImpl.CUSTOM_ID
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var capacityText by rememberSaveable(initial.id) { mutableStateOf(initial.settings.batteryCapacity.toString()) }
    var powerText by rememberSaveable(initial.id) { mutableStateOf(initial.settings.chargingPower.toString()) }
    var targetText by rememberSaveable(initial.id) { mutableStateOf(initial.settings.maxPercent.toString()) }
    var powers by rememberSaveable(initial.id) { mutableStateOf(initial.settings.availablePowers) }
    var addingPower by rememberSaveable(initial.id) { mutableStateOf(false) }
    val validation = normalizeAndValidatePresetName(name, presets.filterNot { it.id == initial.id }.map { it.name })
    // Existing names accepted by older releases remain editable until the name changes.
    val nameValid = fixed || name == initial.name && !isNew || validation.isValid
    val capacity = capacityText.replace(',', '.').toFloatOrNull()
    val power = powerText.replace(',', '.').toFloatOrNull()
    val target = targetText.replace(',', '.').toFloatOrNull()
    val capacityValid = capacity != null && capacity.isFinite() && capacity > 0f
    val powerValid = power != null && power.isFinite() && power > 0f
    val targetValid = target != null && target.isFinite() && target in 0f..100f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) Res.string.save_as_new_preset else Res.string.edit_preset)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fixed) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.preset_name)) },
                        singleLine = true, isError = !nameValid, modifier = Modifier.fillMaxWidth())
                    if (!nameValid) {
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
                PresetNumberField(capacityText, { capacityText = it }, stringResource(Res.string.capacity_kwh_label), capacityValid)
                PresetNumberField(powerText, { powerText = it }, stringResource(Res.string.power_kw_label), powerValid)
                PresetNumberField(targetText, { targetText = it }, stringResource(Res.string.target_percent), targetValid)
                Text(stringResource(Res.string.settings_power_options), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.power_options_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    powers.forEach { option ->
                        val removal = stringResource(Res.string.remove_power, formatPower(option))
                        InputChip(
                            selected = false,
                            enabled = powers.size > 1,
                            onClick = {
                                val updated = initial.settings.copy(
                                    chargingPower = if (powerValid) requireNotNull(power) else initial.settings.chargingPower,
                                    availablePowers = powers
                                ).withRemovedPower(option)
                                powers = updated.availablePowers
                                if (power == option) powerText = updated.chargingPower.toString()
                            },
                            label = { Text(stringResource(Res.string.removable_power, formatPower(option))) },
                            modifier = Modifier.semantics { contentDescription = removal }
                        )
                    }
                    TextButton(onClick = { addingPower = true }) { Text(stringResource(Res.string.add)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = nameValid && capacityValid && powerValid && targetValid, onClick = {
                onSave(initial.copy(
                    name = if (fixed || name == initial.name) initial.name else validation.name,
                    settings = initial.settings.copy(
                        batteryCapacity = requireNotNull(capacity), chargingPower = requireNotNull(power),
                        maxPercent = requireNotNull(target), availablePowers = powers
                    )
                ))
            }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
    if (addingPower) {
        CustomPowerDialog(onDismiss = { addingPower = false }, onConfirm = {
            powers = (powers + it).distinct().sorted()
            powerText = it.toString()
            addingPower = false
        })
    }
}

@Composable
private fun PresetNumberField(value: String, onChange: (String) -> Unit, label: String, valid: Boolean) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        isError = !valid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            if (!valid) Text(stringResource(Res.string.preset_value_invalid))
        }
    )
}
