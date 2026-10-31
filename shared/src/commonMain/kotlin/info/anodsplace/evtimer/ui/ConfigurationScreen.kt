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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import info.anodsplace.evtimer.data.ConfigurationViewState
import info.anodsplace.evtimer.data.ChargingRepositoryImpl
import info.anodsplace.evtimer.data.MAX_PRESET_NAME_CODE_POINTS
import info.anodsplace.evtimer.data.PresetNameError
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.data.formatPower
import info.anodsplace.evtimer.data.normalizeAndValidatePresetName
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.*
import kotlin.math.roundToInt

private val presetEmojiSuggestions = listOf(
    listOf("\uD83C\uDFE0", "\uD83C\uDFE2"), // Home, work
    listOf("\uD83D\uDE97", "\uD83D\uDE99", "\uD83D\uDE95"), // Cars
    listOf("\uD83D\uDD34", "\uD83D\uDFE2", "\uD83D\uDD35", "\uD83D\uDFE1") // Colors
)

@Composable
fun ConfigurationScreen(
    viewState: ConfigurationViewState,
    onEvent: (ConfigurationViewEvent) -> Unit,
    modifier: Modifier = Modifier,
    initiallyShowPresetMenu: Boolean = false
) {
    val settings = viewState.settings
    val presets = viewState.presetConfiguration
    var cardExpansion by remember(presets.selectedId) {
        mutableStateOf(expandedCards(presets.selectedId))
    }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }
    val presetNameValidation = remember(presetName, presets.presets) {
        normalizeAndValidatePresetName(presetName, presets.presets.map { it.name })
    }
    var showPresetMenu by remember(presets.selectedId, presets.isEdited) {
        mutableStateOf(initiallyShowPresetMenu && presets.isEdited)
    }

    if (showNameDialog) {
        val error = when (presetNameValidation.error) {
            PresetNameError.EMPTY, null -> null
            PresetNameError.TOO_LONG -> stringResource(Res.string.preset_name_too_long, MAX_PRESET_NAME_CODE_POINTS)
            PresetNameError.ALREADY_EXISTS -> stringResource(Res.string.preset_name_exists)
        }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(Res.string.save_as_new_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text(stringResource(Res.string.preset_name)) },
                        singleLine = true,
                        isError = error != null
                    )
                    presetEmojiSuggestions.forEach { suggestions ->
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            suggestions.forEach { emoji ->
                                FilterChip(
                                    selected = false,
                                    onClick = { presetName += emoji },
                                    label = { Text(emoji) }
                                )
                            }
                        }
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
                ) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    // Scaffold provides a persistent bottom bar with the Start button while the content scrolls.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            val invalidRange = settings.startPercent >= settings.maxPercent
            val startErrorDescription = stringResource(Res.string.start_percent_error_description)
            Column(modifier = Modifier.fillMaxWidth()) {
                if (invalidRange) {
                    Text(
                        text = stringResource(Res.string.start_percent_exceeds_max),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                            .semantics { contentDescription = startErrorDescription }
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
                    Text(stringResource(Res.string.start_timer), fontSize = 18.sp)
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
                text = stringResource(Res.string.app_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val saveAsPresetDescription = stringResource(Res.string.save_as_new_preset_description)
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
                            label = {
                                Text(
                                    if (preset.id == "custom") {
                                        stringResource(Res.string.default_preset)
                                    } else if (selected && presets.isEdited) {
                                        stringResource(Res.string.edited_preset_name, preset.name)
                                    } else {
                                        preset.name
                                    }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = selected && presets.isEdited && showPresetMenu,
                            onDismissRequest = { showPresetMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.update_preset)) },
                                onClick = {
                                    showPresetMenu = false
                                    onEvent(ConfigurationViewEvent.UpdatePreset)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.reset)) },
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
                    modifier = Modifier.semantics { contentDescription = saveAsPresetDescription }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = viewState.notifyAt80,
                    enabled = !viewState.isLoading,
                    role = Role.Switch,
                    onValueChange = { onEvent(ConfigurationViewEvent.SetNotifyAt80(it)) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.notify_at_80), modifier = Modifier.weight(1f))
                Switch(checked = viewState.notifyAt80, onCheckedChange = null, enabled = !viewState.isLoading)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery Capacity (collapsed by default)
                CollapsibleSettingCard(
                    title = stringResource(Res.string.battery_capacity),
                    summary = { valueState ->
                        stringResource(
                            Res.string.battery_summary,
                            formatLocaleNumber(valueState.settings.batteryCapacity, 0, 0),
                            stringResource(Res.string.unit_kwh)
                        )
                    },
                    viewState = viewState,
                    expanded = cardExpansion.battery,
                    onExpandedChange = { cardExpansion = cardExpansion.copy(battery = it) },
                    modifier = Modifier
                ) {
                    PercentageSettingContent(
                        label = stringResource(Res.string.battery_capacity),
                        value = viewState.settings.batteryCapacity,
                        valueRange = 10..120,
                        onValueChange = { onEvent(ConfigurationViewEvent.UpdateBatteryCapacity(it)) },
                        suffix = stringResource(Res.string.unit_kwh),
                        unitDescription = stringResource(Res.string.unit_kilowatt_hours),
                        step = 1
                    )
                }

                // Max Percentage (collapsed by default, placed above Power as requested)
                CollapsibleSettingCard(
                    title = stringResource(Res.string.max),
                    summary = { valueState ->
                        val maxKwh = ChargingCalculator.calculateKwh(valueState.settings.batteryCapacity, valueState.settings.maxPercent)
                        stringResource(
                            Res.string.max_summary,
                            stringResource(Res.string.max),
                            formatLocaleNumber(valueState.settings.maxPercent.roundToInt().toFloat(), 0, 0),
                            formatLocaleNumber(maxKwh.toFloat(), 0, 2),
                            stringResource(Res.string.unit_kwh)
                        )
                    },
                    viewState = viewState,
                    expanded = cardExpansion.max,
                    onExpandedChange = { cardExpansion = cardExpansion.copy(max = it) },
                    modifier = Modifier
                ) {
                    val maxKwh = remember(settings.batteryCapacity, settings.maxPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, settings.maxPercent)
                    }
                    PercentageSettingContent(
                        label = stringResource(Res.string.max),
                        value = viewState.settings.maxPercent,
                        valueRange = 0..100,
                        onValueChange = { onEvent(ConfigurationViewEvent.UpdateMaxPercent(it)) },
                        additionalInfo = stringResource(
                            Res.string.power_value,
                            formatLocaleNumber(maxKwh.toFloat(), 0, 2),
                            stringResource(Res.string.unit_kwh)
                        )
                    )
                }

                // Start Percentage (expanded by default)
                CollapsibleSettingCard(
                    title = stringResource(Res.string.start),
                    summary = { valueState ->
                        val startKwh = ChargingCalculator.calculateKwh(valueState.settings.batteryCapacity, valueState.settings.startPercent)
                        stringResource(
                            Res.string.start_summary,
                            stringResource(Res.string.start),
                            formatLocaleNumber(valueState.settings.startPercent.roundToInt().toFloat(), 0, 0),
                            formatLocaleNumber(startKwh.toFloat(), 0, 2),
                            stringResource(Res.string.unit_kwh)
                        )
                    },
                    viewState = viewState,
                    expanded = cardExpansion.start,
                    onExpandedChange = { cardExpansion = cardExpansion.copy(start = it) },
                    modifier = Modifier
                ) {
                    val startKwh = remember(settings.batteryCapacity, settings.startPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, settings.startPercent)
                    }
                    PercentageSettingContent(
                        label = stringResource(Res.string.start),
                        value = viewState.settings.startPercent,
                        valueRange = 0..100,
                        onValueChange = { onEvent(ConfigurationViewEvent.UpdateStartPercent(it)) },
                        additionalInfo = stringResource(
                            Res.string.power_value,
                            formatLocaleNumber(startKwh.toFloat(), 0, 2),
                            stringResource(Res.string.unit_kwh)
                        )
                    )
                }

                // Charging Power (expanded by default, moved below Max)
                CollapsibleSettingCard(
                    title = stringResource(Res.string.charging_power),
                    summary = { valueState ->
                        stringResource(
                            Res.string.power_summary,
                            formatPower(valueState.settings.chargingPower),
                            stringResource(Res.string.unit_kw)
                        )
                    },
                    viewState = viewState,
                    expanded = cardExpansion.power,
                    onExpandedChange = { cardExpansion = cardExpansion.copy(power = it) },
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
    MaterialTheme {
        ConfigurationScreen(
            viewState = dummyState.copy(selectedPresetId = ChargingRepositoryImpl.CUSTOM_ID),
            onEvent = {}
        )
    }
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