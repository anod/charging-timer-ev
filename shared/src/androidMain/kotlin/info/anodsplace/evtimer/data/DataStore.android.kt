package info.anodsplace.evtimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStore(context: Context): DataStore<Preferences> = dataStoreFactory(
    producePath = { context.filesDir.resolve(dataStoreFileName).absolutePath }
)