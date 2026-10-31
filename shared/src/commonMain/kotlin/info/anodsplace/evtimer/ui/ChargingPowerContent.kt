package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ConfigurationViewEvent

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
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        powers.forEach { power ->
            FilterChip(
                selected = power == selectedPower,
                onClick = { onPowerSelected(power) },
                label = { Text("$power kW") }
            )
        }
        FilterChip(
            selected = false,
            label = { Text("+") },
            onClick = onAddCustomPower
        )
    }
}

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
        title = { Text("Add Custom Power") },
        text = {
            OutlinedTextField(
                value = powerText,
                onValueChange = { powerText = it },
                label = { Text("Power (kW)") },
                isError = powerText.isNotEmpty() && !validPower,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(
                enabled = validPower,
                onClick = {
                    onConfirm(requireNotNull(power))
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
