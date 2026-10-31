package info.anodsplace.evtimer.data

import kotlin.math.roundToInt

object ChargingCalculator {
    fun calculateKwh(batteryCapacity: Float, percentage: Float): Int =
        (batteryCapacity * percentage / 100f).roundToInt()

    fun calculateChargingTime(
        batteryCapacity: Float,
        startPercent: Float,
        maxPercent: Float,
        chargingPower: Float
    ): Int = minutesToReach(
        ChargingSessionSnapshot.from(
            ChargingSettings(batteryCapacity, chargingPower, startPercent, maxPercent),
            0L
        ),
        0L,
        maxPercent
    ).roundToInt()

    /** Progress is capped at the session target; all integration uses epoch milliseconds. */
    fun percentAt(snapshot: ChargingSessionSnapshot, now: Long): Float =
        (snapshot.startPercent + energyAt(snapshot, now) * 100.0 / snapshot.batteryCapacity)
            .coerceAtMost(maxOf(snapshot.startPercent, snapshot.targetPercent).toDouble()).toFloat()

    /**
     * Fractional minutes until any battery percentage, assuming charging continues even
     * beyond the session target. Already reached percentages return zero.
     */
    fun minutesToReach(snapshot: ChargingSessionSnapshot, now: Long, percent: Float): Double {
        require(percent.isFinite() && percent in 0f..100f)
        val energyNeeded = snapshot.batteryCapacity.toDouble() *
            (percent - snapshot.startPercent).toDouble() / 100.0
        if (energyNeeded <= energyAt(snapshot, now)) return 0.0

        var remaining = energyNeeded
        snapshot.segments.forEachIndexed { index, segment ->
            val rate = segment.powerKw.toDouble() * snapshot.efficiency / 3_600_000.0
            val end = snapshot.segments.getOrNull(index + 1)?.startedAt
            val duration = end?.let { (it - segment.startedAt).toDouble() }
            if (duration == null || remaining <= duration * rate) {
                // Subtract relative times rather than adding a fractional duration to a
                // large epoch, retaining precision at minute/percent boundaries.
                return ((segment.startedAt - now).toDouble() + remaining / rate)
                    .coerceAtLeast(0.0) / 60_000.0
            }
            remaining -= duration * rate
        }
        error("Snapshot must contain a power segment")
    }

    fun predict(snapshot: ChargingSessionSnapshot, now: Long, plusMinutes: Double): Float {
        require(plusMinutes.isFinite() && plusMinutes >= 0.0)
        val milliseconds = plusMinutes * 60_000.0
        require(milliseconds <= Long.MAX_VALUE.toDouble())
        val offset = milliseconds.toLong()
        require(now <= Long.MAX_VALUE - offset)
        return percentAt(snapshot, now + offset)
    }

    private fun energyAt(snapshot: ChargingSessionSnapshot, now: Long): Double {
        var energy = 0.0
        snapshot.segments.forEachIndexed { index, segment ->
            val end = minOf(now, snapshot.segments.getOrNull(index + 1)?.startedAt ?: now)
            val elapsed = (end - segment.startedAt).coerceAtLeast(0L)
            energy += elapsed.toDouble() * segment.powerKw * snapshot.efficiency / 3_600_000.0
        }
        return energy
    }
}
