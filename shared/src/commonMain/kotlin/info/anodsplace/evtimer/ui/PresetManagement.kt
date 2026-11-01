package info.anodsplace.evtimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import info.anodsplace.evtimer.data.MAX_PRESET_NAME_CODE_POINTS
import info.anodsplace.evtimer.data.PresetNameError
import info.anodsplace.evtimer.data.normalizeAndValidatePresetName
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

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
    onReorder: (List<String>) -> Unit
) {
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val dragState = remember(presets) { PresetDragState(presets.map { it.id }) }
    val commitOrder by rememberUpdatedState(onReorder)
    val rowHeight = with(LocalDensity.current) { 56.dp.toPx() }
    val moveUp = stringResource(Res.string.move_up)
    val moveDown = stringResource(Res.string.move_down)
    val rename = stringResource(Res.string.rename_preset)
    Text(stringResource(Res.string.settings_presets), style = MaterialTheme.typography.titleLarge)
    Column {
        dragState.order.forEachIndexed { index, id ->
            key(id) {
                val preset = presets.first { it.id == id }
                val dragging = dragState.draggedId == id
                val reorderDescription = stringResource(Res.string.reorder_preset, preset.name)
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragState.offset.roundToInt() else 0) }
                        .shadow(if (dragging) 4.dp else 0.dp, MaterialTheme.shapes.small)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.small
                        )
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
                            .pointerInput(dragState, id, rowHeight) {
                                try {
                                    if (presets.size > 1) detectDragGestures(
                                        onDragStart = { dragState.start(id) },
                                        onDragCancel = { dragState.cancel() },
                                        onDragEnd = { dragState.finish()?.let(commitOrder) },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragState.drag(amount.y, rowHeight)
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
                        modifier = Modifier.weight(1f).clickable(onClickLabel = rename) {
                            renamingId = id
                        }.padding(vertical = 12.dp)
                    )
                    IconButton(onClick = { deletingId = id }, enabled = presets.size > 1) {
                        Icon(DeletePresetIcon, stringResource(Res.string.delete_named_preset, preset.name))
                    }
                }
            }
        }
    }
    if (presets.size == 1) {
        Text(stringResource(Res.string.last_preset_hint), style = MaterialTheme.typography.bodySmall)
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
                TextButton(onClick = { onDelete(preset.id); deletingId = null }) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(Res.string.cancel)) }
            }
        )
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
            TextButton(enabled = valid, onClick = {
                onRename(if (name == preset.name) preset.name else validation.name)
            }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}
