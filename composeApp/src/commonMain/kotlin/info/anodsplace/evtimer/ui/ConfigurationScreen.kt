package info.anodsplace.evtimer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingViewModel
import kotlin.math.roundToInt

@Composable
fun ConfigurationScreen(
    viewModel: ChargingViewModel,
    onStartCharging: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = viewModel.settings
    
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
        
        // Battery Capacity
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Battery Capacity: ${settings.batteryCapacity.roundToInt()} kWh")
                Slider(
                    value = settings.batteryCapacity,
                    onValueChange = { viewModel.updateBatteryCapacity(it) },
                    valueRange = 20f..120f,
                    steps = 19
                )
            }
        }
        
        // Charging Power
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Charging Power: ${settings.chargingPower} kW")
                
                var showCustomPowerDialog by remember { mutableStateOf(false) }
                
                ChargingPowerChips(
                    powers = settings.availablePowers,
                    selectedPower = settings.chargingPower,
                    onPowerSelected = { viewModel.updateChargingPower(it) },
                    onAddCustom = { showCustomPowerDialog = true }
                )
                
                if (showCustomPowerDialog) {
                    CustomPowerDialog(
                        onDismiss = { showCustomPowerDialog = false },
                        onConfirm = { power ->
                            viewModel.addCustomPower(power)
                            viewModel.updateChargingPower(power)
                            showCustomPowerDialog = false
                        }
                    )
                }
            }
        }
        
        // Start Percentage
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Start %: ${settings.startPercent.roundToInt()}%")
                Slider(
                    value = settings.startPercent,
                    onValueChange = { viewModel.updateStartPercent(it) },
                    valueRange = 0f..100f,
                    steps = 19
                )
            }
        }
        
        // Max Percentage
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Max %: ${settings.maxPercent.roundToInt()}%")
                Slider(
                    value = settings.maxPercent,
                    onValueChange = { viewModel.updateMaxPercent(it) },
                    valueRange = 0f..100f,
                    steps = 19
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onStartCharging,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = settings.startPercent < settings.maxPercent
        ) {
            Text("Start Charging", fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingPowerChips(
    powers: List<Float>,
    selectedPower: Float,
    onPowerSelected: (Float) -> Unit,
    onAddCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        powers.forEach { power ->
            FilterChip(
                selected = power == selectedPower,
                onClick = { onPowerSelected(power) },
                label = { Text("${power} kW") }
            )
        }
        
        FilterChip(
            selected = false,
            onClick = onAddCustom,
            label = { Text("+") }
        )
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
