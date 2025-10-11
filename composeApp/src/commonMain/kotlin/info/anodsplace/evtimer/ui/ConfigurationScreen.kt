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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingViewEvent
import info.anodsplace.evtimer.data.ChargingViewState
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
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
            text = "EV Charging Timer",
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
                    text = "▶",
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
        
        // Start Percentage
        SettingCard(verticalSpacing = 8.dp) {
            Text("Start %: ${settings.startPercent.roundToInt()}%")
            Slider(
                value = settings.startPercent,
                onValueChange = { onEvent(ChargingViewEvent.UpdateStartPercent(it)) },
                valueRange = 0f..100f,
                steps = 19
            )
        }
        
        // Max Percentage
        SettingCard(verticalSpacing = 8.dp) {
            Text("Max %: ${settings.maxPercent.roundToInt()}%")
            Slider(
                value = settings.maxPercent,
                onValueChange = { onEvent(ChargingViewEvent.UpdateMaxPercent(it)) },
                valueRange = 0f..100f,
                steps = 19
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { onEvent(ChargingViewEvent.StartCharging(now = Clock.System.now().toEpochMilliseconds())) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = settings.startPercent < settings.maxPercent
        ) {
            Text("Start Charging", fontSize = 18.sp)
        }
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
