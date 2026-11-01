package info.anodsplace.evtimer.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargingWavesGeometryTest {
    @Test
    fun baselinesAreFixedFractionsOfHeight() {
        assertEquals(listOf(0.38f, 0.43f, 0.48f), chargingWaves.map { it.baselineFraction })
        for (wave in chargingWaves) {
            for (height in listOf(0f, 480f, 1200f, 2400f)) {
                val mean = (0 until 64).sumOf { wave.y(it / 64f, height).toDouble() } / 64
                assertTrue(abs(mean - height * wave.baselineFraction) < 0.001)
            }
        }
    }

    @Test
    fun generatedPointsStayInAmplitudeBoundsAcrossCachedPeriods() {
        for (wave in chargingWaves) {
            for (height in listOf(0f, 480f, 1200f, 2400f)) {
                for (sample in -64..128) {
                    val y = wave.y(sample / 64f, height)
                    val low = height * (wave.baselineFraction - wave.amplitudeFraction)
                    val high = height * (wave.baselineFraction + wave.amplitudeFraction)
                    assertTrue(y.isFinite())
                    assertTrue(y >= low - 0.001f && y <= high + 0.001f)
                    assertTrue(y in 0f..height)
                }
            }
        }
    }

    @Test
    fun translationEndpointsAreSeamlessIncludingStaticMotion() {
        for (wave in chargingWaves) {
            for (sample in 0..64) {
                val x = sample / 64f
                assertEquals(wave.y(x, 1200f), wave.y(x - 1f, 1200f), 0.001f)
                assertEquals(wave.y(x, 1200f), wave.y(x + 1f, 1200f), 0.001f)
            }
        }
    }

    @Test
    fun pointsScaleWithCanvasHeightAndPhaseSeparatesLayers() {
        for (wave in chargingWaves) {
            assertEquals(wave.y(0.2f, 600f) * 2, wave.y(0.2f, 1200f), 0.001f)
        }
        val wave = WaveGeometry(0.4f, 0.03f, 0f)
        assertEquals(400f, wave.y(0f, 1000f), 0.001f)
        assertEquals(430f, wave.y(0.25f, 1000f), 0.001f)
        assertEquals(370f, wave.y(0.75f, 1000f), 0.001f)
        assertEquals(wave.y(0.25f, 1000f), wave.copy(phase = 0.25f).y(0f, 1000f), 0.001f)
    }
}
