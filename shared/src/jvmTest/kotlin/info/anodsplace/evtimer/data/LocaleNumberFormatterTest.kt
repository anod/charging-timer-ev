package info.anodsplace.evtimer.data

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleNumberFormatterTest {
    @Test
    fun usesTheCurrentLocaleDecimalSeparatorAndStablePowerPrecision() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("7,4", formatLocaleNumber(7.4f, 1, 1))
            assertEquals("7,0", formatLocaleNumber(7f, 1, 1))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
