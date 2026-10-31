package info.anodsplace.evtimer.data

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleNumberFormatterTest {
    @Test
    fun powerFormattingPreservesUsefulPrecisionInTheCurrentLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("7,36", formatPower(7.36f))
            assertEquals("11,0", formatPower(11f))
            assertEquals("7,4", formatPower(7.4f))

            Locale.setDefault(Locale.US)
            assertEquals("7.36", formatPower(7.36f))
            assertEquals("11.0", formatPower(11f))
            assertEquals("7.4", formatPower(7.4f))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
