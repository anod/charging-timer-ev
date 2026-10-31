package info.anodsplace.evtimer.data

import kotlin.math.roundToInt

data class ChargingNotificationTextFormat(
    val progress: (Int, Int, String) -> String,
    val targetReached: (Int, String) -> String,
    val durationHoursMinutes: (Int, Int) -> String,
    val durationMinutes: (Int) -> String,
    val eta: (String) -> String,
    val append: (String, String) -> String
)

fun buildChargingNotificationText(
    status: ChargingStatus,
    percentInt: Int,
    speed: String,
    eta: String?,
    format: ChargingNotificationTextFormat
): String = buildString {
    val target = status.targetPercent.roundToInt()
    val remainingMin = status.calculation.timeRemainingMinutes
    var text = if (remainingMin > 0) {
        format.progress(percentInt, target, speed)
    } else {
        format.targetReached(target, speed)
    }
    if (remainingMin > 0) {
        val h = remainingMin / 60
        val m = remainingMin % 60
        val duration = if (h > 0) format.durationHoursMinutes(h, m) else format.durationMinutes(m)
        text = format.append(text, duration)
    }
    if (eta != null) text = format.append(text, format.eta(eta))
    append(text)
}
