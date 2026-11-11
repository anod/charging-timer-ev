package info.anodsplace.evtimer.data

import kotlin.math.roundToInt
import kotlin.math.ceil

const val MILESTONE_PERCENT = 80f

data class MilestoneEstimate(
    val minutesRemaining: Int,
    val estimatedTime: Long,
    val reached: Boolean = false
)

object ChargingCalculator {
    fun calculateKwh(batteryCapacity: Float, percentage: Float): Int =
        (batteryCapacity * percentage / 100f).roundToInt()

    fun calculateChargingTime(
        batteryCapacity: Float,
        startPercent: Float,
        maxPercent: Float,
        chargingPower: Float,
        taperStartPercent: Float = ChargingSettings.DEFAULT.taperStartPercent,
        taperEndPercent: Float = ChargingSettings.DEFAULT.taperEndPercent,
        taperPowerCapKw: Float = ChargingSettings.DEFAULT.taperPowerCapKw
    ): Int = minutesToReach(
        ChargingSessionSnapshot.from(
            ChargingSettings(
                batteryCapacity = batteryCapacity,
                chargingPower = chargingPower,
                startPercent = startPercent,
                maxPercent = maxPercent,
                taperStartPercent = taperStartPercent,
                taperEndPercent = taperEndPercent,
                taperPowerCapKw = taperPowerCapKw
            ),
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

        val elapsedToTarget = elapsedToEnergy(snapshot, energyNeeded, includeEfficiency = true)
        // Keep sub-millisecond precision relative to the session start at large epochs.
        return ((snapshot.startTime - now).toDouble() + elapsedToTarget)
            .coerceAtLeast(0.0) / 60_000.0
    }

    fun estimateMilestone(snapshot: ChargingSessionSnapshot, now: Long): MilestoneEstimate? {
        if (snapshot.targetPercent <= MILESTONE_PERCENT ||
            snapshot.startPercent >= MILESTONE_PERCENT
        ) return null

        val crossingTime = snapshot.startTime + ceil(
            minutesToReach(snapshot, snapshot.startTime, MILESTONE_PERCENT) * 60_000.0
        ).toLong()
        val minutesToMilestone = (crossingTime - now).coerceAtLeast(0L) / 60_000.0

        return MilestoneEstimate(
            minutesRemaining = minutesToMilestone.roundToInt(),
            estimatedTime = crossingTime,
            reached = now >= crossingTime
        )
    }

    fun predict(snapshot: ChargingSessionSnapshot, now: Long, plusMinutes: Double): Float {
        require(plusMinutes.isFinite() && plusMinutes >= 0.0)
        val milliseconds = plusMinutes * 60_000.0
        require(milliseconds <= Long.MAX_VALUE.toDouble())
        val offset = milliseconds.toLong()
        require(now <= Long.MAX_VALUE - offset)
        return percentAt(snapshot, now + offset)
    }

    /** Nominal energy uses the taper cap but ignores the frozen estimate factor. */
    fun nominalEnergyAt(snapshot: ChargingSessionSnapshot, now: Long): Double {
        return energyAt(snapshot, now, includeEfficiency = false)
    }

    private data class RateWindow(val powerKw: Double, val nextBoundaryKwh: Double?)

    private fun rateWindow(
        snapshot: ChargingSessionSnapshot,
        segment: PowerSegment,
        energyKwh: Double
    ): RateWindow {
        val startBoundary = snapshot.batteryCapacity.toDouble() *
            (snapshot.taperStartPercent - snapshot.startPercent) / 100.0
        val endBoundary = snapshot.batteryCapacity.toDouble() *
            (snapshot.taperEndPercent - snapshot.startPercent) / 100.0
        return when {
            energyKwh < startBoundary -> RateWindow(segment.powerKw.toDouble(), startBoundary)
            energyKwh < endBoundary -> RateWindow(
                minOf(segment.powerKw, snapshot.taperPowerCapKw).toDouble(),
                endBoundary
            )
            else -> RateWindow(segment.powerKw.toDouble(), null)
        }
    }

    private fun elapsedToEnergy(
        snapshot: ChargingSessionSnapshot,
        targetEnergyKwh: Double,
        includeEfficiency: Boolean
    ): Double {
        var energy = 0.0
        var elapsed = 0.0
        snapshot.segments.forEachIndexed { index, segment ->
            var available = snapshot.segments.getOrNull(index + 1)
                ?.let { (it.startedAt - segment.startedAt).toDouble() }
                ?: Double.POSITIVE_INFINITY
            while (energy < targetEnergyKwh && available > 0.0) {
                val window = rateWindow(snapshot, segment, energy)
                val efficiency = if (includeEfficiency) {
                    (segment.efficiency ?: snapshot.efficiency).toDouble()
                } else 1.0
                val rate = window.powerKw * efficiency / 3_600_000.0
                val nextEnergy = minOf(targetEnergyKwh, window.nextBoundaryKwh ?: targetEnergyKwh)
                val timeToNextEnergy = (nextEnergy - energy) / rate
                if (timeToNextEnergy <= available) {
                    energy = nextEnergy
                    elapsed += timeToNextEnergy
                    available -= timeToNextEnergy
                    if (energy >= targetEnergyKwh) return elapsed
                } else {
                    energy += available * rate
                    elapsed += available
                    available = 0.0
                }
            }
        }
        error("Snapshot must contain a power segment")
    }

    private fun energyAt(
        snapshot: ChargingSessionSnapshot,
        now: Long,
        includeEfficiency: Boolean = true
    ): Double {
        var energy = 0.0
        snapshot.segments.forEachIndexed { index, segment ->
            if (now <= segment.startedAt) return@forEachIndexed
            val end = minOf(now, snapshot.segments.getOrNull(index + 1)?.startedAt ?: now)
            var remaining = (end - segment.startedAt).coerceAtLeast(0L).toDouble()
            while (remaining > 0.0) {
                val window = rateWindow(snapshot, segment, energy)
                val efficiency = if (includeEfficiency) {
                    (segment.efficiency ?: snapshot.efficiency).toDouble()
                } else 1.0
                val rate = window.powerKw * efficiency / 3_600_000.0
                val timeToBoundary = window.nextBoundaryKwh?.let { (it - energy) / rate }
                    ?: Double.POSITIVE_INFINITY
                val elapsed = minOf(remaining, timeToBoundary)
                energy += elapsed * rate
                remaining -= elapsed
            }
        }
        return energy
    }
}
