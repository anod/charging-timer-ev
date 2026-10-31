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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.anodsplace.evtimer.data.ChargingCalculation
import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.MILESTONE_PERCENT
import info.anodsplace.evtimer.data.MilestoneEstimate
import info.anodsplace.evtimer.data.RunningViewEvent
import info.anodsplace.evtimer.data.RunningViewState
import info.anodsplace.evtimer.data.formatLocaleNumber
import info.anodsplace.evtimer.data.formatPower
import info.anodsplace.evtimer.material.MaterialShapes
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import info.anodsplace.evtimer.material.internal.toPath as polygonToPath
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.apply
import info.anodsplace.evtimer.generated.resources.charging_in_progress
import info.anodsplace.evtimer.generated.resources.charging_speed
import info.anodsplace.evtimer.generated.resources.change_charging_speed
import info.anodsplace.evtimer.generated.resources.change_speed
import info.anodsplace.evtimer.generated.resources.complete
import info.anodsplace.evtimer.generated.resources.current_percent
import info.anodsplace.evtimer.generated.resources.duration_hours_minutes
import info.anodsplace.evtimer.generated.resources.elapsed_time
import info.anodsplace.evtimer.generated.resources.hours_short
import info.anodsplace.evtimer.generated.resources.minutes_short
import info.anodsplace.evtimer.generated.resources.percent_and_energy
import info.anodsplace.evtimer.generated.resources.power_value
import info.anodsplace.evtimer.generated.resources.reached
import info.anodsplace.evtimer.generated.resources.stop_timer
import info.anodsplace.evtimer.generated.resources.target_percent
import info.anodsplace.evtimer.generated.resources.time_remaining
import info.anodsplace.evtimer.generated.resources.time_to_percent
import info.anodsplace.evtimer.generated.resources.unit_kw
import info.anodsplace.evtimer.generated.resources.unit_kwh
import info.anodsplace.evtimer.generated.resources.value_with_separator
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

@Composable
fun formatElapsedTime(minutes: Int): String {
    if (minutes <= 0) return pluralStringResource(Res.plurals.minutes_short, 0, 0)
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        stringResource(
            Res.string.duration_hours_minutes,
            pluralStringResource(Res.plurals.hours_short, hours, hours),
            pluralStringResource(Res.plurals.minutes_short, mins, mins)
        )
    } else {
        pluralStringResource(Res.plurals.minutes_short, mins, mins)
    }
}

