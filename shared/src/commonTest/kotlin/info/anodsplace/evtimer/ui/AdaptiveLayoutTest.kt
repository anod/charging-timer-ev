package info.anodsplace.evtimer.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutTest {
    @Test
    fun widthBreakpointsAndShortHeightAreIndependent() {
        assertEquals(AdaptiveWidth.Compact, adaptiveLayout(599.dp, 900.dp).width)
        assertEquals(AdaptiveWidth.Medium, adaptiveLayout(600.dp, 900.dp).width)
        assertEquals(AdaptiveWidth.Medium, adaptiveLayout(839.dp, 900.dp).width)
        assertEquals(AdaptiveWidth.Expanded, adaptiveLayout(840.dp, 900.dp).width)
        assertFalse(adaptiveLayout(840.dp, 479.dp).twoColumns)
        assertTrue(adaptiveLayout(840.dp, 480.dp).twoColumns)
    }

    @Test
    fun runningTimerUsesTwoColumnsForFoldAndTabletSizesWithoutChangingPhoneLayout() {
        assertFalse(runningTimerUsesTwoColumns(411.dp, 914.dp))
        assertFalse(runningTimerUsesTwoColumns(914.dp, 411.dp))
        assertFalse(runningTimerUsesTwoColumns(673.dp, 841.dp))
        assertFalse(runningTimerUsesTwoColumns(750.dp, 832.dp))
        assertTrue(runningTimerUsesTwoColumns(832.dp, 750.dp))
        assertTrue(runningTimerUsesTwoColumns(841.dp, 673.dp))
        assertFalse(runningTimerUsesTwoColumns(800.dp, 1280.dp))
        assertTrue(runningTimerUsesTwoColumns(1280.dp, 800.dp))
        assertTrue(runningTimerUsesTwoColumns(800.dp, 480.dp))
        assertFalse(runningTimerUsesTwoColumns(800.dp, 900.dp))
        assertTrue(runningTimerUsesTwoColumns(1100.dp, 900.dp))
        assertFalse(runningTimerUsesTwoColumns(767.dp, 600.dp))
        assertTrue(runningTimerUsesTwoColumns(768.dp, 600.dp))
        assertFalse(runningTimerUsesTwoColumns(1280.dp, 479.dp))
    }

    @Test
    fun runningTimerPaneWidthsPreserveTheGutterAndFullWidthOnCompactLayouts() {
        val density = Density(2f)
        assertEquals(304.5.dp, runningTimerPaneWidth(1282, density, twoColumns = true))
        assertEquals(328.dp, runningTimerPaneWidth(1376, density, twoColumns = true))
        assertEquals(641.dp, runningTimerPaneWidth(1282, density, twoColumns = false))
    }
}
