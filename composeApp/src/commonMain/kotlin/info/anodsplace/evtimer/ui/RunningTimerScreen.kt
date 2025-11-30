package info.anodsplace.evtimer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingViewEvent
import info.anodsplace.evtimer.data.ChargingViewState
import info.anodsplace.evtimer.material.MaterialShapes
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import info.anodsplace.evtimer.material.internal.toPath as polygonToPath

fun formatElapsedTime(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@OptIn(ExperimentalTime::class)
@Composable
fun RunningTimerScreen(
    viewState: ChargingViewState,
    onEvent: (ChargingViewEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val calculation = viewState.calculation
    val settings = viewState.settings

    // State that updates every second to drive elapsed time recomposition
    var tick by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }

    // Elapsed time (minutes) since charging started
    val elapsedMinutes = remember(viewState.startTime, tick) {
        if (viewState.startTime > 0L) {
            ((tick - viewState.startTime).coerceAtLeast(0L) / 60000L).toInt()
        } else 0
    }

    // Update calculation + tick periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = Clock.System.now().toEpochMilliseconds()
            onEvent(ChargingViewEvent.UpdateCalculation)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Static background shapes
        StaticMaterialShapesBackground(
            modifier = Modifier.fillMaxSize(),
            shapeCount = 14
        )

        Column(
            modifier = Modifier
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
                maxPercent = settings.maxPercent,
                batteryCapacity = settings.batteryCapacity
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

                    val currentKwh = remember(settings.batteryCapacity, calculation.estimatedPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, calculation.estimatedPercent)
                    }
                    StatRow(
                        label = "Current %",
                        value = "${calculation.estimatedPercent.roundToInt()}% ($currentKwh kWh)"
                    )

                    val targetKwh = remember(settings.batteryCapacity, settings.maxPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, settings.maxPercent)
                    }
                    StatRow(
                        label = "Target %",
                        value = "${settings.maxPercent.roundToInt()}% ($targetKwh kWh)"
                    )

                    StatRow(
                        label = "Time Remaining",
                        value = formatTime(calculation.timeRemainingMinutes)
                    )

                    StatRow(
                        label = "Elapsed Time",
                        value = formatElapsedTime(elapsedMinutes)
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
}

@Composable
fun BatteryChargingIndicator(
    currentPercent: Float,
    maxPercent: Float,
    batteryCapacity: Float,
    modifier: Modifier = Modifier,
    cellCount: Int = 10
) {
    val fraction = if (maxPercent > 0) (currentPercent / maxPercent).coerceIn(0f, 1f) else 0f
    val fullCells = (fraction * cellCount).toInt()
    val isPartial = fraction < 1f && fullCells < cellCount
    val currentKwh = remember(batteryCapacity, currentPercent) {
        ChargingCalculator.calculateKwh(batteryCapacity, currentPercent)
    }

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
            // Percentage and kWh text centered with background for contrast
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(textBgColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${currentPercent.roundToInt()}% ($currentKwh kWh)",
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

// -----------------------------------------------------------------------------
// Static background implementation: randomly placed non-overlapping shapes
// -----------------------------------------------------------------------------

private data class ShapePlacement(
    val shapeIndex: Int,
    val cx: Float,
    val cy: Float,
    val size: Float,
    val color: Color,
    val bounds: Rect
)

private fun generatePlacements(
    random: Random,
    width: Float,
    height: Float,
    shapeCount: Int,
    shapePaths: List<Path>,
    colors: List<Color>
): List<ShapePlacement> {
    if (width <= 0f || height <= 0f || shapeCount <= 0) return emptyList()
    if (shapePaths.isEmpty()) return emptyList()
    // Precompute bounds once per shape as requested
    val shapeBounds: List<Rect> = shapePaths.map { it.getBounds() }

    val result = mutableListOf<ShapePlacement>()
    val maxAttempts = shapeCount * 200
    val padding = 8f
    val minSize = min(width, height) * 0.10f
    val maxSize = min(width, height) * 0.28f
    var attempts = 0
    while (result.size < shapeCount && attempts < maxAttempts) {
        attempts++
        val size = lerp(minSize, maxSize, random.nextFloat())
        val r = size / 2f
        val cx = r + random.nextFloat() * (width - 2 * r)
        val cy = r + random.nextFloat() * (height - 2 * r)
        val shapeIndex = random.nextInt(shapePaths.size)
        val nb = shapeBounds[shapeIndex]
        val scale = size
        val finalLeft = cx + nb.left * scale
        val finalTop = cy + nb.top * scale
        val finalRight = cx + nb.right * scale
        val finalBottom = cy + nb.bottom * scale
        val finalBounds = Rect(finalLeft, finalTop, finalRight, finalBottom)
        val overlaps = result.any { placed ->
            val b2 = placed.bounds
            !(finalBounds.right + padding < b2.left ||
                    finalBounds.left - padding > b2.right ||
                    finalBounds.bottom + padding < b2.top ||
                    finalBounds.top - padding > b2.bottom)
        }
        if (!overlaps) {
            val baseColor = if (colors.isNotEmpty()) colors.random(random) else Color(0xFF607D8B)
            val alpha = lerp(0.08f, 0.16f, random.nextFloat())
            val placement = ShapePlacement(shapeIndex, cx, cy, size, baseColor.copy(alpha = alpha), finalBounds)
            result += placement
        }
    }
    return result
}

@Composable
fun StaticMaterialShapesBackground(
    modifier: Modifier = Modifier,
    shapeCount: Int = 14
) {
    val rndSeed = remember(shapeCount) { Random.nextInt() }
    val random = remember(rndSeed) { Random(rndSeed) }
    val shapesCatalog = remember {
        listOf(
            MaterialShapes.Circle,
            MaterialShapes.Square,
            MaterialShapes.Slanted,
            MaterialShapes.Arch,
            MaterialShapes.Fan,
            MaterialShapes.Arrow,
            MaterialShapes.SemiCircle,
            MaterialShapes.Oval,
            MaterialShapes.Pill,
            MaterialShapes.Triangle,
            MaterialShapes.Diamond,
            MaterialShapes.ClamShell,
            MaterialShapes.Pentagon,
            MaterialShapes.Gem,
            MaterialShapes.Sunny,
            MaterialShapes.VerySunny,
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Cookie7Sided,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Cookie12Sided,
            MaterialShapes.Ghostish,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Clover8Leaf,
            MaterialShapes.Burst,
            MaterialShapes.SoftBurst,
            MaterialShapes.Boom,
            MaterialShapes.SoftBoom,
            MaterialShapes.Flower,
            MaterialShapes.Puffy,
            MaterialShapes.PuffyDiamond,
            MaterialShapes.PixelCircle,
            MaterialShapes.PixelTriangle,
            MaterialShapes.Bun,
            MaterialShapes.Heart
        )
    }
    // Convert polygons to paths once (they are already normalized)
    val shapePaths = remember(shapesCatalog) { shapesCatalog.map { it.polygonToPath() } }
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(colorScheme) {
        listOf(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary,
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer
        ).distinct()
    }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val placements = remember(widthPx, heightPx, shapeCount, palette) {
            generatePlacements(random, widthPx, heightPx, shapeCount, shapePaths, palette)
        }
        val displayPaths = remember(placements, shapePaths) {
            val matrix = Matrix()
            placements.map { p ->
                val base = shapePaths[p.shapeIndex]
                val transformed = Path().apply { addPath(base) }
                matrix.reset(); matrix.scale(p.size, p.size); matrix.translate(p.cx, p.cy)
                transformed.transform(matrix)
                transformed to p.color
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            displayPaths.forEach { (path, color) -> drawPath(path, color) }
        }
    }
}

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

@Preview(name = "RunningTimerScreen")
@Composable
private fun RunningTimerScreenPreview() {
    val state = ChargingViewState(
        isRunning = true,
        currentPercent = 45f,
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
        Scaffold {
            RunningTimerScreen(viewState = state, onEvent = {})
        }
    }
}

@Preview(name = "RunningTimerScreen Alt")
@Composable
private fun RunningTimerScreenAltPreview() {
    val state = ChargingViewState(
        isRunning = true,
        currentPercent = 60f,
        settings = ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 7.2f,
            availablePowers = listOf(3.6f, 7.2f, 11f),
            startPercent = 10f,
            maxPercent = 90f
        ),
        calculation = ChargingCalculation(
            timeRemainingMinutes = 120,
            estimatedPercent = 60f,
            chargingSpeed = 7.0f
        )
    )
    MaterialTheme {
        Scaffold {
            RunningTimerScreen(viewState = state, onEvent = {})
        }
    }
}
