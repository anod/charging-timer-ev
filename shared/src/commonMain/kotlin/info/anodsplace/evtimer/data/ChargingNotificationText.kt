package info.anodsplace.evtimer.data

import kotlin.math.roundToInt

fun buildChargingNotificationText(
    status: ChargingStatus,
    percentInt: Int,
    speed: String,
    eta: String?
): String = buildString {
    val target = status.targetPercent.roundToInt()
    val remainingMin = status.calculation.timeRemainingMinutes
    if (remainingMin > 0) {
        append(percentInt).append("% → ").append(target).append('%')
    } else {
        append("Target reached · ").append(target).append('%')
    }
    append(" • ").append(speed)
    if (remainingMin > 0) {
        val h = remainingMin / 60
        val m = remainingMin % 60
        append(" • ")
        if (h > 0) append("${h}h ${m}m left") else append("${m}m left")
    }
    if (eta != null) append(" • ETA ").append(eta)
}
