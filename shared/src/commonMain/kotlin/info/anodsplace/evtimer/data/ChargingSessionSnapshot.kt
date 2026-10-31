package info.anodsplace.evtimer.data

data class PowerSegment(val startedAt: Long, val powerKw: Float) {
    init {
        require(powerKw.isFinite() && powerKw > 0f)
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
    val presetEdited: Boolean = false
) {
    init {
        require(batteryCapacity.isFinite() && batteryCapacity > 0f)
        require(startPercent.isFinite() && startPercent in 0f..100f)
        require(targetPercent.isFinite() && targetPercent in 0f..100f)
        require(efficiency.isFinite() && efficiency > 0f && efficiency <= 1f)
        require(segments.isNotEmpty())
        require(segments.zipWithNext().all { (a, b) -> a.startedAt < b.startedAt })
    }

    val startTime: Long get() = segments.first().startedAt

    companion object {
        fun from(settings: ChargingSettings, startedAt: Long) = ChargingSessionSnapshot(
            batteryCapacity = settings.batteryCapacity,
            startPercent = settings.startPercent,
            targetPercent = settings.maxPercent,
            segments = listOf(PowerSegment(startedAt, settings.chargingPower))
        )
    }
}
