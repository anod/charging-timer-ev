package info.anodsplace.evtimer.data

expect fun formatLocaleNumber(
    value: Float,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int
): String

fun formatPower(value: Float): String = formatLocaleNumber(value, 1, 2)
