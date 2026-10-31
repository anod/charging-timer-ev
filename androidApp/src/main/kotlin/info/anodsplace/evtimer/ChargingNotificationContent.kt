package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.buildChargingNotificationText
import java.util.Locale

data class ChargingNotificationContent(
    val sessionId: Long,
    val percent: Int,
    val text: String,
    val segmentLengths: List<Int>,
    val speedChangePoints: List<Int>
) {
    val criticalText: String get() = "$percent%"

    companion object {
        fun from(status: ChargingStatus, formatter: ClockTimeFormatter): ChargingNotificationContent {
            val percent = status.currentPercent.toInt().coerceIn(0, 100)
            val speed = String.format(Locale.US, "%.1f kW", status.calculation.chargingSpeed)
            val eta = status.estimatedEndTime.takeIf { it > 0 }?.let(formatter::format)
            val changes = status.snapshot?.let { snapshot ->
                snapshot.segments.drop(1).map {
                    ChargingCalculator.percentAt(snapshot, it.startedAt).toInt().coerceIn(0, 100)
                }
            }.orEmpty()
            // The absolute 0-100 battery scale keeps progress and the chip consistent.
            // Sub-percent speed changes share a point; avoid zero-length framework segments.
            val boundaries = (listOf(0) + changes + 100).distinct().sorted()
            return ChargingNotificationContent(
                sessionId = status.startTime,
                percent = percent,
                text = buildChargingNotificationText(status, percent, speed, eta),
                segmentLengths = boundaries.zipWithNext { start, end -> end - start },
                speedChangePoints = changes.filter { it > 0 }.distinct()
            )
        }
    }
}
