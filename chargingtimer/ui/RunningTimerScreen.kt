package com.anod.chargingtimer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anod.chargingtimer.data.ChargingViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun RunningTimerScreen(
    viewModel: ChargingViewModel,
    onStopCharging: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calculation = viewModel.calculation
    val settings = viewModel.settings
    
    // Update calculation periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.updateCalculation()
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
        
        // Wobble animation circle
        WobbleChargingIndicator(
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
            onClick = onStopCharging,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Stop Charging", fontSize = 18.sp)
        }
    }
}

@Composable
fun WobbleChargingIndicator(
    currentPercent: Float,
    maxPercent: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Wobble rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Pulsing scale animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${currentPercent.roundToInt()}%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "of ${maxPercent.roundToInt()}%",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
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
