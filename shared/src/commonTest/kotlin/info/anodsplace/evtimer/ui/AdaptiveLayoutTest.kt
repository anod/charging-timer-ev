package info.anodsplace.evtimer.ui

import androidx.compose.ui.unit.dp
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
}
