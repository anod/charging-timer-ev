package info.anodsplace.evtimer.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface ChargingRepository {
    suspend fun saveSettings(settings: ChargingSettings)
    fun observeSettings(): Flow<ChargingSettings>
    suspend fun createSnapshot(startedAt: Long): ChargingSessionSnapshot =
        ChargingSessionSnapshot.from(observeSettings().first(), startedAt)

    // Session
    suspend fun saveSession(session: ChargingSession)
    fun observeSession(): Flow<ChargingSession>
}

// Session state representing currently running charging process
// Persisted to survive process death
data class ChargingSession(
    val isRunning: Boolean = false,
    val snapshot: ChargingSessionSnapshot? = null
) {
    init {
        require(!isRunning || snapshot != null) { "A running session requires a snapshot" }
    }

    val startTime: Long get() = snapshot?.startTime ?: 0L
}

internal object SessionPreferences {
    val selectedPreset = stringPreferencesKey("selected_preset")
    val draftPreset = stringPreferencesKey("draft_preset")
    val draftValues = stringPreferencesKey("draft_values")
    val batteryCapacity = floatPreferencesKey("battery_capacity")
    val chargingPower = floatPreferencesKey("charging_power")
    val startPercent = floatPreferencesKey("start_percent")
    val maxPercent = floatPreferencesKey("max_percent")
    val availablePowers = stringPreferencesKey("available_powers")
    val isRunning = booleanPreferencesKey("session_is_running")
    val startTime = longPreferencesKey("session_start_time")
    private val capacity = floatPreferencesKey("snapshot_capacity")
    private val start = floatPreferencesKey("snapshot_start_percent")
    private val target = floatPreferencesKey("snapshot_target_percent")
    private val efficiency = floatPreferencesKey("snapshot_efficiency")
    private val presetId = stringPreferencesKey("snapshot_preset_id")
    private val presetName = stringPreferencesKey("snapshot_preset_name")
    private val presetEdited = booleanPreferencesKey("snapshot_preset_edited")
    private val segments = stringPreferencesKey("snapshot_segments")

    fun clearStructuredData(prefs: MutablePreferences) {
        listOf(batteryCapacity, chargingPower, maxPercent).forEach(prefs::remove)
        prefs.remove(availablePowers)
        prefs.remove(isRunning)
        prefs.remove(startTime)
        writeSnapshot(prefs, null)
    }

    fun readSettings(prefs: Preferences): ChargingSettings = ChargingSettings(
        batteryCapacity = validSetting(prefs, batteryCapacity, ChargingSettings.DEFAULT.batteryCapacity) { it > 0f },
        chargingPower = validSetting(prefs, chargingPower, ChargingSettings.DEFAULT.chargingPower) { it > 0f },
        startPercent = validSetting(prefs, startPercent, ChargingSettings.DEFAULT.startPercent) { it in 0f..100f },
        maxPercent = validSetting(prefs, maxPercent, ChargingSettings.DEFAULT.maxPercent) { it in 0f..100f },
        availablePowers = prefs[availablePowers]?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.filter { it.isFinite() && it > 0f }
            ?.takeIf { it.isNotEmpty() } ?: ChargingSettings.DEFAULT.availablePowers
    )

    private fun validSetting(
        prefs: Preferences,
        key: Preferences.Key<Float>,
        default: Float,
        isValid: (Float) -> Boolean
    ): Float {
        val value = prefs[key] ?: return default
        if (value.isFinite() && isValid(value)) return value
        println("Charging settings: invalid ${key.name}; using default")
        return default
    }

    fun writeSnapshot(prefs: MutablePreferences, snapshot: ChargingSessionSnapshot?) {
        if (snapshot == null) {
            prefs.remove(capacity)
            prefs.remove(start)
            prefs.remove(target)
            prefs.remove(efficiency)
            prefs.remove(presetId)
            prefs.remove(presetName)
            prefs.remove(presetEdited)
            prefs.remove(segments)
            return
        }
        prefs[capacity] = snapshot.batteryCapacity
        prefs[start] = snapshot.startPercent
        prefs[target] = snapshot.targetPercent
        prefs[efficiency] = snapshot.efficiency
        snapshot.presetId?.let { prefs[presetId] = it } ?: prefs.remove(presetId)
        snapshot.presetName?.let { prefs[presetName] = it } ?: prefs.remove(presetName)
        prefs[presetEdited] = snapshot.presetEdited
        prefs[segments] = snapshot.segments.joinToString(",") { "${it.startedAt}:${it.powerKw}" }
    }

    fun readSnapshot(prefs: Preferences): ChargingSessionSnapshot? {
        val storedSegments = prefs[segments] ?: return null
        return ChargingSessionSnapshot(
            batteryCapacity = requireNotNull(prefs[capacity]),
            startPercent = requireNotNull(prefs[start]),
            targetPercent = requireNotNull(prefs[target]),
            efficiency = requireNotNull(prefs[efficiency]),
            presetId = prefs[presetId],
            presetName = prefs[presetName],
            presetEdited = prefs[presetEdited] ?: false,
            segments = storedSegments.split(",").map {
                val parts = it.split(":")
                require(parts.size == 2) { "Invalid persisted power segment" }
                PowerSegment(parts[0].toLong(), parts[1].toFloat())
            }
        )
    }
}

internal class ChargingSessionMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = try {
        val snapshot = SessionPreferences.readSnapshot(currentData)
        currentData[SessionPreferences.isRunning] == true && snapshot == null
    } catch (_: IllegalArgumentException) {
        true
    }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            try {
                val existing = SessionPreferences.readSnapshot(currentData)
                val snapshot = existing ?: ChargingSessionSnapshot.from(
                    SessionPreferences.readSettings(currentData),
                    requireNotNull(currentData[SessionPreferences.startTime])
                )
                SessionPreferences.writeSnapshot(this, snapshot)
            } catch (_: IllegalArgumentException) {
                println("Charging session: invalid persisted snapshot or legacy start time; clearing session")
                this[SessionPreferences.isRunning] = false
                this[SessionPreferences.startTime] = 0L
                SessionPreferences.writeSnapshot(this, null)
            }
        }

    override suspend fun cleanUp() = Unit
}