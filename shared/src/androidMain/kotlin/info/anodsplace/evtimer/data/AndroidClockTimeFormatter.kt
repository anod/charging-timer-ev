package info.anodsplace.evtimer.data

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

class AndroidClockTimeFormatter(private val context: Context) : ClockTimeFormatter {
    override fun format(epochMilliseconds: Long): String =
        DateFormat.getTimeFormat(context).format(Date(epochMilliseconds))
}
