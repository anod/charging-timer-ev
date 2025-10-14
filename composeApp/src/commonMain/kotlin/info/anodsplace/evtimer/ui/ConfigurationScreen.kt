package info.anodsplace.evtimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingViewEvent
import info.anodsplace.evtimer.data.ChargingViewState
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

@Composable
fun ConfigurationScreen(
    viewState: ChargingViewState,
    onEvent: (ChargingViewEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = viewState.settings
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Charging Timer for EV",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Battery Capacity (collapsible, collapsed by default)
        var batteryExpanded by remember { mutableStateOf(false) }
        val arrowRotation by animateFloatAsState(
            targetValue = if (batteryExpanded) 90f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "batteryArrowRotation"
        )
        SettingCard(verticalSpacing = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { batteryExpanded = !batteryExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Battery Capacity",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "\u25b6",
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
            // Always show current capacity summary
            Text("${settings.batteryCapacity.roundToInt()} kWh")
            AnimatedVisibility(
                visible = batteryExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Slider(
                    value = settings.batteryCapacity,
                    onValueChange = { onEvent(ChargingViewEvent.UpdateBatteryCapacity(it)) },
                    valueRange = 20f..120f,
                    steps = 19
                )
            }
        }
        
        // Charging Power
        SettingCard(verticalSpacing = 12.dp) {
            var showCustomPowerDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Charging Power: ${settings.chargingPower} kW",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "+",
                    modifier = Modifier
                        .clickable { showCustomPowerDialog = true }
                        .padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            ChargingPowerChips(
                powers = settings.availablePowers,
                selectedPower = settings.chargingPower,
                onPowerSelected = { onEvent(ChargingViewEvent.UpdateChargingPower(it)) }
            )

            if (showCustomPowerDialog) {
                CustomPowerDialog(
                    onDismiss = { showCustomPowerDialog = false },
                    onConfirm = { power ->
                        onEvent(ChargingViewEvent.AddCustomPower(power))
                        showCustomPowerDialog = false
                    }
                )
            }
        }

        // Start Percentage (refactored)
        PercentageSettingCard(
            label = "Start",
            value = settings.startPercent,
            valueRange = 0..100,
            onValueChange = { onEvent(ChargingViewEvent.UpdateStartPercent(it)) }
        )
        // Max Percentage (refactored)
        PercentageSettingCard(
            label = "Max",
            value = settings.maxPercent,
            valueRange = 0..100,
            onValueChange = { onEvent(ChargingViewEvent.UpdateMaxPercent(it)) }
        )

        if (settings.startPercent >= settings.maxPercent) {
            Text(
                text = "Start is less than Max",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onEvent(ChargingViewEvent.StartCharging) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Start Timer", fontSize = 18.sp)
        }
    }
}

@Composable
private fun PercentageSettingCard(
    label: String,
    value: Float,
    valueRange: IntRange,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var text by remember(value) { mutableStateOf(value.roundToInt().toString()) }
    var isError by remember { mutableStateOf(false) }
    var previousError by remember { mutableStateOf(false) }

    val min = valueRange.first
    val max = valueRange.last

    SettingCard(modifier = modifier, verticalSpacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$label current value ${value.roundToInt()} percent" }
            )
            TextButton(
                onClick = { onValueChange((value - 1).coerceAtLeast(min.toFloat())) },
                enabled = value > min,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = "Decrease $label" }
            ) { Text("-") }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    val digits = new.filter { it.isDigit() }.take(3)
                    text = digits
                    val intVal = digits.toIntOrNull()
                    val valid = intVal != null && intVal in valueRange
                    isError = digits.isNotEmpty() && !valid
                    if (isError && !previousError) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    previousError = isError
                    if (valid) {
                        onValueChange(intVal.toFloat())
                    }
                },
                label = { Text("Set") },
                singleLine = true,
                isError = isError,
                supportingText = {
                    AnimatedVisibility(visible = isError) {
                        Text(
                            text = "Must be between $min and $max",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { contentDescription = "$label error value must be between $min and $max" }
                        )
                    }
                },
                suffix = {
                    Text("%", modifier = Modifier.semantics { contentDescription = "percent sign" })
                },
                modifier = Modifier
                    .width(96.dp)
                    .semantics { contentDescription = "$label input field" }
            )
            TextButton(
                onClick = { onValueChange((value + 1).coerceAtMost(max.toFloat())) },
                enabled = value < max,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = "Increase $label" }
            ) { Text("+") }
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(min.toFloat(), max.toFloat())) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0), // 1% increments
            modifier = Modifier.semantics { contentDescription = "$label slider value ${value.roundToInt()} percent" }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChargingPowerChips(
    powers: List<Float>,
    selectedPower: Float,
    onPowerSelected: (Float) -> Unit,
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
    }
}

@Composable
fun CustomPowerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var powerText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Power") },
        text = {
            OutlinedTextField(
                value = powerText,
                onValueChange = { powerText = it },
                label = { Text("Power (kW)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    powerText.toFloatOrNull()?.let { power ->
                        if (power > 0) {
                            onConfirm(power)
                        }
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview
@Composable
fun ConfigurationScreenPreview() {
    val dummyState = ChargingViewState(
        settings = info.anodsplace.evtimer.data.ChargingSettings(
            batteryCapacity = 75f,
            chargingPower = 11f,
            availablePowers = listOf(3.6f, 7.2f, 11f, 22f),
            startPercent = 30f,
            maxPercent = 80f
        )
    )
    MaterialTheme {
        Surface {
            ConfigurationScreen(viewState = dummyState, onEvent = {})
        }
    }
}
