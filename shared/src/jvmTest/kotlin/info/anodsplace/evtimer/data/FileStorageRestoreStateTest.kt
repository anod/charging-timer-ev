package info.anodsplace.evtimer.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileStorageRestoreStateTest {
    private fun fixture(block: (File, File) -> Unit) {
        val directory = Files.createTempDirectory("evtimer-installation").toFile()
        val database = File(directory, "charging.db")
        try { block(database, directory) } finally {
            directory.listFiles()!!.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }

    @Test
    fun freshAndLegacyInstallSeedMarkerBeforeDatabaseCreation() = fixture { database, directory ->
        val state = FileStorageRestoreState(database, directory)
        assertFalse(state.requiresInterruption)
        assertTrue(File(directory, "charging_storage_installation").isFile)
        assertTrue(database.createNewFile())
        assertFalse(FileStorageRestoreState(database, directory).requiresInterruption)
    }

    @Test
    fun unmarkedDatabaseRequiresInterruptionUntilMarkReady() = fixture { database, directory ->
        assertTrue(database.createNewFile())
        val state = FileStorageRestoreState(database, directory)
        assertTrue(state.requiresInterruption)
        state.markReady()
        assertFalse(state.requiresInterruption)
        assertFalse(FileStorageRestoreState(database, directory).requiresInterruption)
    }

    @Test
    fun inPlaceRestoreOverridesInstallationMarker() = fixture { database, directory ->
        val state = FileStorageRestoreState(database, directory)
        assertTrue(database.createNewFile())
        FileStorageRestoreState.recordRestore(directory)
        assertTrue(state.requiresInterruption)
        state.markReady()
        assertFalse(state.requiresInterruption)
        assertFalse(File(directory, "charging_restore_pending").exists())
    }

    @Test
    fun preferencesOnlyRestoreMarkerSurvivesFreshDatabaseCreation() = fixture { database, directory ->
        FileStorageRestoreState.recordRestore(directory)
        val state = FileStorageRestoreState(database, directory)
        assertTrue(state.requiresInterruption)
        assertTrue(database.createNewFile())
        assertTrue(state.requiresInterruption)
        state.markReady()
        assertFalse(state.requiresInterruption)
    }
}
