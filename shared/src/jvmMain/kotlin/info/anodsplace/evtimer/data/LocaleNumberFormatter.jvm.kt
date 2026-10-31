package info.anodsplace.evtimer.data

import java.text.NumberFormat
import java.util.Locale

actual fun formatLocaleNumber(
    value: Float,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int
): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    isGroupingUsed = false
    this.minimumFractionDigits = minimumFractionDigits
    this.maximumFractionDigits = maximumFractionDigits
}.format(value.toDouble())
