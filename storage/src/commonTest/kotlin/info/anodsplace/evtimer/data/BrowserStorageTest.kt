package info.anodsplace.evtimer.data

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class BrowserStorageTest {
    private val now = Instant.parse("2026-11-03T10:00:00Z")
    private val clock = object : Clock { override fun now() = now }

    private class Store : BrowserDocumentStore {
        var record: StoredBrowserDocument? = null
        var failRead = false
        var failWrite = false
        var conflict = false
        private var revision = 0
        override suspend fun read(): StoredBrowserDocument? {
            if (failRead) throw BackupException(BackupError.IO, "Read denied")
            return record
        }
        override suspend fun compareAndSet(expectedRevision: String?, payload: String): StoredBrowserDocument {
            if (failWrite) throw BackupException(BackupError.IO, "Quota exceeded")
            if (conflict || record?.revision != expectedRevision) {
                throw BackupException(BackupError.IO, "Concurrent write")
            }
            return StoredBrowserDocument((++revision).toString(), payload).also { record = it }
        }
    }

    private fun session(id: Long = 9007199254740993L, endedAt: Long? = 300) = BackupSession(
        id, 83.0, 20.0, 80.0, 0.9,
        listOf(BackupSegment(100, 11.0, 0.85), BackupSegment(200, 7.0, 0.8)),
        presetId = "custom", presetName = "Car", endedAt = endedAt,
        endPercent = 80.0, actualPercent = 78.5, calibrationEnabled = true, presetEdited = true
    )

    @Test
    fun firstUseMustPersistBeforeReturningAndReopenPreservesEveryField() = runTest {
        val store = Store()
        val repository = BrowserStorage(store, clock)
        val initial = repository.load()
        assertNotNull(store.record)
        val preset = initial.backup.presets.single().copy(name = "Car \uD83D\uDE97", position = 12)
        val draft = BackupDraft("custom", 85f, 7f, 90f, listOf(7f, 11f))
        val edited = repository.update {
            it.copy(backup = it.backup.copy(
                presets = listOf(preset),
                preferences = BackupPreferences("custom", 21.5f, false, draft),
                sessions = listOf(session())
            ))
        }
        val reopened = BrowserStorage(store, clock)
        assertEquals(edited, reopened.load())
        val backup = reopened.exportBackup("test-version")
        assertEquals(BackupFormat.VERSION, backup.formatVersion)
        assertEquals("test-version", backup.appVersion)
        assertEquals(backup, BackupFormat.decode(BackupFormat.encode(backup)))
        assertEquals(9007199254740993L, backup.sessions.single().id)
        assertEquals(0.8, backup.sessions.single().segments.last().efficiency)
        val destination = BrowserStorage(Store(), clock)
        destination.restoreBackup(backup)
        assertEquals(backup.copy(sessions = backup.sessions.map { it.copy(reviewed = true) }),
            destination.exportBackup("test-version"))
    }

    @Test
    fun missingStorageOrQuotaFailureCannotBecomeMemoryOnlySuccess() = runTest {
        val store = Store()
        store.failRead = true
        assertFailsWith<BackupException> { BrowserStorage(store, clock).load() }
        assertNull(store.record)
        store.failRead = false
        store.failWrite = true
        assertFailsWith<BackupException> { BrowserStorage(store, clock).load() }
        assertNull(store.record)
        store.failWrite = false
        val repository = BrowserStorage(store, clock)
        val before = repository.load()
        val record = store.record
        store.failWrite = true
        assertFailsWith<BackupException> {
            repository.update { it.copy(backup = it.backup.copy(preferences = it.backup.preferences.copy(notifyAt80 = false))) }
        }
        assertEquals(record, store.record)
        assertEquals(before, repository.load())
    }

    @Test
    fun corruptOrFutureStorageIsNeverReplacedWithDefaults() = runTest {
        val store = Store()
        BrowserStorage(store, clock).load()
        val good = assertNotNull(store.record)
        for (bad in listOf("{", good.payload.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))) {
            store.record = good.copy(payload = bad)
            assertFailsWith<BackupException> { BrowserStorage(store, clock).load() }
            assertEquals(bad, store.record?.payload)
        }
    }

    @Test
    fun invalidImportsFailedCommitsAndConcurrentTabsLeaveOriginalDataIntact() = runTest {
        val store = Store()
        val repository = BrowserStorage(store, clock)
        val before = repository.load()
        val record = store.record
        assertFailsWith<BackupException> { repository.restoreBackup(before.backup.copy(presets = emptyList())) }
        assertEquals(record, store.record)
        store.conflict = true
        assertFailsWith<BackupException> { repository.restoreBackup(before.backup) }
        assertEquals(record, store.record)
        store.conflict = false
        store.failWrite = true
        assertFailsWith<BackupException> { repository.restoreBackup(before.backup) }
        assertEquals(record, store.record)
    }

    @Test
    fun readsLatestCommittedDocumentRatherThanStaleRepositoryCache() = runTest {
        val store = Store()
        val first = BrowserStorage(store, clock)
        val second = BrowserStorage(store, clock)
        first.load()
        second.update { it.copy(backup = it.backup.copy(preferences = it.backup.preferences.copy(notifyAt80 = false))) }
        assertFalse(first.load().backup.preferences.notifyAt80)
    }

    @Test
    fun restoreNormalizesHistorySelectionAndOrderingLikeNativeAndNeverStartsATimer() = runTest {
        val repository = BrowserStorage(Store(), clock)
        val initial = repository.load().backup
        val backup = initial.copy(
            presets = listOf(initial.presets.single().copy(id = "later", position = 9),
                initial.presets.single().copy(id = "first", position = 2)),
            preferences = initial.preferences.copy(selectedPresetId = "deleted"),
            sessions = listOf(session(id = 1), session(id = 2, endedAt = null))
        )
        repository.restoreBackup(backup)
        val result = repository.load()
        assertNull(result.activeSession)
        assertEquals(listOf("first", "later"), result.backup.presets.map { it.id })
        assertEquals("first", result.backup.preferences.selectedPresetId)
        val unfinished = result.backup.sessions.last()
        assertTrue(unfinished.interrupted)
        assertTrue(unfinished.reviewed)
        assertTrue(unfinished.calibrationEnabled)
        assertEquals(now.toEpochMilliseconds(), unfinished.endedAt)
        assertNull(unfinished.endPercent)
        assertFalse(result.backup.sessions.first().reviewed)
    }

    @Test
    fun activeSessionSurvivesReopenIsExcludedFromExportAndBlocksImport() = runTest {
        val store = Store()
        val repository = BrowserStorage(store, clock)
        repository.update { it.copy(activeSession = session(endedAt = null)) }
        val reopened = BrowserStorage(store, clock)
        assertEquals(session(endedAt = null), reopened.load().activeSession)
        val backup = reopened.exportBackup("test")
        assertTrue(backup.sessions.isEmpty())
        val before = store.record
        assertEquals(BackupError.SESSION_RUNNING,
            assertFailsWith<BackupException> { reopened.restoreBackup(backup) }.error)
        assertEquals(before, store.record)
    }

    @Test
    fun legacyImportUpgradesWithoutGrantingLearningConsent() = runTest {
        val repository = BrowserStorage(Store(), clock)
        val base = repository.load().backup
        repository.restoreBackup(base.copy(formatVersion = 1,
            sessions = listOf(session().copy(calibrationEnabled = false,
                segments = listOf(BackupSegment(100, 11.0))))))
        assertEquals(2, repository.load().backup.formatVersion)
        assertFalse(repository.load().backup.sessions.single().calibrationEnabled)
    }
}
