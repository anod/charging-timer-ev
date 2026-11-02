package info.anodsplace.evtimer.data

data class SummarySegment(val powerKw: Float, val durationMillis: Long)

val ChargingHistoryEntry.reachedTarget: Boolean
    get() = !interrupted && endPercent != null && endPercent >= snapshot.targetPercent

data class ChargingSummary(
    val startPercent: Float,
    val endPercent: Float?,
    val durationMillis: Long?,
    val energyKwh: Float?,
    val averageKw: Float?,
    val segments: List<SummarySegment>,
    val presetName: String?,
    val presetEdited: Boolean,
    val interrupted: Boolean
)

fun ChargingHistoryEntry.summary(): ChargingSummary {
    val duration = if (interrupted) null else (endedAt - snapshot.startTime).coerceAtLeast(0L)
    val end = actualPercent ?: endPercent
    val energy = end?.let { snapshot.batteryCapacity * (it - snapshot.startPercent).coerceAtLeast(0f) / 100f }
    return ChargingSummary(
        startPercent = snapshot.startPercent,
        endPercent = end,
        durationMillis = duration,
        energyKwh = energy,
        averageKw = if (duration != null && duration > 0L && energy != null) {
            energy * 3_600_000f / duration
        } else null,
        segments = if (duration == null) emptyList() else snapshot.segments.mapIndexedNotNull { index, segment ->
            val endTime = minOf(endedAt, snapshot.segments.getOrNull(index + 1)?.startedAt ?: endedAt)
            (endTime - segment.startedAt).takeIf { it > 0L }?.let { SummarySegment(segment.powerKw, it) }
        },
        presetName = snapshot.presetName?.takeIf { it.isNotBlank() },
        presetEdited = snapshot.presetEdited,
        interrupted = interrupted
    )
}
