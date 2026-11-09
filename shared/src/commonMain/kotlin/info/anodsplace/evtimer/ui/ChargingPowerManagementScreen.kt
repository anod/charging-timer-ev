package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.formatPower
import info.anodsplace.evtimer.data.RemovedPower
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ChargingPowerManagementScreen(
    powers: List<Float>,
    selectedPower: Float,
    onAddPower: (Float) -> Unit,
    onRemovePower: suspend (Float) -> RemovedPower,
    onUndoRemovePower: suspend (RemovedPower) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPowerDialog by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(Res.string.power_undo)
    val editFailed = stringResource(Res.string.power_edit_failed)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = readablePageWidth).fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            SettingsHeader(stringResource(Res.string.charging_power), onBack, showBack = !LocalSettingsTwoPane.current)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(UiSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
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
                        val removedMessage = stringResource(Res.string.power_removed, formatPower(power))
                        if (index > 0) SettingsDivider(inset = UiSpacing.medium)
                        ListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.power_value, formatPower(power), stringResource(Res.string.unit_kw)))
                            },
                            supportingContent = if (power == selectedPower) {
                                { Text(stringResource(Res.string.settings_power_selected)) }
                            } else null,
                            trailingContent = {
                                AppIconButton(
                                    enabled = !busy && powers.any { it != power },
                                    onClick = {
                                        busy = true
                                        snackbar.currentSnackbarData?.dismiss()
                                        scope.launch {
                                            val removed = try {
                                                onRemovePower(power)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                println("Charging power removal failed: $error")
                                                busy = false
                                                snackbar.showSnackbar(editFailed)
                                                return@launch
                                            }
                                            busy = false
                                            if (snackbar.showSnackbar(
                                                    removedMessage, undoLabel, duration = SnackbarDuration.Short
                                                ) == SnackbarResult.ActionPerformed) {
                                                busy = true
                                                var failed = false
                                                try {
                                                    onUndoRemovePower(removed)
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    println("Charging power restore failed: $error")
                                                    failed = true
                                                } finally {
                                                    busy = false
                                                }
                                                if (failed) snackbar.showSnackbar(editFailed)
                                            }
                                        }
                                    }
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
        SnackbarHost(snackbar, Modifier.fillMaxWidth()) { notice ->
            Snackbar(
                modifier = Modifier.padding(UiSpacing.small),
                action = notice.visuals.actionLabel?.let { label ->
                    {
                        TextButton(
                            onClick = notice::performAction,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.inversePrimary
                            )
                        ) { Text(label) }
                    }
                }
            ) { Text(notice.visuals.message) }
        }
    }
    }
    if (showCustomPowerDialog) {
        CustomPowerDialog(
            existingPowers = powers,
            onDismiss = { showCustomPowerDialog = false },
            onConfirm = {
                onAddPower(it)
                showCustomPowerDialog = false
            }
        )
    }
}
