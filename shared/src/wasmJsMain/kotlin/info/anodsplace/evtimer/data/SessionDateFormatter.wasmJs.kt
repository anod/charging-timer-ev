package info.anodsplace.evtimer.data

actual fun formatSessionDate(epochMilliseconds: Long): String =
    js("new Date(Number(epochMilliseconds)).toLocaleString()")
