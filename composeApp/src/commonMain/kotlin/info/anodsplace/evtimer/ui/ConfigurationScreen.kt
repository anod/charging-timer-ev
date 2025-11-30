package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingSettings
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Charging Timer for EV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

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
                        onValueChange = { onEvent(ChargingViewEvent.UpdateBatteryCapacity(it)) },
                        suffix = "kWh",
                        unitDescription = "kilowatt hours",
                        step = 1
                    )
                }

                // Max Percentage (collapsed by default, placed above Power as requested)
                CollapsibleSettingCard(
                    title = "Max",
                    summary = { valueState ->
                        val maxKwh = (valueState.settings.batteryCapacity * valueState.settings.maxPercent / 100f).roundToInt()
                        "Max: ${valueState.settings.maxPercent.roundToInt()}% ($maxKwh kWh)"
                    },
                    viewState = viewState,
                    initiallyExpanded = false,
                    modifier = Modifier
                ) {
                    PercentageSettingContent(
                        label = "Max",
                        value = viewState.settings.maxPercent,
                        valueRange = 0..100,
                        onValueChange = { onEvent(ChargingViewEvent.UpdateMaxPercent(it)) }
                    )
                }

                // Start Percentage (expanded by default)
                CollapsibleSettingCard(
                    title = "Start",
                    summary = { valueState ->
                        val startKwh = (valueState.settings.batteryCapacity * valueState.settings.startPercent / 100f).roundToInt()
                        "Start: ${valueState.settings.startPercent.roundToInt()}% ($startKwh kWh)"
                    },
                    viewState = viewState,
                    initiallyExpanded = true,
                    modifier = Modifier
                ) {
                    PercentageSettingContent(
                        label = "Start",
                        value = viewState.settings.startPercent,
                        valueRange = 0..100,
                        onValueChange = { onEvent(ChargingViewEvent.UpdateStartPercent(it)) }
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
    val dummyState = ChargingViewState(
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
fun ConfigurationScreenErrorEqualPreview() {
    val dummyState = ChargingViewState(
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