package info.anodsplace.evtimer.data

actual fun formatLocaleNumber(
    value: Float,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int
): String = js(
    """
    new Intl.NumberFormat(undefined, {
        useGrouping: false,
        minimumFractionDigits: minimumFractionDigits,
        maximumFractionDigits: maximumFractionDigits
    }).format(value)
    """
)
