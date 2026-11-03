package info.anodsplace.evtimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.Path.Companion.toPath

fun dataStoreFactory(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = chargingPreferencesCorruptionHandler,
        migrations = listOf(ChargingSessionMigration()),
        produceFile = { producePath().toPath() }
    )

internal val chargingPreferencesCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
    println("Charging storage: corrupt preferences; restoring simple preference defaults")
    emptyPreferences()
}

internal const val dataStoreFileName = ".charging_settings.preferences_pb"