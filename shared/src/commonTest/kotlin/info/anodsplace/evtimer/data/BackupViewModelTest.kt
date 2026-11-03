package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val backup = ManualBackup(
        BackupFormat.FORMAT, 1, "2026-11-01T09:30:00Z", "test",
        BackupPreferences("custom", 20f),
        listOf(BackupPreset("custom", "Default", 60.0, 7.0, 80.0, listOf(7.0))), emptyList()
    )

    @Test
    fun parsedBackupStaysPendingUntilExplicitConfirmationAndCanBeCancelled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = BackupTestRepository()
        val model = SettingsViewModel(repository, repository, repository)
        try {
            runCurrent()
            val retainedState = model.backup
            model.readBackup(BackupSource { BackupFormat.encode(backup) })
            model.backup.first { !it.busy }
            assertEquals(backup, model.backup.value.pending)
            assertSame(retainedState, model.backup)
            assertNull(repository.imported)
            model.dismissBackup()
            assertNull(model.backup.value.pending)
            assertNull(repository.imported)
            model.readBackup(BackupSource { BackupFormat.encode(backup) })
            model.backup.first { !it.busy }
            model.confirmRestore()
            model.backup.first { !it.busy }
            assertEquals(backup, repository.imported)
            assertEquals(BackupMessage.RESTORED, model.backup.value.message)
            assertNull(model.backup.value.pending)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun corruptAndInaccessibleDocumentsShowErrorsWithoutImporting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = BackupTestRepository()
        val model = SettingsViewModel(repository, repository, repository)
        try {
            model.readBackup(BackupSource { "{".encodeToByteArray() })
            assertEquals(BackupError.INVALID_JSON, model.backup.first { !it.busy }.error)
            assertNull(repository.imported)
            model.readBackup(BackupSource { throw IllegalStateException("provider unavailable") })
            assertEquals(BackupError.IO, model.backup.first { !it.busy }.error)
            assertNull(repository.imported)
            assertNull(model.backup.value.pending)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sessionStartingWhileConfirmationIsOpenStillBlocksRestore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = BackupTestRepository()
        val model = SettingsViewModel(repository, repository, repository)
        try {
            model.readBackup(BackupSource { BackupFormat.encode(backup) })
            model.backup.first { !it.busy }
            repository.session.value = ChargingSession(true, ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 1))
            model.confirmRestore()
            assertEquals(BackupError.SESSION_RUNNING, model.backup.first { !it.busy }.error)
            assertNull(repository.imported)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exportWritesOnlyAfterDestinationSelectionAndReportsWriteFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = BackupTestRepository()
        val model = SettingsViewModel(repository, repository, repository)
        try {
            runCurrent()
            assertFalse(repository.exported)
            model.exportBackup(BackupDestination { bytes ->
                assertEquals(backup, BackupFormat.decode(bytes))
            })
            assertEquals(BackupMessage.EXPORTED, model.backup.first { !it.busy }.message)
            assertTrue(repository.exported)
            model.exportBackup(BackupDestination { throw IllegalStateException("provider rejected write") })
            assertEquals(BackupError.IO, model.backup.first { !it.busy }.error)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private inner class BackupTestRepository : ChargingRepository, PresetRepository, ManualBackupRepository {
        val session = MutableStateFlow(ChargingSession())
        var imported: ManualBackup? = null
        var exported = false
        override suspend fun exportBackup(appVersion: String): ManualBackup { exported = true; return backup }
        override suspend fun restoreBackup(backup: ManualBackup) {
            if (session.value.isRunning) throw BackupException(BackupError.SESSION_RUNNING, "Stop first")
            imported = backup
        }
        override fun observeSession() = session
        override fun observeSettings() = MutableStateFlow(ChargingSettings.DEFAULT)
        override fun observeNotifyAt80() = MutableStateFlow(true)
        override fun observePresets() = MutableStateFlow(emptyList<ChargingPreset>())
        override fun observeConfiguration() = MutableStateFlow(
            PresetConfiguration(listOf(ChargingPreset("custom", "Default", ChargingSettings.DEFAULT)), "custom", ChargingSettings.DEFAULT)
        )
        override suspend fun saveSession(session: ChargingSession) { this.session.value = session }
        override suspend fun saveSettings(settings: ChargingSettings) = Unit
        override suspend fun setNotifyAt80(enabled: Boolean) = Unit
        override suspend fun updateSelectedPreset() = Unit
        override suspend fun saveAsPreset(name: String) = Unit
        override suspend fun resetPreset() = Unit
        override suspend fun savePreset(preset: ChargingPreset) = Unit
        override suspend fun renamePreset(id: String, name: String) = Unit
        override suspend fun deletePreset(id: String) = Unit
        override suspend fun selectPreset(id: String) = Unit
        override suspend fun reorderPresets(ids: List<String>) = Unit
    }
}
