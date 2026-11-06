package info.anodsplace.evtimer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import info.anodsplace.evtimer.data.ConfigurationViewState
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.collapse_setting_description
import info.anodsplace.evtimer.generated.resources.collapsed_setting_state
import info.anodsplace.evtimer.generated.resources.expand_setting_description
import info.anodsplace.evtimer.generated.resources.expanded_setting_state
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.animateContentSize

/**
 * CollapsibleSettingCard shows a compact summary chip when collapsed and an expanded SettingCard when open.
 *
 * UX / Interaction:
 * - Collapsed state: ElevatedAssistChip displays a directional chevron + summary text; tapping expands.
 * - Expanded state: A SettingCard fills the width; only the title row is clickable (with a subtle bounded ripple)
 *   to collapse back to the chip state. This keeps slider / text input interactions unaffected by collapse gestures.
 * - Haptic feedback (LongPress style) is triggered on expand and collapse for tactile confirmation.
 *
 * Accessibility:
 * - Collapsed chip has contentDescription: "Expand <Title>. <Summary>".
 * - Expanded title row has contentDescription: "Collapse <Title>. <Summary>".
 * - Expanded/collapsed state is exposed through stateDescription; the decorative chevron is not announced.
 *
 * Parameters:
 * @param title Section title displayed in expanded header and used in accessibility descriptions.
 * @param summary Lambda producing human-readable summary from the current viewState (invoked in both states).
 * @param viewState Current ConfigurationViewState for deriving summary values.
 * @param modifier External modifier for padding/placement.
 * @param initiallyExpanded Whether the card starts expanded.
 * @param expandedContent Composable content shown inside the expanded SettingCard below the title row.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CollapsibleSettingCard(
    title: String,
    summary: @Composable (ConfigurationViewState) -> String,
    viewState: ConfigurationViewState,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    expandedContent: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    CollapsibleSettingCard(
        title = title,
        summary = summary,
        viewState = viewState,
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        expandedContent = expandedContent
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CollapsibleSettingCard(
    title: String,
    summary: @Composable (ConfigurationViewState) -> String,
    viewState: ConfigurationViewState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headerAction: (@Composable () -> Unit)? = null,
    expandedContent: @Composable () -> Unit
) {
    val summaryText = summary(viewState)
    val collapseDescription = stringResource(Res.string.collapse_setting_description, title, summaryText)
    val expandDescription = stringResource(Res.string.expand_setting_description, title, summaryText)
    val expandedState = stringResource(Res.string.expanded_setting_state)
    val collapsedState = stringResource(Res.string.collapsed_setting_state)
    val haptics = LocalHapticFeedback.current

    AnimatedContent(
        targetState = expanded,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = title
    ) { isExpanded ->
        if (isExpanded) {
            SettingCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
                // Title row with ripple-only clickable area
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                            .clickable(role = Role.Button) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExpandedChange(false)
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = collapseDescription
                                stateDescription = expandedState
                            }
                    ) {
                        SettingChevron(expanded = true)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    headerAction?.invoke()
                }
                expandedContent()
            }
        } else {
            ElevatedAssistChip(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExpandedChange(true)
                },
                shape = MaterialTheme.shapes.small,
                colors = tonalSummaryColors(),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingChevron(expanded = false)
                        Spacer(Modifier.width(8.dp))
                        Text(summaryText)
                    }
                },
                modifier = modifier
                    .semantics {
                        contentDescription = expandDescription
                        stateDescription = collapsedState
                    }
            )
        }
    }
}

@Composable
private fun SettingChevron(expanded: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = Modifier.size(18.dp).clearAndSetSemantics {}
    ) {
        val strokeWidth = 2.dp.toPx()
        val cap = StrokeCap.Round
        settingChevronSegments(expanded, isRtl).forEach { segment ->
            drawLine(
                color = color,
                start = Offset(size.width * segment.start.x, size.height * segment.start.y),
                end = Offset(size.width * segment.end.x, size.height * segment.end.y),
                strokeWidth = strokeWidth,
                cap = cap
            )
        }
    }
}

internal data class ChevronSegment(val start: Offset, val end: Offset)

internal fun settingChevronSegments(expanded: Boolean, rtl: Boolean): List<ChevronSegment> {
    val segments = if (expanded) {
        listOf(
            ChevronSegment(Offset(0.28f, 0.4f), Offset(0.5f, 0.62f)),
            ChevronSegment(Offset(0.5f, 0.62f), Offset(0.72f, 0.4f))
        )
    } else {
        listOf(
            ChevronSegment(Offset(0.4f, 0.28f), Offset(0.62f, 0.5f)),
            ChevronSegment(Offset(0.62f, 0.5f), Offset(0.4f, 0.72f))
        )
    }
    return if (!rtl || expanded) segments else segments.map { segment ->
        ChevronSegment(
            start = Offset(1f - segment.start.x, segment.start.y),
            end = Offset(1f - segment.end.x, segment.end.y)
        )
    }
}
