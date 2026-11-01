package info.anodsplace.evtimer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.sin

internal data class WaveGeometry(
    val baselineFraction: Float,
    val amplitudeFraction: Float,
    val phase: Float
) {
    fun y(xFraction: Float, height: Float): Float =
        height * (baselineFraction + amplitudeFraction * sin((xFraction + phase) * 2 * PI).toFloat())
}

internal val chargingWaves = listOf(
    WaveGeometry(baselineFraction = 0.38f, amplitudeFraction = 0.025f, phase = 0f),
    WaveGeometry(baselineFraction = 0.43f, amplitudeFraction = 0.035f, phase = 0.35f),
    WaveGeometry(baselineFraction = 0.48f, amplitudeFraction = 0.02f, phase = 0.7f)
)

private val waveDurations = listOf(18_000, 24_000, 32_000)
private const val SamplesPerPeriod = 64

@Composable
fun ChargingWavesBackground(modifier: Modifier = Modifier) {
    // InfiniteTransition suspends at scale zero; periodic endpoints give the same static wave.
    val transition = rememberInfiniteTransition()
    val offsets = waveDurations.map { duration ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing))
        )
    }
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(
        scheme.tertiaryContainer.copy(alpha = 0.18f),
        scheme.primaryContainer.copy(alpha = 0.14f),
        scheme.primaryContainer.copy(alpha = 0.12f)
    )
    Box(modifier.fillMaxSize().drawWithCache {
        // Three periods cover the canvas while translating one full period in either direction.
        val paths = chargingWaves.map { wave ->
            Path().apply {
                moveTo(-size.width, size.height)
                for (sample in -SamplesPerPeriod..2 * SamplesPerPeriod) {
                    val x = sample.toFloat() / SamplesPerPeriod
                    lineTo(x * size.width, wave.y(x, size.height))
                }
                lineTo(2 * size.width, size.height)
                close()
            }
        }
        onDrawBehind {
            clipRect {
                paths.forEachIndexed { index, path ->
                    val direction = if (index == 1) 1f else -1f
                    withTransform({
                        translate(left = direction * offsets[index].value * size.width)
                    }) {
                        drawPath(path, colors[index])
                    }
                }
            }
        }
    })
}
