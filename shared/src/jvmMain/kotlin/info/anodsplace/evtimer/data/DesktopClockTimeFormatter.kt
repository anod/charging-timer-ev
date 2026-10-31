package info.anodsplace.evtimer.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DesktopClockTimeFormatter : ClockTimeFormatter {
    override fun format(epochMilliseconds: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMilliseconds))
}
