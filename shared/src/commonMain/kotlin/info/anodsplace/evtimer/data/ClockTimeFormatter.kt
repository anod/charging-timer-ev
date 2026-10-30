package info.anodsplace.evtimer.data

fun interface ClockTimeFormatter {
    /** Formats epoch milliseconds as a local clock time using the platform's time preferences. */
    fun format(epochMilliseconds: Long): String
}
