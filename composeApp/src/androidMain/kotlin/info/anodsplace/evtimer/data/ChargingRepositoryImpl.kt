package info.anodsplace.evtimer.data

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

actual class ChargingRepositoryImpl : ChargingRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Attempt to retrieve Application context reflectively (since expect class has empty ctor)
    private val appContext: Context by lazy {
        val app = Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Application
        app.applicationContext
    }

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { appContext.preferencesDataStoreFile("charging_settings") }
    )

    private val KEY_BATTERY_CAPACITY = floatPreferencesKey("battery_capacity")
    private val KEY_CHARGING_POWER = floatPreferencesKey("charging_power")
    private val KEY_START_PERCENT = floatPreferencesKey("start_percent")
    private val KEY_MAX_PERCENT = floatPreferencesKey("max_percent")
    private val KEY_AVAILABLE_POWERS = stringPreferencesKey("available_powers")

    override fun saveSettings(settings: ChargingSettings) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_BATTERY_CAPACITY] = settings.batteryCapacity
                prefs[KEY_CHARGING_POWER] = settings.chargingPower
                prefs[KEY_START_PERCENT] = settings.startPercent
                prefs[KEY_MAX_PERCENT] = settings.maxPercent
                prefs[KEY_AVAILABLE_POWERS] = settings.availablePowers.joinToString(",")
            }
        }
    }

    override fun loadSettings(onLoaded: (ChargingSettings) -> Unit) {
        scope.launch {
            val prefs = runCatching { dataStore.data.first() }.getOrNull()
            val settings = if (prefs != null) {
                ChargingSettings(
                    batteryCapacity = prefs[KEY_BATTERY_CAPACITY] ?: 60f,
                    chargingPower = prefs[KEY_CHARGING_POWER] ?: 7f,
                    startPercent = prefs[KEY_START_PERCENT] ?: 20f,
                    maxPercent = prefs[KEY_MAX_PERCENT] ?: 80f,
                    availablePowers = prefs[KEY_AVAILABLE_POWERS]
                        ?.split(",")
                        ?.mapNotNull { it.toFloatOrNull() }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(3.6f, 7f, 11f, 22f)
                )
            } else {
                ChargingSettings()
            }
            onLoaded(settings)
        }
    }
}

