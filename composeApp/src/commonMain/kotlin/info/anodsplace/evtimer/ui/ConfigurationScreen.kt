package info.anodsplace.evtimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Scaffold provides a persistent bottom bar with the Start button while the content scrolls.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            val invalidRange = settings.startPercent >= settings.maxPercent
            Column(modifier = Modifier.fillMaxWidth()) {
                if (invalidRange) {
                    Text(
                        text = "Start % is more than Max %",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                            .semantics { contentDescription = "error start must be less than max" }
                            .align(Alignment.CenterHorizontally)
                    )
                }
                Button(
                    onClick = { onEvent(ChargingViewEvent.StartCharging) },
                    enabled = !invalidRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp)
                ) {
                    Text("Start Timer", fontSize = 18.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Charging Timer for EV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Battery Capacity (now uses generic PercentageSettingCard for consistency)
            PercentageSettingCard(
                label = "Battery Capacity",
                value = settings.batteryCapacity,
                valueRange = 20..120,
                onValueChange = { onEvent(ChargingViewEvent.UpdateBatteryCapacity(it)) },
                suffix = "kWh",
                unitDescription = "kilowatt hours",
                step = 5
            )

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

            // Start Percentage
            PercentageSettingCard(
                label = "Start",
                value = settings.startPercent,
                valueRange = 0..100,
                onValueChange = { onEvent(ChargingViewEvent.UpdateStartPercent(it)) }
            )
            // Max Percentage
            PercentageSettingCard(
                label = "Max",
                value = settings.maxPercent,
                valueRange = 0..100,
                onValueChange = { onEvent(ChargingViewEvent.UpdateMaxPercent(it)) }
            )
        }
    }
}

@Composable
private fun PercentageSettingCard(
    label: String,
    value: Float,
    valueRange: IntRange,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "%",
    unitDescription: String = "percent",
    step: Int = 1,
) {
    val haptics = LocalHapticFeedback.current
    var text by remember(value) { mutableStateOf(value.roundToInt().toString()) }
    var isError by remember { mutableStateOf(false) }
    var previousError by remember { mutableStateOf(false) }

    val min = valueRange.first
    val max = valueRange.last
    val clampedStep = if (step <= 0) 1 else step

    fun coerceToStep(v: Float): Int {
        val rounded = (((v - min) / clampedStep).roundToInt() * clampedStep + min)
            .coerceIn(min, max)
        return rounded
    }

    SettingCard(modifier = modifier, verticalSpacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$label current value ${value.roundToInt()} $unitDescription" }
                    .align(Alignment.Top)
            )
            TextButton(
                onClick = { onValueChange((value - clampedStep).coerceAtLeast(min.toFloat())) },
                enabled = value > min,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = "Decrease $label" }
            ) { Text("-") }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    val digits = new.filter { it.isDigit() }.take(4)
                    text = digits
                    val intVal = digits.toIntOrNull()
                    val valid = intVal != null && intVal in valueRange && ((intVal - min) % clampedStep == 0)
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
                            text = "Must be between $min and $max in $clampedStep increments",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { contentDescription = "$label error value must be between $min and $max in $clampedStep increments" }
                        )
                    }
                },
                suffix = {
                    Text(suffix, modifier = Modifier.semantics { contentDescription = suffix })
                },
                modifier = Modifier
                    .width(96.dp)
                    .semantics { contentDescription = "$label input field" }
            )
            TextButton(
                onClick = { onValueChange((value + clampedStep).coerceAtMost(max.toFloat())) },
                enabled = value < max,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = "Increase $label" }
            ) { Text("+") }
        }
        val stepsCount = ((max - min) / clampedStep).coerceAtLeast(1)
        val sliderSteps = (stepsCount - 1).coerceAtLeast(0) // Compose expects discreteValues - 2
        Slider(
            value = value,
            onValueChange = { raw ->
                val stepped = coerceToStep(raw).toFloat()
                if (stepped != value.roundToInt().toFloat()) {
                    onValueChange(stepped)
                }
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = sliderSteps,
            modifier = Modifier.semantics { contentDescription = "$label slider value ${value.roundToInt()} $unitDescription" }
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
        ConfigurationScreen(viewState = dummyState, onEvent = {})
    }
}

@Preview
@Composable
fun ConfigurationScreenErrorEqualPreview() {
    val dummyState = ChargingViewState(
        settings = info.anodsplace.evtimer.data.ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 7.2f,
            availablePowers = listOf(3.6f, 7.2f, 11f),
            startPercent = 70f,
            maxPercent = 70f // equal triggers error
        )
    )
    MaterialTheme { ConfigurationScreen(viewState = dummyState, onEvent = {}) }
}

@Preview
@Composable
fun ConfigurationScreenErrorGreaterPreview() {
    val dummyState = ChargingViewState(
        settings = info.anodsplace.evtimer.data.ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 7.2f,
            availablePowers = listOf(3.6f, 7.2f, 11f),
            startPercent = 80f,
            maxPercent = 60f // greater triggers error
        )
    )
    MaterialTheme { ConfigurationScreen(viewState = dummyState, onEvent = {}) }
}
