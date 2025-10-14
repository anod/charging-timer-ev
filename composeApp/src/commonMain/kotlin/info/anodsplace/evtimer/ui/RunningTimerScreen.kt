package info.anodsplace.evtimer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingViewEvent
import info.anodsplace.evtimer.data.ChargingViewState
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

@Composable
fun RunningTimerScreen(
    viewState: ChargingViewState,
    onEvent: (ChargingViewEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val calculation = viewState.calculation
    val settings = viewState.settings
    
    // Update calculation periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            onEvent(ChargingViewEvent.UpdateCalculation)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text(
            text = "Charging in Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Battery indicator with animated charging cell
        BatteryChargingIndicator(
            currentPercent = calculation.estimatedPercent,
            maxPercent = settings.maxPercent
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // Charging Stats
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatRow(
                    label = "Charging Speed",
                    value = "${calculation.chargingSpeed} kW"
                )
                
                StatRow(
                    label = "Current %",
                    value = "${calculation.estimatedPercent.roundToInt()}%"
                )
                
                StatRow(
                    label = "Target %",
                    value = "${settings.maxPercent.roundToInt()}%"
                )
                
                StatRow(
                    label = "Time Remaining",
                    value = formatTime(calculation.timeRemainingMinutes)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        OutlinedButton(
            onClick = { onEvent(ChargingViewEvent.StopCharging) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Stop Timer", fontSize = 18.sp)
        }
    }
}

@Composable
fun BatteryChargingIndicator(
    currentPercent: Float,
    maxPercent: Float,
    modifier: Modifier = Modifier,
    cellCount: Int = 10
) {
    val fraction = if (maxPercent > 0) (currentPercent / maxPercent).coerceIn(0f, 1f) else 0f
    val fullCells = (fraction * cellCount).toInt()
    val isPartial = fraction < 1f && fullCells < cellCount

    // Blink animation for the currently charging (next) cell
    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val borderColor = MaterialTheme.colorScheme.outline
    val bodyBorderColor = borderColor.copy(alpha = 0.45f)
    val cellBorderColor = borderColor.copy(alpha = 0.35f)
    val terminalColor = borderColor.copy(alpha = 0.45f)
    val fillColor = MaterialTheme.colorScheme.primary
    val blinkColor = MaterialTheme.colorScheme.primary.copy(alpha = blinkAlpha)
    val textBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .size(width = 240.dp, height = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Battery body with cells
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(80.dp)
                .border(1.dp, bodyBorderColor, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until cellCount) {
                    val color = when {
                        i < fullCells -> fillColor
                        i == fullCells && isPartial -> blinkColor
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, cellBorderColor, RoundedCornerShape(2.dp))
                            .background(color = color, shape = RoundedCornerShape(2.dp))
                    )
                }
            }
            // Percentage text centered with background for contrast
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(textBgColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${currentPercent.roundToInt()}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // Battery positive terminal (lighter)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 2.dp)
                .width(12.dp)
                .height(32.dp)
                .background(terminalColor, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatTime(minutes: Int): String {
    if (minutes <= 0) return "Complete"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        "${hours}h ${mins}m"
    } else {
        "${mins}m"
    }
}

@Preview
@Composable
fun RunningTimerScreenPreview() {
    val state = ChargingViewState(
        settings = ChargingSettings(
            batteryCapacity = 75f,
            chargingPower = 11f,
            availablePowers = listOf(3.6f, 7.2f, 11f),
            startPercent = 20f,
            maxPercent = 80f
        ),
        calculation = ChargingCalculation(
            timeRemainingMinutes = 95,
            estimatedPercent = 45f,
            chargingSpeed = 10.5f
        )
    )
    MaterialTheme {
        Surface {
            RunningTimerScreen(viewState = state, onEvent = {})
        }
    }
}
