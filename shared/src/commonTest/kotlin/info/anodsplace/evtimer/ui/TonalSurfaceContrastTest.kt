package info.anodsplace.evtimer.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TonalSurfaceContrastTest {
    @Test
    fun staticTextPassesAaOverEveryWaveCombination() {
        for (scheme in listOf(LightColors, DarkColors)) {
            for (background in waveBackgrounds(scheme)) {
                for (surface in listOf(scheme.surfaceContainer, scheme.surfaceContainerLow)) {
                    val panel = tonalSurfaceColor(surface, usesDynamicColor = false)
                    assertEquals(0.9f, panel.alpha, 0.001f)
                    val painted = panel.compositeOver(background)
                    assertContrast(scheme.onSurface, painted)
                    assertContrast(scheme.onSurfaceVariant, painted)
                    assertContrast(scheme.primary, painted)
                }
                assertContrast(scheme.onSurface, background)
            }
            assertContrast(scheme.onSurface, scheme.surfaceContainer)
            assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer)
        }
    }

    @Test
    fun dynamicTextSurfacesStayOpaqueRegardlessOfWallpaper() {
        val schemes = listOf(
            lightColorScheme(
                surfaceContainer = Color(0xFFECE6F0),
                onSurface = Color(0xFF1D1B20),
                onSurfaceVariant = Color(0xFF49454F)
            ),
            darkColorScheme(
                surfaceContainer = Color(0xFF2B2632),
                onSurface = Color(0xFFE6E0E9),
                onSurfaceVariant = Color(0xFFCAC4D0)
            )
        )
        for (scheme in schemes) {
            val panel = tonalSurfaceColor(scheme.surfaceContainer, usesDynamicColor = true)
            assertEquals(1f, panel.alpha)
            for (background in listOf(Color.Black, Color.White, Color.Magenta)) {
                assertEquals(panel, panel.compositeOver(background))
                assertContrast(scheme.onSurface, panel)
                assertContrast(scheme.onSurfaceVariant, panel)
            }
        }
    }

    private fun waveBackgrounds(scheme: ColorScheme): List<Color> = (0..7).map { coverage ->
        var background = scheme.background
        val layers = listOf(
            scheme.tertiaryContainer.copy(alpha = 0.18f),
            scheme.primaryContainer.copy(alpha = 0.14f),
            scheme.primaryContainer.copy(alpha = 0.12f)
        )
        layers.forEachIndexed { index, layer ->
            if (coverage and (1 shl index) != 0) background = layer.compositeOver(background)
        }
        background
    }

    private fun assertContrast(text: Color, background: Color) {
        val textLuminance = text.luminance()
        val backgroundLuminance = background.luminance()
        val contrast = (maxOf(textLuminance, backgroundLuminance) + 0.05f) /
            (minOf(textLuminance, backgroundLuminance) + 0.05f)
        assertTrue(contrast >= 4.5f, "Contrast $contrast must meet WCAG AA (4.5:1)")
    }
}
