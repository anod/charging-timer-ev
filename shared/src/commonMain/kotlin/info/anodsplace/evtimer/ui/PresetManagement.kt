package info.anodsplace.evtimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.ChargingCalibration
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.data.MAX_PRESET_NAME_CODE_POINTS
import info.anodsplace.evtimer.data.PresetNameError
import info.anodsplace.evtimer.data.normalizeAndValidatePresetName
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class PresetDragState(initialOrder: List<String>) {
    private var startingOrder = initialOrder
    var order by mutableStateOf(initialOrder)
        private set
    var draggedId by mutableStateOf<String?>(null)
        private set
    var offset by mutableStateOf(0f)
        private set

    fun start(id: String) {
        require(id in order)
        startingOrder = order
        draggedId = id
        offset = 0f
    }

    fun drag(delta: Float, rowHeight: Float) {
        val index = order.indexOf(draggedId)
        if (index < 0) return
        offset = (offset + delta).coerceIn(-index * rowHeight, (order.lastIndex - index) * rowHeight)
        var target = index
        while (offset > rowHeight / 2 && target < order.lastIndex) {
            target++
            offset -= rowHeight
        }
        while (offset < -rowHeight / 2 && target > 0) {
            target--
            offset += rowHeight
        }
        if (target != index) {
            order = order.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

    fun finish(): List<String>? {
        if (draggedId == null) return null
        draggedId = null
        offset = 0f
        return order.takeIf { it != startingOrder }
    }

    fun cancel() {
        order = startingOrder
        draggedId = null
        offset = 0f
    }
}

@Composable
internal fun PresetManagement(
    presets: List<ChargingPreset>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onReorder: (List<String>) -> Unit,
    showTitle: Boolean = true,
    calibration: ChargingCalibration = ChargingCalibration(),
    onResetCalibration: suspend (String, Float) -> Unit = { _, _ -> }
) {
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoId by rememberSaveable { mutableStateOf<String?>(null) }
    var resettingSpeed by rememberSaveable { mutableStateOf<Float?>(null) }
    var resetBusy by remember { mutableStateOf(false) }
    var resetFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dragState = remember(presets) { PresetDragState(presets.map { it.id }) }
    val commitOrder by rememberUpdatedState(onReorder)
    val rowPitch = with(LocalDensity.current) { 57.dp.toPx() }
    val moveUp = stringResource(Res.string.move_up)
    val moveDown = stringResource(Res.string.move_down)
    if (showTitle) Text(stringResource(Res.string.settings_presets), style = MaterialTheme.typography.titleLarge)
    SettingsGroup {
        dragState.order.forEachIndexed { index, id ->
            key(id) {
                val preset = presets.first { it.id == id }
                val dragging = dragState.draggedId == id
                val reorderDescription = stringResource(Res.string.reorder_preset, preset.name)
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragState.offset.roundToInt() else 0) }
                        .then(if (dragging) Modifier
                            .shadow(8.dp, MaterialTheme.shapes.medium)
                            .background(tonalCardContainerColor().copy(alpha = 1f), MaterialTheme.shapes.medium)
                        else Modifier)
                        .testTag("preset-$id")
                        .semantics(mergeDescendants = true) {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { onMove(id, -1); true })
                                if (index < presets.lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(id, 1); true })
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(48.dp)
                            .semantics { contentDescription = reorderDescription }
                            .pointerInput(dragState, id, rowPitch) {
                                try {
                                    if (presets.size > 1) detectDragGestures(
                                        onDragStart = { dragState.start(id) },
                                        onDragCancel = { dragState.cancel() },
                                        onDragEnd = { dragState.finish()?.let(commitOrder) },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragState.drag(amount.y, rowPitch)
                                        }
                                    )
                                } finally {
                                    if (dragState.draggedId == id) dragState.cancel()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(DragHandleIcon, contentDescription = null)
                    }
                    Text(
                        preset.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = UiSpacing.small)
                    )
                    AppIconButton(onClick = { infoId = id }) {
                        Icon(painterResource(Res.drawable.ic_info), stringResource(Res.string.calibration_info, preset.name))
                    }
                    AppIconButton(onClick = { renamingId = id }) {
                        Icon(EditIcon, stringResource(Res.string.rename_named_preset, preset.name))
                    }
                    AppIconButton(onClick = { deletingId = id }, enabled = presets.size > 1) {
                        Icon(DeletePresetIcon, stringResource(Res.string.delete_named_preset, preset.name))
                    }
                }
                if (index < dragState.order.lastIndex) SettingsDivider(inset = 48.dp)
            }
        }
    }
    if (presets.size == 1) {
        Text(stringResource(Res.string.last_preset_hint), Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    presets.firstOrNull { it.id == renamingId }?.let { preset ->
        PresetRenameDialog(preset, presets,
            onDismiss = { renamingId = null },
            onRename = { onRename(preset.id, it); renamingId = null })
    }
    presets.firstOrNull { it.id == deletingId }?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(Res.string.delete_preset)) },
            text = { Text(stringResource(Res.string.delete_preset_confirmation, preset.name)) },
            confirmButton = {
                AppDestructiveButton(onClick = { onDelete(preset.id); deletingId = null }) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                AppTextButton(onClick = { deletingId = null }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
    presets.firstOrNull { it.id == infoId && resettingSpeed == null }?.let { preset ->
        AlertDialog(
            onDismissRequest = { infoId = null },
            title = { Text(preset.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    preset.settings.availablePowers.forEach { power ->
                        val estimate = calibration.estimate(preset.id, power)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(Res.string.calibration_speed,
                                    formatLocaleNumber(power, 0, 6)))
                                Text(pluralStringResource(
                                    Res.plurals.calibration_factor_count,
                                    estimate.eligibleCount,
                                    formatLocaleNumber(estimate.factor * 100f, 0, 1),
                                    estimate.eligibleCount
                                ),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            AppTextButton(onClick = {
                                resettingSpeed = power
                                resetFailed = false
                            }) { Text(stringResource(Res.string.calibration_reset_speed)) }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(onClick = { infoId = null }) { Text(stringResource(Res.string.calibration_close)) }
            }
        )
    }
    presets.firstOrNull { it.id == infoId }?.let { preset ->
        resettingSpeed?.let { power ->
            val speed = formatLocaleNumber(power, 0, 6)
            AlertDialog(
                onDismissRequest = { if (!resetBusy) resettingSpeed = null },
                title = { Text(stringResource(Res.string.calibration_reset_speed_title, preset.name, speed)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.calibration_reset_speed_message))
                        if (resetFailed) Text(stringResource(Res.string.calibration_reset_failed),
                            color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    AppDestructiveButton(enabled = !resetBusy, onClick = {
                        resetBusy = true
                        resetFailed = false
                        scope.launch {
                            try {
                                onResetCalibration(preset.id, power)
                                resettingSpeed = null
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                println("Calibration reset failed: $error")
                                resetFailed = true
                            } finally {
                                resetBusy = false
                            }
                        }
                    }) { Text(stringResource(Res.string.reset)) }
                },
                dismissButton = {
                    AppTextButton(enabled = !resetBusy, onClick = { resettingSpeed = null }) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
internal fun PresetRenameDialog(
    preset: ChargingPreset,
    presets: List<ChargingPreset>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
    val validation = normalizeAndValidatePresetName(name, presets.filterNot { it.id == preset.id }.map { it.name })
    val valid = name == preset.name || validation.isValid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_preset)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.preset_name)) },
                    singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth())
                if (!valid) {
                    val error = when (validation.error) {
                        PresetNameError.TOO_LONG -> stringResource(Res.string.preset_name_too_long, MAX_PRESET_NAME_CODE_POINTS)
                        PresetNameError.ALREADY_EXISTS -> stringResource(Res.string.preset_name_exists)
                        else -> stringResource(Res.string.preset_name_required)
                    }
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                presetEmojiSuggestions.forEach { suggestions ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { emoji ->
                            FilterChip(selected = false, onClick = { name += emoji }, label = { Text(emoji) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(enabled = valid, onClick = {
                onRename(if (name == preset.name) preset.name else validation.name)
            }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}
