package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.formatPower
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChargingPowerManagementScreen(
    powers: List<Float>,
    selectedPower: Float,
    onAddPower: (Float) -> Unit,
    onRemovePower: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPowerDialog by rememberSaveable { mutableStateOf(false) }
    SettingsPage(modifier) {
        SettingsHeader(stringResource(Res.string.charging_power), onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.settings_charging_powers_help),
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppPrimaryButton(onClick = { showCustomPowerDialog = true }) {
                Text(stringResource(Res.string.add_custom_power))
            }
            SettingsGroup {
                powers.forEachIndexed { index, power ->
                    if (index > 0) SettingsDivider(inset = 16.dp)
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.power_value, formatPower(power), stringResource(Res.string.unit_kw)))
                        },
                        supportingContent = if (power == selectedPower) {
                            { Text(stringResource(Res.string.settings_power_selected)) }
                        } else null,
                        trailingContent = {
                            AppIconButton(
                                enabled = powers.any { it != power },
                                onClick = { onRemovePower(power) }
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_delete),
                                    stringResource(Res.string.remove_power, formatPower(power))
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
    if (showCustomPowerDialog) {
        CustomPowerDialog(
            onDismiss = { showCustomPowerDialog = false },
            onConfirm = {
                onAddPower(it)
                showCustomPowerDialog = false
            }
        )
    }
}
