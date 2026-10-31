package info.anodsplace.evtimer.data

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

actual fun formatLocaleNumber(
    value: Float,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int
): String = NSNumberFormatter().apply {
    numberStyle = NSNumberFormatterDecimalStyle
    usesGroupingSeparator = false
    this.minimumFractionDigits = minimumFractionDigits.toULong()
    this.maximumFractionDigits = maximumFractionDigits.toULong()
}.stringFromNumber(NSNumber(double = value.toDouble())) ?: value.toString()
