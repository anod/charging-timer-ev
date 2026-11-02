package info.anodsplace.evtimer.data

import java.text.DateFormat
import java.util.Date

actual fun formatSessionDate(epochMilliseconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMilliseconds))
