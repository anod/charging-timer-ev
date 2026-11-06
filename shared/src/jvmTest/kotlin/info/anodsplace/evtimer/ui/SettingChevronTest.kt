package info.anodsplace.evtimer.ui

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingChevronTest {
    @Test
    fun rtlCollapsedChevronMirrorsLtrAcrossVerticalAxis() {
        val ltr = settingChevronSegments(expanded = false, rtl = false)
        val rtl = settingChevronSegments(expanded = false, rtl = true)

        assertEquals(
            ltr.map { segment ->
                ChevronSegment(
                    start = Offset(1f - segment.start.x, segment.start.y),
                    end = Offset(1f - segment.end.x, segment.end.y)
                )
            },
            rtl
        )
    }

    @Test
    fun expandedChevronIsIdenticalInLtrAndRtl() {
        assertEquals(
            settingChevronSegments(expanded = true, rtl = false),
            settingChevronSegments(expanded = true, rtl = true)
        )
    }

    @Test
    fun ltrCollapsedChevronApexPointsRightOfItsArms() {
        val segments = settingChevronSegments(expanded = false, rtl = false)
        val apex = segments.first().end

        assertEquals(apex, segments.last().start)
        assertTrue(apex.x > segments.first().start.x)
        assertTrue(apex.x > segments.last().end.x)
    }
}
