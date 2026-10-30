package info.anodsplace.evtimer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.anodsplace.evtimer.data.ChargingViewState
import androidx.compose.animation.animateContentSize

/**
 * CollapsibleSettingCard shows a compact summary chip when collapsed and an expanded SettingCard when open.
 *
 * UX / Interaction:
 * - Collapsed state: ElevatedAssistChip displays an arrow (▸) + summary text; tapping expands (▾).
 * - Expanded state: A SettingCard fills the width; only the title row is clickable (with a subtle bounded ripple)
 *   to collapse back to the chip state. This keeps slider / text input interactions unaffected by collapse gestures.
 * - Haptic feedback (LongPress style) is triggered on expand and collapse for tactile confirmation.
 *
 * Accessibility:
 * - Collapsed chip has contentDescription: "Expand <Title>. <Summary>".
 * - Expanded title row has contentDescription: "Collapse <Title>. <Summary>".
 * - Arrow indicator updates (▸ collapsed, ▾ expanded) and is included in the visible text but not required for SR users.
 *
 * Parameters:
 * @param title Section title displayed in expanded header and used in accessibility descriptions.
 * @param summary Lambda producing human-readable summary from the current viewState (invoked in both states).
 * @param viewState Current ChargingViewState for deriving summary values.
 * @param modifier External modifier for padding/placement.
 * @param initiallyExpanded Whether the card starts expanded.
 * @param expandedContent Composable content shown inside the expanded SettingCard below the title row.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CollapsibleSettingCard(
    title: String,
    summary: (ChargingViewState) -> String,
    viewState: ChargingViewState,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    expandedContent: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val arrow = if (expanded) "▾" else "▸"
    val haptics = LocalHapticFeedback.current

    AnimatedContent(
        targetState = expanded,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "$title-collapsible"
    ) { isExpanded ->
        if (isExpanded) {
            SettingCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
                // Title row with ripple-only clickable area
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            expanded = false
                        }
                        .semantics { contentDescription = "Collapse $title. ${summary(viewState)}" }
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "$arrow $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                expandedContent()
            }
        } else {
            ElevatedAssistChip(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = true
                },
                label = { Text("$arrow ${summary(viewState)}") },
                modifier = modifier
                    .semantics { contentDescription = "Expand $title. ${summary(viewState)}" }
            )
        }
    }
}
