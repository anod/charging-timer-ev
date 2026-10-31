package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import info.anodsplace.evtimer.data.formatPower
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.ac
import info.anodsplace.evtimer.generated.resources.add
import info.anodsplace.evtimer.generated.resources.add_custom_power
import info.anodsplace.evtimer.generated.resources.add_custom_power_description
import info.anodsplace.evtimer.generated.resources.cancel
import info.anodsplace.evtimer.generated.resources.dc
import info.anodsplace.evtimer.generated.resources.power_kw_label
import info.anodsplace.evtimer.generated.resources.power_value
import info.anodsplace.evtimer.generated.resources.unit_kw
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChargingPowerContent(
    settings: info.anodsplace.evtimer.data.ChargingSettings,
    onEvent: (ConfigurationViewEvent) -> Unit,
) {
    var showCustomPowerDialog by remember { mutableStateOf(false) }

    ChargingPowerChips(
        powers = settings.availablePowers,
        selectedPower = settings.chargingPower,
        onPowerSelected = { onEvent(ConfigurationViewEvent.UpdateChargingPower(it)) },
        onAddCustomPower = { showCustomPowerDialog = true }
    )

    if (showCustomPowerDialog) {
        CustomPowerDialog(
            onDismiss = { showCustomPowerDialog = false },
            onConfirm = { power ->
                onEvent(ConfigurationViewEvent.AddCustomPower(power))
                showCustomPowerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChargingPowerChips(
    powers: List<Float>,
    selectedPower: Float,
    onPowerSelected: (Float) -> Unit,
    onAddCustomPower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addPowerDescription = stringResource(Res.string.add_custom_power_description)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        powers.forEach { power ->
            FilterChip(
                selected = power == selectedPower,
                onClick = { onPowerSelected(power) },
                label = {
                    Text(
                        stringResource(
                            Res.string.power_value,
                            formatPower(power),
                            stringResource(Res.string.unit_kw)
                        )
                    )
                }
            )
        }
        FilterChip(
            selected = false,
            label = { Text("+") },
            onClick = onAddCustomPower,
            modifier = Modifier.semantics { contentDescription = addPowerDescription }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomPowerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var powerText by remember { mutableStateOf("") }
    val power = powerText.replace(',', '.').toFloatOrNull()
    val validPower = power != null && power.isFinite() && power > 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_custom_power)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = powerText,
                    onValueChange = { powerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.power_kw_label)) },
                    singleLine = true,
                    isError = powerText.isNotEmpty() && !validPower,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                listOf(
                    stringResource(Res.string.ac) to listOf("2.3", "3.6", "7", "11", "22"),
                    stringResource(Res.string.dc) to listOf("50", "100", "150")
                ).forEach { (label, suggestions) ->
                    Text(label)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            FilterChip(
                                selected = power == suggestion.toFloat(),
                                onClick = { powerText = suggestion },
                                label = {
                                    Text(
                                        stringResource(
                                            Res.string.power_value,
                                            formatPower(suggestion.toFloat()),
                                            stringResource(Res.string.unit_kw)
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validPower,
                onClick = {
                    onConfirm(requireNotNull(power))
                }
            ) { Text(stringResource(Res.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
