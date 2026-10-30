package info.anodsplace.evtimer.data

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle

class IosClockTimeFormatter : ClockTimeFormatter {
    override fun format(epochMilliseconds: Long): String =
        NSDateFormatter().apply {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
        }.stringFromDate(NSDate(timeIntervalSince1970 = epochMilliseconds / 1000.0))
}
