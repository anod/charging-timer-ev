package info.anodsplace.evtimer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

interface ChargingRepository {
    suspend fun saveSettings(settings: ChargingSettings)
    fun observeSettings(): Flow<ChargingSettings>
    fun observeNotifyAt80(): Flow<Boolean>
    suspend fun setNotifyAt80(enabled: Boolean)
    suspend fun createSnapshot(startedAt: Long): ChargingSessionSnapshot =
        ChargingSessionSnapshot.from(observeSettings().first(), startedAt)
    suspend fun efficiencyFor(presetId: String?, batteryCapacity: Float, powerKw: Float): Float = 1f
    suspend fun changePower(session: ChargingSession, changedAt: Long, powerKw: Float) {
        val snapshot = session.snapshot ?: return
        val efficiency = efficiencyFor(snapshot.presetId, snapshot.batteryCapacity, powerKw)
        val updated = snapshot.withPower(changedAt, powerKw, efficiency) ?: return
        saveSession(session.copy(snapshot = updated))
    }
    suspend fun saveSession(session: ChargingSession)
    fun observeSession(): Flow<ChargingSession>
}

data class ChargingSession(
    val isRunning: Boolean = false,
    val snapshot: ChargingSessionSnapshot? = null
) {
    init {
        require(!isRunning || snapshot != null) { "A running session requires a snapshot" }
    }
    val startTime: Long get() = snapshot?.startTime ?: 0L
}

data class ChargingPreset(val id: String, val name: String, val settings: ChargingSettings)

interface PresetRepository {
    fun observeCalibration(): Flow<ChargingCalibration> = flowOf(ChargingCalibration())
    suspend fun resetLearnedEstimates(presetId: String, powerKw: Float) { error("Calibration reset is not supported") }
    fun observeConfiguration(): Flow<PresetConfiguration>
    suspend fun updateSelectedPreset()
    suspend fun saveAsPreset(name: String)
    suspend fun resetPreset()
    fun observePresets(): Flow<List<ChargingPreset>>
    suspend fun savePreset(preset: ChargingPreset)
    suspend fun renamePreset(id: String, name: String)
    suspend fun deletePreset(id: String)
    suspend fun selectPreset(id: String)
    suspend fun reorderPresets(ids: List<String>)
}

data class ChargingHistoryEntry(
    val id: Long,
    val snapshot: ChargingSessionSnapshot,
    val endedAt: Long,
    val endPercent: Float?,
    val actualPercent: Float?,
    val reviewed: Boolean,
    val interrupted: Boolean = false,
    val calibrationEnabled: Boolean = false
)

interface ChargingHistoryRepository {
    fun observeHistory(): Flow<List<ChargingHistoryEntry>>
    suspend fun reviewSession(id: Long, actualPercent: Float?, reviewed: Boolean)
    suspend fun saveReading(id: Long, actualPercent: Float?, calibrationEnabled: Boolean)
    suspend fun acknowledgeSession(id: Long)
    suspend fun deleteSession(id: Long)
}
