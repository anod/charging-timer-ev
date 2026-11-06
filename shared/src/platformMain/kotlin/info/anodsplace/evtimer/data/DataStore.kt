package info.anodsplace.evtimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

fun dataStoreFactory(
    producePath: () -> String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        scope = scope,
        corruptionHandler = chargingPreferencesCorruptionHandler,
        migrations = listOf(ChargingSessionMigration()),
        produceFile = { producePath().toPath() }
    )

internal val chargingPreferencesCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
    println("Charging storage: corrupt preferences; restoring simple preference defaults")
    emptyPreferences()
}

internal const val dataStoreFileName = ".charging_settings.preferences_pb"