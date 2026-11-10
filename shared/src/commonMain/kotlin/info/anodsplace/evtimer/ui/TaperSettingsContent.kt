package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ConfigurationViewEvent
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.apply
import info.anodsplace.evtimer.generated.resources.charging_taper_cap
import info.anodsplace.evtimer.generated.resources.charging_taper_end
import info.anodsplace.evtimer.generated.resources.charging_taper_estimate
import info.anodsplace.evtimer.generated.resources.charging_taper_start
import info.anodsplace.evtimer.generated.resources.duration_hours_minutes
import info.anodsplace.evtimer.generated.resources.hours_short
import info.anodsplace.evtimer.generated.resources.minutes_short
import info.anodsplace.evtimer.generated.resources.power_cap_must_be_positive
import info.anodsplace.evtimer.generated.resources.unit_kw
import info.anodsplace.evtimer.generated.resources.unit_kilowatts
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

@Composable
internal fun TaperSettingsContent(
    settings: ChargingSettings,
    onEvent: (ConfigurationViewEvent) -> Unit
) {
    val startMaximum = (settings.taperEndPercent.toInt() - 1).coerceIn(0, 99)
    val endMinimum = (settings.taperStartPercent.toInt() + 1).coerceIn(1, 100)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PercentageSettingContent(
            label = stringResource(Res.string.charging_taper_start),
            value = settings.taperStartPercent,
            valueRange = 0..startMaximum,
            onValueChange = { onEvent(ConfigurationViewEvent.UpdateTaperStartPercent(it)) }
        )
        PercentageSettingContent(
            label = stringResource(Res.string.charging_taper_end),
            value = settings.taperEndPercent,
            valueRange = endMinimum..100,
            onValueChange = { onEvent(ConfigurationViewEvent.UpdateTaperEndPercent(it)) }
        )
        TaperPowerCapField(settings, onEvent)

        val snapshot = ChargingSessionSnapshot.from(settings, 0L)
        val minutes = ceil(
            ChargingCalculator.minutesToReach(snapshot, 0L, settings.maxPercent)
        ).toInt()
        val hours = minutes / 60
        val duration = if (hours > 0) {
            stringResource(
                Res.string.duration_hours_minutes,
                pluralStringResource(Res.plurals.hours_short, hours, hours),
                pluralStringResource(Res.plurals.minutes_short, minutes % 60, minutes % 60)
            )
        } else {
            pluralStringResource(Res.plurals.minutes_short, minutes, minutes)
        }
        Text(stringResource(Res.string.charging_taper_estimate, duration))
    }
}

@Composable
private fun TaperPowerCapField(
    settings: ChargingSettings,
    onEvent: (ConfigurationViewEvent) -> Unit
) {
    var value by remember(settings.taperPowerCapKw) {
        mutableStateOf(formatLocaleNumber(settings.taperPowerCapKw, 0, 2))
    }
    val parsed = value.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed.isFinite() && parsed > 0f
    val capLabel = stringResource(Res.string.charging_taper_cap)
    val unitDescription = stringResource(Res.string.unit_kilowatts)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { candidate ->
                val normalized = candidate.replace(',', '.')
                if (candidate.length <= 12 &&
                    normalized.matches(Regex("""\d*(?:\.\d*)?"""))) value = candidate
            },
            label = { Text(capLabel) },
            suffix = {
                Text(stringResource(Res.string.unit_kw), Modifier.semantics {
                    contentDescription = unitDescription
                })
            },
            supportingText = {
                if (value.isNotEmpty() && !valid) {
                    Text(stringResource(Res.string.power_cap_must_be_positive))
                }
            },
            isError = value.isNotEmpty() && !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        AppTextButton(
            enabled = valid && parsed != settings.taperPowerCapKw,
            onClick = { onEvent(ConfigurationViewEvent.UpdateTaperPowerCap(requireNotNull(parsed))) }
        ) { Text(stringResource(Res.string.apply)) }
    }
}
