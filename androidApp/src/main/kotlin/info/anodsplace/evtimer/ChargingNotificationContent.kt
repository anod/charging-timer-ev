package info.anodsplace.evtimer

import android.content.Context
import info.anodsplace.evtimer.data.ChargingCalculator
import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.data.ChargingNotificationTextFormat
import info.anodsplace.evtimer.data.buildChargingNotificationText
import info.anodsplace.evtimer.data.formatLocaleNumber

data class ChargingNotificationTextFormatter(
    val percent: (Int) -> String,
    val power: (Float) -> String,
    val text: ChargingNotificationTextFormat
)

data class ChargingNotificationContent(
    val sessionId: Long,
    val percent: Int,
    val criticalText: String,
    val text: String,
    val segmentLengths: List<Int>,
    val speedChangePoints: List<Int>
) {
    companion object {
        fun from(
            status: ChargingStatus,
            formatter: ClockTimeFormatter,
            textFormatter: ChargingNotificationTextFormatter
        ): ChargingNotificationContent {
            val percent = status.currentPercent.toInt().coerceIn(0, 100)
            val speed = textFormatter.power(status.calculation.chargingSpeed)
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
                criticalText = textFormatter.percent(percent),
                text = buildChargingNotificationText(status, percent, speed, eta, textFormatter.text),
                segmentLengths = boundaries.zipWithNext { start, end -> end - start },
                speedChangePoints = changes.filter { it > 0 }.distinct()
            )
        }
    }
}

fun chargingNotificationTextFormatter(context: Context): ChargingNotificationTextFormatter =
    ChargingNotificationTextFormatter(
        percent = { value -> context.getString(R.string.notification_percent, value) },
        power = { value ->
            context.getString(
                R.string.power_value,
                formatLocaleNumber(value, 1, 1),
                context.getString(R.string.unit_kw)
            )
        },
        text = ChargingNotificationTextFormat(
            progress = { current, target, speed ->
                context.getString(R.string.notification_progress_format, current, target, speed)
            },
            targetReached = { target, speed ->
                context.getString(R.string.notification_target_reached, target, speed)
            },
            durationHoursMinutes = { hours, minutes ->
                context.getString(
                    R.string.notification_duration_hours_minutes,
                    context.resources.getQuantityString(R.plurals.notification_hours_short, hours, hours),
                    context.resources.getQuantityString(R.plurals.notification_minutes_short, minutes, minutes)
                )
            },
            durationMinutes = { minutes ->
                context.getString(
                    R.string.notification_duration_minutes,
                    context.resources.getQuantityString(R.plurals.notification_minutes_short, minutes, minutes)
                )
            },
            eta = { value -> context.getString(R.string.notification_eta, value) },
            append = { first, second -> context.getString(R.string.notification_append, first, second) }
        )
    )
