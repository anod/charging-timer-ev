package info.anodsplace.evtimer.data

data class PowerSegment(
    val startedAt: Long,
    val powerKw: Float,
    val efficiency: Float? = null
) {
    init {
        require(powerKw.isFinite() && powerKw > 0f)
        require(efficiency == null || (efficiency.isFinite() && efficiency > 0f && efficiency <= 1f))
    }
}

data class ChargingSessionSnapshot(
    val batteryCapacity: Float,
    val startPercent: Float,
    val targetPercent: Float,
    val segments: List<PowerSegment>,
    val presetId: String? = null,
    val presetName: String? = null,
    val efficiency: Float = 1f,
    val presetEdited: Boolean = false,
    val taperStartPercent: Float = ChargingSettings.DEFAULT.taperStartPercent,
    val taperEndPercent: Float = ChargingSettings.DEFAULT.taperEndPercent,
    val taperPowerCapKw: Float = ChargingSettings.DEFAULT.taperPowerCapKw
) {
    init {
        require(batteryCapacity.isFinite() && batteryCapacity > 0f)
        require(startPercent.isFinite() && startPercent in 0f..100f)
        require(targetPercent.isFinite() && targetPercent in 0f..100f)
        require(efficiency.isFinite() && efficiency > 0f && efficiency <= 1f)
        require(taperStartPercent.isFinite() && taperStartPercent in 0f..100f)
        require(taperEndPercent.isFinite() && taperEndPercent in 0f..100f)
        require(taperStartPercent < taperEndPercent)
        require(taperPowerCapKw.isFinite() && taperPowerCapKw > 0f)
        require(segments.isNotEmpty())
        require(segments.zipWithNext().all { (a, b) -> a.startedAt < b.startedAt })
    }

    val startTime: Long get() = segments.first().startedAt

    fun powerAt(time: Long): Float =
        segments.lastOrNull { it.startedAt <= time }?.powerKw ?: segments.first().powerKw

    /** Appends a segment at [now]; returns null when the power is unchanged. */
    fun withPower(now: Long, powerKw: Float, efficiency: Float? = null): ChargingSessionSnapshot? {
        val last = segments.last()
        // A clock that did not advance past the last change still has to keep segments ordered.
        val startedAt = maxOf(now, last.startedAt + 1)
        if (powerAt(startedAt) == powerKw) return null
        return copy(segments = segments + PowerSegment(startedAt, powerKw, efficiency))
    }

    companion object {
        fun from(settings: ChargingSettings, startedAt: Long) = ChargingSessionSnapshot(
            batteryCapacity = settings.batteryCapacity,
            startPercent = settings.startPercent,
            targetPercent = settings.maxPercent,
            segments = listOf(PowerSegment(startedAt, settings.chargingPower)),
            taperStartPercent = settings.taperStartPercent,
            taperEndPercent = settings.taperEndPercent,
            taperPowerCapKw = settings.taperPowerCapKw
        )
    }
}
