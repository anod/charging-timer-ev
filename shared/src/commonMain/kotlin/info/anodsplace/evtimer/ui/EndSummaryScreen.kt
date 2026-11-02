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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ChargingSummary
import info.anodsplace.evtimer.data.ShareResult
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.generated.resources.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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
    onShare: (String) -> ShareResult,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shareText = summaryShareText(summary)
    val copied = stringResource(Res.string.summary_copied)
    val failed = stringResource(Res.string.summary_share_failed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unknown = stringResource(Res.string.summary_unknown)
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
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.summary_done))
                }
                Button(onClick = {
                    scope.launch {
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
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
