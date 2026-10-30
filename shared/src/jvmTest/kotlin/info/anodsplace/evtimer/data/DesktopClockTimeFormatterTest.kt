package info.anodsplace.evtimer.data

import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopClockTimeFormatterTest {
    @Test
    fun followsLocaleAndLocalTimeZoneChanges() {
        val originalLocale = Locale.getDefault(Locale.Category.FORMAT)
        val originalTimeZone = TimeZone.getDefault()
        val formatter = DesktopClockTimeFormatter()
        val time = Instant.parse("2026-10-30T15:40:00Z").toEpochMilli()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            Locale.setDefault(Locale.Category.FORMAT, Locale.US)
            // JDK locale data may separate the day period with a narrow non-breaking space.
            assertEquals("3:40 PM", formatter.format(time).replace('\u202f', ' '))

            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
            assertEquals("15:40", formatter.format(time))

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("00:40", formatter.format(time))
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }
}
