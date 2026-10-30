package info.anodsplace.evtimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * PercentageSettingContent is a reusable composable for editing a numeric value within a bounded IntRange
 * using +/- step buttons, a text field (validated & stepped), and a discrete slider.
 *
 * Accessibility notes:
 * - All interactive elements have content descriptions.
 * - Validation errors announce the constraint requirements.
 */
@Composable
fun PercentageSettingContent(
    label: String,
    value: Float,
    valueRange: IntRange,
    onValueChange: (Float) -> Unit,
    suffix: String = "%",
    unitDescription: String = "percent",
    step: Int = 1,
    additionalInfo: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    var text by remember(value) { mutableStateOf(value.roundToInt().toString()) }
    var isError by remember { mutableStateOf(false) }
    var previousError by remember { mutableStateOf(false) }

    val min = valueRange.first
    val max = valueRange.last
    val clampedStep = if (step <= 0) 1 else step

    fun coerceToStep(v: Float): Int {
        val rounded = (((v - min) / clampedStep).roundToInt() * clampedStep + min)
            .coerceIn(min, max)
        return rounded
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = { onValueChange((value - clampedStep).coerceAtLeast(min.toFloat())) },
            enabled = value > min,
            modifier = Modifier
                .width(48.dp)
                .semantics { contentDescription = "Decrease $label" }
        ) { Text("-") }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val digits = new.filter { it.isDigit() }.take(4)
                text = digits
                val intVal = digits.toIntOrNull()
                val valid = intVal != null && intVal in valueRange && ((intVal - min) % clampedStep == 0)
                isError = digits.isNotEmpty() && !valid
                if (isError && !previousError) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                previousError = isError
                if (valid) {
                    onValueChange(intVal.toFloat())
                }
            },
            label = { Text("Set") },
            singleLine = true,
            isError = isError,
            supportingText = {
                AnimatedVisibility(visible = isError) {
                    Text(
                        text = "Must be between $min and $max in $clampedStep increments",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics {
                            contentDescription = "$label error value must be between $min and $max in $clampedStep increments"
                        }
                    )
                }
            },
            suffix = { Text(suffix, modifier = Modifier.semantics { contentDescription = suffix }) },
            modifier = Modifier
                .width(96.dp)
                .semantics { contentDescription = "$label input field" }
        )
        TextButton(
            onClick = { onValueChange((value + clampedStep).coerceAtMost(max.toFloat())) },
            enabled = value < max,
            modifier = Modifier
                .width(48.dp)
                .semantics { contentDescription = "Increase $label" }
        ) { Text("+") }
    }
    val stepsCount = ((max - min) / clampedStep).coerceAtLeast(1)
    val sliderSteps = (stepsCount - 1).coerceAtLeast(0)
    Slider(
        value = value,
        onValueChange = { raw ->
            val stepped = coerceToStep(raw).toFloat()
            if (stepped != value.roundToInt().toFloat()) {
                onValueChange(stepped)
            }
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = sliderSteps,
        modifier = Modifier.semantics { contentDescription = "$label slider value ${value.roundToInt()} $unitDescription" }
    )
    if (additionalInfo != null) {
        Text(
            text = additionalInfo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = "$label equivalent to $additionalInfo" }
        )
    }
}

