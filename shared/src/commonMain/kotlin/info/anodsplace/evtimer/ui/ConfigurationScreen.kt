package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import info.anodsplace.evtimer.data.ConfigurationViewState
import info.anodsplace.evtimer.data.PresetNameError
import info.anodsplace.evtimer.data.normalizeAndValidatePresetName
import kotlin.math.roundToInt

@Composable
fun ConfigurationScreen(
    viewState: ConfigurationViewState,
    onEvent: (ConfigurationViewEvent) -> Unit,
    modifier: Modifier = Modifier,
    initiallyShowPresetMenu: Boolean = false
) {
    val settings = viewState.settings
    val presets = viewState.presetConfiguration
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }
    val presetNameValidation = remember(presetName, presets.presets) {
        normalizeAndValidatePresetName(presetName, presets.presets.map { it.name })
    }
    var showPresetMenu by remember(presets.selectedId, presets.isEdited) {
        mutableStateOf(initiallyShowPresetMenu && presets.isEdited)
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save as new preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset name") },
                        singleLine = true,
                        isError = presetNameValidation.error != null
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", "\uD83C\uDDEE\uD83C\uDDF1", "\uD83D\uDC4D\uD83C\uDFFD", "\u26A1").forEach { emoji ->
                            FilterChip(
                                selected = false,
                                onClick = { presetName += emoji },
                                label = { Text(emoji) }
                            )
                        }
                    }
                    val error = when (presetNameValidation.error) {
                        PresetNameError.EMPTY, null -> null
                        PresetNameError.TOO_LONG -> "Preset name must be at most 32 Unicode code points"
                        PresetNameError.ALREADY_EXISTS -> "A preset with this name already exists"
                    }
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = presetNameValidation.isValid,
                    onClick = {
                        onEvent(ConfigurationViewEvent.SaveAsPreset(presetNameValidation.name))
                        showNameDialog = false
                        presetName = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

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
                    onClick = { onEvent(ConfigurationViewEvent.StartCharging) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Charging Timer for EV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewState.presets.forEach { preset ->
                    val selected = preset.id == presets.selectedId
                    Box {
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected && presets.isEdited) showPresetMenu = true
                                else if (!selected) onEvent(ConfigurationViewEvent.SelectPreset(preset.id))
                            },
                            enabled = !viewState.isLoading,
                            label = { Text(preset.name + if (selected && presets.isEdited) " *" else "") }
                        )
                        DropdownMenu(
                            expanded = selected && presets.isEdited && showPresetMenu,
                            onDismissRequest = { showPresetMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Update preset") },
                                onClick = {
                                    showPresetMenu = false
                                    onEvent(ConfigurationViewEvent.UpdatePreset)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset") },
                                onClick = {
                                    showPresetMenu = false
                                    onEvent(ConfigurationViewEvent.ResetPreset)
                                }
                            )
                        }
                    }
                }
                FilterChip(
                    selected = false,
                    enabled = !viewState.isLoading,
                    onClick = { showNameDialog = true },
                    label = { Text("+") },
                    modifier = Modifier.semantics { contentDescription = "Save as new preset" }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery Capacity (collapsed by default)
                CollapsibleSettingCard(
                    title = "Battery Capacity",
                    summary = { valueState -> "Battery: ${valueState.settings.batteryCapacity.roundToInt()} kWh" },
                    viewState = viewState,
                    initiallyExpanded = false,
                    modifier = Modifier
                ) {
                    PercentageSettingContent(
                        label = "Battery Capacity",
                        value = viewState.settings.batteryCapacity,
                        valueRange = 10..120,
                        onValueChange = { onEvent(ConfigurationViewEvent.UpdateBatteryCapacity(it)) },
                        suffix = "kWh",
                        unitDescription = "kilowatt hours",
                        step = 1
                    )
                }

                // Max Percentage (collapsed by default, placed above Power as requested)
                CollapsibleSettingCard(
                    title = "Max",
                    summary = { valueState ->
                        val maxKwh = ChargingCalculator.calculateKwh(valueState.settings.batteryCapacity, valueState.settings.maxPercent)
                        "Max: ${valueState.settings.maxPercent.roundToInt()}% ($maxKwh kWh)"
                    },
                    viewState = viewState,
                    initiallyExpanded = false,
                    modifier = Modifier
                ) {
                    val maxKwh = remember(settings.batteryCapacity, settings.maxPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, settings.maxPercent)
                    }
                    PercentageSettingContent(
                        label = "Max",
                        value = viewState.settings.maxPercent,
                        valueRange = 0..100,
                        onValueChange = { onEvent(ConfigurationViewEvent.UpdateMaxPercent(it)) },
                        additionalInfo = "$maxKwh kWh"
                    )
                }

                // Start Percentage (expanded by default)
                CollapsibleSettingCard(
                    title = "Start",
                    summary = { valueState ->
                        val startKwh = ChargingCalculator.calculateKwh(valueState.settings.batteryCapacity, valueState.settings.startPercent)
                        "Start: ${valueState.settings.startPercent.roundToInt()}% ($startKwh kWh)"
                    },
                    viewState = viewState,
                    initiallyExpanded = true,
                    modifier = Modifier
                ) {
                    val startKwh = remember(settings.batteryCapacity, settings.startPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, settings.startPercent)
                    }
                    PercentageSettingContent(
                        label = "Start",
                        value = viewState.settings.startPercent,
                        valueRange = 0..100,
                        onValueChange = { onEvent(ConfigurationViewEvent.UpdateStartPercent(it)) },
                        additionalInfo = "$startKwh kWh"
                    )
                }

                // Charging Power (expanded by default, moved below Max)
                CollapsibleSettingCard(
                    title = "Charging Power",
                    summary = { valueState -> "Power: ${valueState.settings.chargingPower} kW" },
                    viewState = viewState,
                    initiallyExpanded = true,
                    modifier = Modifier
                ) {
                    ChargingPowerContent(
                        settings = viewState.settings,
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}


@Preview
@Composable
fun ConfigurationScreenPreview() {
    val dummyState = ConfigurationViewState(
        isLoading = false,
        settings = ChargingSettings(
            batteryCapacity = 75f,
            chargingPower = 11f,
            availablePowers = listOf(3.6f, 7.2f, 11f, 22f),
            startPercent = 30f,
            maxPercent = 80f
        )
    )
    MaterialTheme { ConfigurationScreen(viewState = dummyState, onEvent = {}) }
}

@Preview
@Composable
fun ConfigurationNamedPresetPreview() {
    val settings = ChargingSettings.DEFAULT.copy(batteryCapacity = 75f, chargingPower = 11f, maxPercent = 80f)
    MaterialTheme {
        ConfigurationScreen(
            ConfigurationViewState(
                isLoading = false, settings = settings,
                presets = listOf(
                    ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
                    ChargingPreset("home", "\uD83C\uDFE0 Home", settings)
                ),
                selectedPresetId = "home"
            ), onEvent = {}
        )
    }
}

@Preview
@Composable
fun ConfigurationEditedPresetPreview() = ConfigurationEditedPresetPreviewContent()

@Preview
@Composable
fun ConfigurationEditedPresetMenuPreview() = ConfigurationEditedPresetPreviewContent(showMenu = true)

@Composable
private fun ConfigurationEditedPresetPreviewContent(showMenu: Boolean = false) {
    val settings = ChargingSettings.DEFAULT.copy(batteryCapacity = 75f, maxPercent = 80f)
    MaterialTheme {
        ConfigurationScreen(
            ConfigurationViewState(
                isLoading = false, settings = settings.copy(chargingPower = 22f),
                presets = listOf(
                    ChargingPreset("custom", "Default", ChargingSettings.DEFAULT),
                    ChargingPreset("home", "\uD83C\uDFE0 Home", settings)
                ),
                selectedPresetId = "home"
            ), onEvent = {}, initiallyShowPresetMenu = showMenu
        )
    }
}

@Preview
@Composable
fun ConfigurationScreenErrorEqualPreview() {
    val dummyState = ConfigurationViewState(
        settings = ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 7.2f,
            availablePowers = listOf(3.6f, 7.2f, 11f),
            startPercent = 70f,
            maxPercent = 70f // equal triggers error banner
        )
    )
    MaterialTheme { ConfigurationScreen(viewState = dummyState, onEvent = {}) }
}