@Composable
fun RunningTimerScreen(
    viewState: RunningViewState,
    onEvent: (RunningViewEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val calculation = viewState.calculation
    val settings = viewState.settings
    var showChangeSpeed by rememberSaveable { mutableStateOf(false) }

    if (showChangeSpeed) {
        ChangeSpeedSheet(
            powers = settings.availablePowers,
            currentPower = calculation.chargingSpeed,
            onAddPower = { onEvent(RunningViewEvent.AddPower(it)) },
            onApply = {
                onEvent(RunningViewEvent.ChangePower(it))
                showChangeSpeed = false
            },
            onDismiss = { showChangeSpeed = false }
        )
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
                text = stringResource(Res.string.charging_in_progress),
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
                // The 48dp speed row replaces 12dp of padding and 12dp of spacing so the rows below keep their positions.
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EditableStatRow(
                        label = stringResource(Res.string.charging_speed),
                        value = stringResource(
                            Res.string.power_value,
                            formatPower(calculation.chargingSpeed),
                            stringResource(Res.string.unit_kw)
                        ),
                        actionLabel = stringResource(Res.string.change_charging_speed),
                        onClick = { showChangeSpeed = true }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val currentKwh = remember(settings.batteryCapacity, calculation.estimatedPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, calculation.estimatedPercent)
                    }
                    StatRow(
                        label = stringResource(Res.string.current_percent),
                        value = stringResource(
                            Res.string.percent_and_energy,
                            formatLocaleNumber(calculation.estimatedPercent.roundToInt().toFloat(), 0, 0),
                            formatLocaleNumber(currentKwh.toFloat(), 0, 2),
                            stringResource(Res.string.unit_kwh)
                        )
                    )

                    val targetKwh = remember(settings.batteryCapacity, settings.maxPercent) {
                        ChargingCalculator.calculateKwh(settings.batteryCapacity, settings.maxPercent)
                    }
                    StatRow(
                        label = stringResource(Res.string.target_percent),
                        value = stringResource(
                            Res.string.percent_and_energy,
                            formatLocaleNumber(settings.maxPercent.roundToInt().toFloat(), 0, 0),
                            formatLocaleNumber(targetKwh.toFloat(), 0, 2),
                            stringResource(Res.string.unit_kwh)
                        )
                    )

                    StatRow(
                        label = stringResource(Res.string.time_remaining),
                        value = if (viewState.estimatedEndTimeText.isNotEmpty()) {
                            stringResource(
                                Res.string.value_with_separator,
                                formatTime(calculation.timeRemainingMinutes),
                                viewState.estimatedEndTimeText
                            )
                        } else {
                            formatTime(calculation.timeRemainingMinutes)
                        }
                    )

                    viewState.milestoneEstimate?.let { estimate ->
                        StatRow(
                            label = stringResource(
                                Res.string.time_to_percent,
                                formatLocaleNumber(MILESTONE_PERCENT, 0, 0)
                            ),
                            value = if (viewState.milestoneEtaText.isNotEmpty()) {
                                stringResource(
                                    Res.string.value_with_separator,
                                    if (estimate.reached) stringResource(Res.string.reached)
                                    else formatElapsedTime(estimate.minutesRemaining),
                                    viewState.milestoneEtaText
                                )
                            } else if (estimate.reached) {
                                stringResource(Res.string.reached)
                            } else {
                                formatElapsedTime(estimate.minutesRemaining)
                            }
                        )
                    }

                    StatRow(
                        label = stringResource(Res.string.elapsed_time),
                        value = formatElapsedTime(viewState.elapsedMinutes)
                    )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { onEvent(RunningViewEvent.StopCharging) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(Res.string.stop_timer), fontSize = 18.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeSpeedSheet(
    powers: List<Float>,
    currentPower: Float,
    onAddPower: (Float) -> Unit,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableFloatStateOf(currentPower) }
    var showCustomPowerDialog by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(Res.string.change_speed), style = MaterialTheme.typography.titleLarge)
            ChargingPowerChips(
                powers = (powers + currentPower).distinct().sorted(),
                selectedPower = selected,
                onPowerSelected = { selected = it },
                onAddCustomPower = { showCustomPowerDialog = true }
            )
            Button(
                onClick = { onApply(selected) },
                enabled = selected != currentPower,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(stringResource(Res.string.apply)) }
        }
    }
    if (showCustomPowerDialog) {
        CustomPowerDialog(
            onDismiss = { showCustomPowerDialog = false },
            onConfirm = { power ->
                onAddPower(power)
                selected = power
                showCustomPowerDialog = false
            }
        )
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
                    text = stringResource(
                        Res.string.percent_and_energy,
                        formatLocaleNumber(currentPercent.roundToInt().toFloat(), 0, 0),
                        formatLocaleNumber(currentKwh.toFloat(), 0, 2),
                        stringResource(Res.string.unit_kwh)
                    ),
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

@Composable
fun EditableStatRow(
    label: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditIcon,
                    contentDescription = actionLabel,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun formatTime(minutes: Int): String {
    if (minutes <= 0) return stringResource(Res.string.complete)
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        stringResource(
            Res.string.duration_hours_minutes,
            pluralStringResource(Res.plurals.hours_short, hours, hours),
            pluralStringResource(Res.plurals.minutes_short, mins, mins)
        )
    } else {
        pluralStringResource(Res.plurals.minutes_short, mins, mins)
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

@Preview(name = "RunningTimerScreen - Time to 80% hidden")
@Composable
private fun RunningTimerScreenPreview() {
    val state = RunningViewState(
        estimatedEndTimeText = "15:40",
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

@Preview(name = "RunningTimerScreen - Time to 80% shown")
@Composable
private fun RunningTimerScreenAltPreview() {
    val state = RunningViewState(
        estimatedEndTimeText = "4:05 PM",
        milestoneEstimate = MilestoneEstimate(35, 14 * 60L + 5),
        milestoneEtaText = "2:05 PM",
        settings = ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 7.2f,
            availablePowers = listOf(3.6f, 7.2f, 11f),
            startPercent = 10f,
            maxPercent = 100f
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

@Preview(name = "RunningTimerScreen - Time to 80% reached")
@Composable
private fun RunningTimerScreenReachedPreview() {
    val state = RunningViewState(
        estimatedEndTimeText = "6:53 PM",
        milestoneEstimate = MilestoneEstimate(0, 1_800_000_000_000L, reached = true),
        milestoneEtaText = "5:48 PM",
        settings = ChargingSettings(60f, 11f, 20f, 100f),
        calculation = ChargingCalculation(
            timeRemainingMinutes = 60,
            estimatedPercent = 82f,
            chargingSpeed = 11f
        ),
        elapsedMinutes = 202
    )
    MaterialTheme {
        Scaffold {
            RunningTimerScreen(viewState = state, onEvent = {})
        }
    }
}
