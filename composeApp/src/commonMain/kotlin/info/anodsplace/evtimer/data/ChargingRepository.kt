package info.anodsplace.evtimer.data

import androidx.datastore.core.DataStore
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
    val startTime: Long = 0L
)

class ChargingRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : ChargingRepository {

    companion object {
        private val KEY_BATTERY_CAPACITY = floatPreferencesKey("battery_capacity")
        private val KEY_CHARGING_POWER = floatPreferencesKey("charging_power")
        private val KEY_START_PERCENT = floatPreferencesKey("start_percent")
        private val KEY_MAX_PERCENT = floatPreferencesKey("max_percent")
        private val KEY_AVAILABLE_POWERS = stringPreferencesKey("available_powers")
        private val KEY_SESSION_IS_RUNNING = booleanPreferencesKey("session_is_running")
        private val KEY_SESSION_START_TIME = longPreferencesKey("session_start_time")
    }

    override suspend fun saveSettings(settings: ChargingSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_BATTERY_CAPACITY] = settings.batteryCapacity
            prefs[KEY_CHARGING_POWER] = settings.chargingPower
            prefs[KEY_START_PERCENT] = settings.startPercent
            prefs[KEY_MAX_PERCENT] = settings.maxPercent
            prefs[KEY_AVAILABLE_POWERS] = settings.availablePowers.joinToString(",")
        }
    }

    override fun observeSettings(): Flow<ChargingSettings> {
        return dataStore.data.map { prefs ->
            ChargingSettings(
                batteryCapacity = prefs[KEY_BATTERY_CAPACITY] ?: 83f,
                chargingPower = prefs[KEY_CHARGING_POWER] ?: 11f,
                startPercent = prefs[KEY_START_PERCENT] ?: 60f,
                maxPercent = prefs[KEY_MAX_PERCENT] ?: 100f,
                availablePowers = prefs[KEY_AVAILABLE_POWERS]
                    ?.split(",")
                    ?.mapNotNull { it.toFloatOrNull() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(3.6f, 7f, 11f, 22f)
            )
        }
    }

    override suspend fun saveSession(session: ChargingSession) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_IS_RUNNING] = session.isRunning
            prefs[KEY_SESSION_START_TIME] = session.startTime
        }
    }

    override fun observeSession(): Flow<ChargingSession> = dataStore.data.map { prefs ->
        ChargingSession(
            isRunning = prefs[KEY_SESSION_IS_RUNNING] ?: false,
            startTime = prefs[KEY_SESSION_START_TIME] ?: 0L
        )
    }
}