package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.data.formatSessionDate
import info.anodsplace.evtimer.data.summary
import info.anodsplace.evtimer.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryScreen(
    entries: List<ChargingHistoryEntry>?,
    onOpenSummary: (ChargingHistoryEntry) -> Unit,
    onDelete: suspend (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleteFailed = stringResource(Res.string.history_delete_failed)
    if (deletingId != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) deletingId = null },
            title = { Text(stringResource(Res.string.history_delete_title)) },
            text = { Text(stringResource(Res.string.history_delete_message)) },
            confirmButton = {
                AppDestructiveButton(enabled = !busy, onClick = {
                    val id = requireNotNull(deletingId)
                    busy = true
                    scope.launch {
                        try {
                            onDelete(id)
                            deletingId = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            println("Charging history: delete failed: $error")
                            deletingId = null
                            snackbar.showSnackbar(deleteFailed)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(Res.string.history_delete)) }
            },
            dismissButton = {
                AppTextButton(enabled = !busy, onClick = { deletingId = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = readablePageWidth).fillMaxSize()) {
            SettingsHeader(stringResource(Res.string.history_title), onBack)
            when {
                entries == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                entries.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.history_empty), Modifier.padding(UiSpacing.large),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(UiSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryCard(entry, { onOpenSummary(entry) }, { deletingId = entry.id })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun HistoryCard(entry: ChargingHistoryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val summary = entry.summary()
    val unknown = stringResource(Res.string.summary_unknown)
    val preset = summary.presetName?.let {
        if (summary.presetEdited) stringResource(Res.string.edited_preset_name, it) else it
    } ?: stringResource(Res.string.history_no_preset)
    val date = formatSessionDate(entry.snapshot.startTime)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = tonalCardColors(), border = tonalCardBorder()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = UiSpacing.medium,
                top = UiSpacing.medium,
                end = UiSpacing.small,
                bottom = UiSpacing.medium
            ),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(date, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.value_with_separator, preset,
                    stringResource(Res.string.summary_percent,
                        formatLocaleNumber(summary.startPercent, 0, 1),
                        summary.endPercent?.let { formatLocaleNumber(it, 0, 1) } ?: unknown)),
                    style = MaterialTheme.typography.titleMedium)
                if (summary.interrupted) {
                    Text(stringResource(Res.string.summary_interrupted),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(Res.string.history_stats,
                        summary.energyKwh?.let { formatLocaleNumber(it, 0, 1) } ?: unknown,
                        summary.durationMillis?.let { formatElapsedTime((it / 60_000L).toInt()) } ?: unknown,
                        summary.averageKw?.let { formatLocaleNumber(it, 0, 1) } ?: unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppIconButton(onClick = onDelete) {
                Icon(painterResource(Res.drawable.ic_delete),
                    stringResource(Res.string.history_delete_description, date, preset))
            }
        }
    }
}
