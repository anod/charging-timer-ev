package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingCalibration
import info.anodsplace.evtimer.data.CalibrationExclusion
import info.anodsplace.evtimer.data.parseCarReading
import info.anodsplace.evtimer.data.ChargingSummary
import info.anodsplace.evtimer.data.ShareResult
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.generated.resources.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun summaryShareText(summary: ChargingSummary): String {
    val unknown = stringResource(Res.string.summary_unknown)
    val end = summary.endPercent?.let { formatLocaleNumber(it, 0, 1) } ?: unknown
    val duration = summary.durationMillis?.let { formatElapsedTime((it / 60_000L).toInt()) } ?: unknown
    val energy = summary.energyKwh?.let { formatLocaleNumber(it, 0, 1) } ?: unknown
    val average = summary.averageKw?.let { formatLocaleNumber(it, 0, 1) } ?: unknown
    val text = stringResource(
        Res.string.summary_share_text,
        formatLocaleNumber(summary.startPercent, 0, 1), end, energy, duration, average
    )
    val withPreset = summary.presetName?.let {
        stringResource(Res.string.value_with_separator, text, summaryPresetName(summary))
    } ?: text
    return if (summary.interrupted) {
        stringResource(Res.string.value_with_separator, withPreset, stringResource(Res.string.summary_interrupted))
    } else withPreset
}

@Composable
private fun summaryPresetName(summary: ChargingSummary): String =
    if (summary.presetEdited) stringResource(Res.string.edited_preset_name, summary.presetName.orEmpty())
    else summary.presetName.orEmpty()

@Composable
fun EndSummaryScreen(
    summary: ChargingSummary,
    onShare: (suspend (String) -> ShareResult)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    entry: ChargingHistoryEntry? = null,
    onSaveReading: suspend (Long, Float?, Boolean) -> Unit = { _, _, _ -> },
    allowReadingEditing: Boolean = true
) {
    val shareText = summaryShareText(summary)
    val copied = stringResource(Res.string.summary_copied)
    val failed = stringResource(Res.string.summary_share_failed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unknown = stringResource(Res.string.summary_unknown)
    var editing by rememberSaveable(entry?.id) { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(Res.string.summary_title), style = MaterialTheme.typography.headlineMedium)
            Card(Modifier.fillMaxWidth(), colors = tonalCardColors(), border = tonalCardBorder()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            Res.string.summary_percent,
                            formatLocaleNumber(summary.startPercent, 0, 1),
                            summary.endPercent?.let { formatLocaleNumber(it, 0, 1) } ?: unknown
                        ),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    entry?.let {
                        Text(
                            if (it.actualPercent == null) stringResource(Res.string.reading_estimated)
                            else stringResource(Res.string.reading_saved_estimate,
                                it.endPercent?.let { value -> formatLocaleNumber(value, 0, 1) } ?: unknown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    summary.presetName?.let {
                        Text(summaryPresetName(summary), style = MaterialTheme.typography.titleMedium)
                    }
                    if (summary.interrupted) {
                        Text(stringResource(Res.string.summary_interrupted), color = MaterialTheme.colorScheme.error)
                    }
                    SummaryRow(stringResource(Res.string.elapsed_time),
                        summary.durationMillis?.let { formatElapsedTime((it / 60_000L).toInt()) } ?: unknown)
                    SummaryRow(stringResource(Res.string.summary_energy),
                        summary.energyKwh?.let {
                            stringResource(Res.string.power_value, formatLocaleNumber(it, 0, 1), stringResource(Res.string.unit_kwh))
                        } ?: unknown)
                    SummaryRow(stringResource(Res.string.summary_average),
                        summary.averageKw?.let {
                            stringResource(Res.string.power_value, formatLocaleNumber(it, 0, 1), stringResource(Res.string.unit_kw))
                        } ?: unknown)
                    if (entry != null && allowReadingEditing) {
                        AppTextButton(onClick = { editing = true }) {
                            Text(stringResource(if (entry.actualPercent == null) Res.string.reading_add else Res.string.reading_edit))
                        }
                    }
                }
            }
            if (summary.segments.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = tonalCardColors(), border = tonalCardBorder()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.summary_speeds), style = MaterialTheme.typography.titleMedium)
                        summary.segments.forEach {
                            SummaryRow(
                                stringResource(Res.string.power_value, formatLocaleNumber(it.powerKw, 0, 2), stringResource(Res.string.unit_kw)),
                                formatElapsedTime((it.durationMillis / 60_000L).toInt())
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppPrimaryButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.summary_done))
                }
                if (onShare != null) AppSecondaryButton(onClick = {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            if (onShare(shareText) == ShareResult.Copied) snackbar.showSnackbar(copied)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            println("Charging summary: sharing failed: $error")
                            snackbar.showSnackbar(failed)
                        }
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.summary_share))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (editing && entry != null && allowReadingEditing) {
        CarReadingDialog(entry, onDismiss = { editing = false }, onSave = onSaveReading)
    }
}

@Composable
internal fun CarReadingDialog(
    entry: ChargingHistoryEntry,
    onDismiss: () -> Unit,
    onSave: suspend (Long, Float?, Boolean) -> Unit
) {
    var text by rememberSaveable(entry.id) {
        mutableStateOf(entry.actualPercent?.toString()?.removeSuffix(".0").orEmpty())
    }
    var consent by rememberSaveable(entry.id) { mutableStateOf(entry.calibrationEnabled) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val actual = parseCarReading(text)
    val valid = text.isBlank() || actual != null
    val exclusion = ChargingCalibration.exclusion(entry, actual)
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.reading_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; failed = false },
                    label = { Text(stringResource(Res.string.reading_percent)) },
                    suffix = { Text(stringResource(Res.string.unit_percent)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = !busy, isError = !valid,
                    supportingText = if (!valid) { { Text(stringResource(Res.string.reading_invalid)) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().toggleable(
                    value = consent && exclusion == null, enabled = !busy && exclusion == null,
                    role = Role.Checkbox, onValueChange = { consent = it }
                ), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent && exclusion == null, onCheckedChange = null,
                        enabled = !busy && exclusion == null)
                    Text(stringResource(Res.string.reading_consent), Modifier.weight(1f))
                }
                exclusion?.let {
                    if (actual != null && it != CalibrationExclusion.READING_REQUIRED) {
                        Text(stringResource(when (it) {
                            CalibrationExclusion.NO_GAIN -> Res.string.reading_no_gain
                            CalibrationExclusion.INTERRUPTED -> Res.string.reading_interrupted
                            CalibrationExclusion.NO_ENERGY -> Res.string.reading_no_energy
                            CalibrationExclusion.MIXED_POWER -> Res.string.reading_mixed_power
                            CalibrationExclusion.PRESET_REQUIRED -> Res.string.reading_preset_required
                            CalibrationExclusion.READING_REQUIRED -> error("Handled above")
                        }), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (failed) Text(stringResource(Res.string.reading_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            AppTextButton(enabled = valid && !busy, onClick = {
                busy = true
                failed = false
                scope.launch {
                    try {
                        onSave(entry.id, actual, consent && exclusion == null)
                        onDismiss()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        println("Car reading: save failed: $error")
                        failed = true
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            AppTextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
