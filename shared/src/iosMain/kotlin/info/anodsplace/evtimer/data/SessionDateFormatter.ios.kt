package info.anodsplace.evtimer.data

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle

actual fun formatSessionDate(epochMilliseconds: Long): String =
    NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(NSDate(timeIntervalSince1970 = epochMilliseconds / 1000.0))
