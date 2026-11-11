package info.anodsplace.evtimer.data

enum class CalibrationExclusion {
    READING_REQUIRED, NO_GAIN, INTERRUPTED, NO_ENERGY, MIXED_POWER, PRESET_REQUIRED
}

data class CalibrationKey(val presetId: String, val powerKw: Float)

data class CalibrationEstimate(val factor: Float = 1f, val eligibleCount: Int = 0)

data class ChargingCalibration(
    val estimates: Map<CalibrationKey, CalibrationEstimate> = emptyMap()
) {
    fun estimate(presetId: String?, powerKw: Float): CalibrationEstimate =
        presetId?.let { estimates[CalibrationKey(it, powerKw)] } ?: CalibrationEstimate()

    fun factor(presetId: String?, powerKw: Float): Float = estimate(presetId, powerKw).factor

    companion object {
        fun exclusion(entry: ChargingHistoryEntry, actual: Float? = entry.actualPercent): CalibrationExclusion? {
            if (entry.interrupted) return CalibrationExclusion.INTERRUPTED
            if (actual == null || !actual.isFinite() || actual !in 0f..100f) return CalibrationExclusion.READING_REQUIRED
            if (actual <= entry.snapshot.startPercent) return CalibrationExclusion.NO_GAIN
            if (entry.snapshot.presetId == null) return CalibrationExclusion.PRESET_REQUIRED
            val duration = entry.endedAt.toDouble() - entry.snapshot.startTime.toDouble()
            if (!duration.isFinite() || duration <= 0.0) return CalibrationExclusion.NO_ENERGY
            if (singleSpeedPower(entry) == null) return CalibrationExclusion.MIXED_POWER
            val energy = ChargingCalculator.nominalEnergyAt(entry.snapshot, entry.endedAt)
            if (!energy.isFinite() || energy <= 0.0) return CalibrationExclusion.NO_ENERGY
            return null
        }

        /** The exact power used by all positive-duration segments, or null for mixed/empty history. */
        fun singleSpeedPower(entry: ChargingHistoryEntry): Float? {
            val powers = entry.snapshot.segments.mapIndexedNotNull { index, segment ->
                val end = minOf(
                    entry.endedAt,
                    entry.snapshot.segments.getOrNull(index + 1)?.startedAt ?: entry.endedAt
                )
                segment.powerKw.takeIf { end > segment.startedAt }
            }.toSet()
            return powers.singleOrNull()
        }

        fun sample(entry: ChargingHistoryEntry): Double? {
            if (!entry.calibrationEnabled || exclusion(entry) != null) return null
            val unityEfficiencySnapshot = entry.snapshot.copy(
                efficiency = 1f,
                segments = entry.snapshot.segments.map { it.copy(efficiency = 1f) }
            )
            val unityDuration = ChargingCalculator.minutesToReach(
                unityEfficiencySnapshot,
                entry.snapshot.startTime,
                requireNotNull(entry.actualPercent)
            )
            val observedDuration = (entry.endedAt - entry.snapshot.startTime).toDouble() / 60_000.0
            val sample = unityDuration / observedDuration
            return sample.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.7, 1.0)
        }

        fun derive(
            history: List<ChargingHistoryEntry>,
            presets: List<ChargingPreset>,
            capacityOverrides: Map<String, Float> = emptyMap()
        ): ChargingCalibration {
            val capacities = presets.associate { it.id to it.settings.batteryCapacity } + capacityOverrides
            val estimates = mutableMapOf<CalibrationKey, CalibrationEstimate>()
            history.sortedWith(compareBy<ChargingHistoryEntry> { it.endedAt }.thenBy { it.id }).forEach { entry ->
                val id = entry.snapshot.presetId ?: return@forEach
                if (capacities[id] != entry.snapshot.batteryCapacity) return@forEach
                val power = singleSpeedPower(entry) ?: return@forEach
                val sample = sample(entry) ?: return@forEach
                val key = CalibrationKey(id, power)
                val previous = estimates[key] ?: CalibrationEstimate()
                estimates[key] = CalibrationEstimate(
                    factor = (0.7 * previous.factor + 0.3 * sample).toFloat(),
                    eligibleCount = previous.eligibleCount + 1
                )
            }
            return ChargingCalibration(estimates)
        }
    }
}

/** Blank clears the reading; the caller distinguishes blank from malformed input. */
fun parseCarReading(text: String): Float? {
    val trimmed = text.trim()
    if (!Regex("""(?:\d+(?:[.,]\d*)?|[.,]\d+)""").matches(trimmed)) return null
    return trimmed.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..100f }
}
