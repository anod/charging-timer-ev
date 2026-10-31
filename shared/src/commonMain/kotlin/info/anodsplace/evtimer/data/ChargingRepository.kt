package info.anodsplace.evtimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChargingRepository {
    suspend fun saveSettings(settings: ChargingSettings)
    fun observeSettings(): Flow<ChargingSettings>

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

class ChargingRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : ChargingRepository {

    override suspend fun saveSettings(settings: ChargingSettings) {
        dataStore.edit { prefs ->
            prefs[SessionPreferences.batteryCapacity] = settings.batteryCapacity
            prefs[SessionPreferences.chargingPower] = settings.chargingPower
            prefs[SessionPreferences.startPercent] = settings.startPercent
            prefs[SessionPreferences.maxPercent] = settings.maxPercent
            prefs[SessionPreferences.availablePowers] = settings.availablePowers.joinToString(",")
        }
    }

    override fun observeSettings(): Flow<ChargingSettings> {
        return dataStore.data.map(SessionPreferences::readSettings)
    }

    override suspend fun saveSession(session: ChargingSession) {
        dataStore.edit { prefs ->
            prefs[SessionPreferences.isRunning] = session.isRunning
            prefs[SessionPreferences.startTime] = session.startTime
            SessionPreferences.writeSnapshot(prefs, session.snapshot)
        }
    }

    override fun observeSession(): Flow<ChargingSession> = dataStore.data.map { prefs ->
        ChargingSession(
            isRunning = prefs[SessionPreferences.isRunning] ?: false,
            snapshot = SessionPreferences.readSnapshot(prefs)
        )
    }
}

internal object SessionPreferences {
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
    private val segments = stringPreferencesKey("snapshot_segments")

    fun readSettings(prefs: Preferences): ChargingSettings = ChargingSettings(
        batteryCapacity = prefs[batteryCapacity] ?: ChargingSettings.DEFAULT.batteryCapacity,
        chargingPower = prefs[chargingPower] ?: ChargingSettings.DEFAULT.chargingPower,
        startPercent = prefs[startPercent] ?: ChargingSettings.DEFAULT.startPercent,
        maxPercent = prefs[maxPercent] ?: ChargingSettings.DEFAULT.maxPercent,
        availablePowers = prefs[availablePowers]?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: ChargingSettings.DEFAULT.availablePowers
    )

    fun writeSnapshot(prefs: MutablePreferences, snapshot: ChargingSessionSnapshot?) {
        if (snapshot == null) {
            prefs.remove(capacity)
            prefs.remove(start)
            prefs.remove(target)
            prefs.remove(efficiency)
            prefs.remove(presetId)
            prefs.remove(presetName)
            prefs.remove(segments)
            return
        }
        prefs[capacity] = snapshot.batteryCapacity
        prefs[start] = snapshot.startPercent
        prefs[target] = snapshot.targetPercent
        prefs[efficiency] = snapshot.efficiency
        snapshot.presetId?.let { prefs[presetId] = it } ?: prefs.remove(presetId)
        snapshot.presetName?.let { prefs[presetName] = it } ?: prefs.remove(presetName)
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
            segments = storedSegments.split(",").map {
                val parts = it.split(":")
                require(parts.size == 2) { "Invalid persisted power segment" }
                PowerSegment(parts[0].toLong(), parts[1].toFloat())
            }
        )
    }
}

internal class ChargingSessionMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SessionPreferences.isRunning] == true &&
            SessionPreferences.readSnapshot(currentData) == null

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            SessionPreferences.writeSnapshot(
                this,
                ChargingSessionSnapshot.from(
                    SessionPreferences.readSettings(currentData),
                    requireNotNull(currentData[SessionPreferences.startTime]) {
                        "Running legacy session is missing its start time"
                    }
                )
            )
        }

    override suspend fun cleanUp() = Unit
}