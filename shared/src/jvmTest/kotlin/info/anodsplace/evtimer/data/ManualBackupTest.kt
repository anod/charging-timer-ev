package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import info.anodsplace.evtimer.ui.Destination
import info.anodsplace.evtimer.ui.NavigationViewModel
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.QueryResult
import info.anodsplace.evtimer.db.ChargingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ManualBackupTest {
    private val sample = ManualBackup(
        BackupFormat.FORMAT, BackupFormat.VERSION, "2026-11-01T09:30:00Z", "test",
        BackupPreferences("home", 25f, false, BackupDraft("home", 61f, 11f, 85f, listOf(7f, 11f))),
        listOf(
            BackupPreset("custom", "Default", 70.0, 7.0, 80.0, listOf(7.0, 11.0), 1),
            BackupPreset("home", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", 60.0, 11.0, 90.0, listOf(11.0, 22.0), 0)
        ),
        listOf(
            BackupSession(12, 60.0, 20.0, 80.0, 0.9,
                listOf(BackupSegment(100, 7.0), BackupSegment(200, 11.0)),
                "deleted-preset", "Old preset", 300, 75.0, 76.0, reviewed = true, presetEdited = true),
            BackupSession(11, 70.0, 25.0, 80.0, 1.0, listOf(BackupSegment(50, 7.0)),
                "custom", "Default", 90, interrupted = true)
        )
    )

    @Test
    fun utf8RoundTripPreservesAllValuesAndIgnoresUnknownKeys() {
        assertEquals(sample, BackupFormat.decode(BackupFormat.encode(sample)))
        val extra = BackupFormat.encode(sample).decodeToString().replaceFirst("{", """{"futureField": 123,""")
        assertEquals(sample, BackupFormat.decode(extra.encodeToByteArray()))
    }

    @Test
    fun versionTwoConsentAndFactorsRoundTripResetAndRestoreWithoutTeachingInterruptedRows() = runTest {
        fixture { f ->
            val reading = BackupSession(
                100L, 60.0, 20.0, 90.0, 1.0, listOf(BackupSegment(100L, 6.0, 0.8)),
                "home", "Home", 3_600_100L, 30.0, 28.0, calibrationEnabled = true
            )
            val backup = sample.copy(
                preferences = BackupPreferences("home", 20f),
                sessions = listOf(reading, reading.copy(id = 99L, interrupted = true, actualPercent = 100.0))
            )
            f.repo.restoreBackup(BackupFormat.decode(BackupFormat.encode(backup)))
            assertEquals(0.94f, f.repo.observeCalibration().first().factor("home", 6f), 1e-6f)
            assertEquals(1, f.repo.observeCalibration().first().estimate("home", 6f).eligibleCount)
            val exported = f.repo.exportBackup("test")
            assertEquals(BackupFormat.VERSION, exported.formatVersion)
            assertTrue(exported.sessions.all { it.calibrationEnabled })
            assertTrue(exported.sessions.first().reviewed)
            assertEquals(1.0, exported.sessions.first().efficiency)
            assertEquals(0.8, exported.sessions.first().segments.single().efficiency)
            fixture { target ->
                target.repo.restoreBackup(exported)
                assertEquals(0.94f, target.repo.observeCalibration().first().factor("home", 6f), 1e-6f)
                assertEquals(1, target.repo.observeCalibration().first().estimate("home", 6f).eligibleCount)
            }
            f.repo.resetLearnedEstimates("home", 6f)
            val reset = f.repo.exportBackup("test")
            assertTrue(reset.sessions.none { it.calibrationEnabled })
            assertEquals(exported.sessions.map { it.actualPercent }, reset.sessions.map { it.actualPercent })
            fixture { target ->
                target.repo.restoreBackup(BackupFormat.decode(BackupFormat.encode(reset)))
                assertEquals(1f, target.repo.observeCalibration().first().factor("home", 6f))
                target.repo.saveReading(reading.id, 28f, true)
                assertEquals(0.94f, target.repo.observeCalibration().first().factor("home", 6f), 1e-6f)
            }
        }
    }

    @Test
    fun exportAndReplaceEmptyAndPopulatedDatabasesIncludingCustomDraftAndEmoji() = runTest {
        fixture { source ->
            source.repo.restoreBackup(sample)
            val exported = source.repo.exportBackup("test")
            assertEquals(sample.preferences, exported.preferences)
            assertEquals(sample.presets.sortedBy { it.position }, exported.presets)
            assertEquals(sample.sessions, exported.sessions)
            for (populated in listOf(false, true)) fixture { target ->
                if (populated) {
                    target.repo.observeSettings().first()
                    target.repo.savePreset(ChargingPreset("unrelated", "Unrelated", ChargingSettings.DEFAULT))
                    target.repo.saveSession(ChargingSession(true,
                        ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 999)))
                    target.repo.saveSession(ChargingSession())
                }

                target.repo.restoreBackup(BackupFormat.decode(BackupFormat.encode(exported)))
                assertEquals(exported, target.repo.exportBackup("test"))
                assertEquals(1, target.database.storageQueries.allPresets().executeAsList().count { it.id == "custom" })
                assertEquals(70f, target.repo.observePresets().first().first { it.id == "custom" }.settings.batteryCapacity)
                assertEquals(ChargingSession(), target.repo.observeSession().first())
                assertFalse(target.repo.observeSession().first().isRunning)
                assertTrue(target.repo.observeHistory().first().first().reviewed)
                assertEquals(sample.preferences, target.repository().exportBackup("test").preferences)
            }
        }
    }

    @Test
    fun runningSessionIsNotExportedAndBlocksRestoreWithoutChangingAnything() = runTest {
        fixture { f ->
            f.repo.restoreBackup(sample)
            val snapshot = ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 1000)
            f.repo.saveSession(ChargingSession(true, snapshot))
            val before = f.repo.exportBackup("test")
            assertEquals(2, before.sessions.size)
            val error = assertFailsWith<BackupException> { f.repo.restoreBackup(sample) }
            assertEquals(BackupError.SESSION_RUNNING, error.error)
            assertEquals(before, f.repo.exportBackup("test"))
            assertEquals(ChargingSession(true, snapshot), f.repo.observeSession().first())
        }
    }

    @Test
    fun sessionsWithoutEndBecomeInterruptedAndLatestCannotTriggerCompletion() = runTest {
        fixture { f ->
            val unfinished = sample.sessions.first().copy(endedAt = null, reviewed = false, endPercent = 80.0)
            f.repo.restoreBackup(sample.copy(sessions = listOf(unfinished)))
            val entry = f.repo.observeHistory().first().single()
            assertTrue(entry.interrupted)
            assertTrue(entry.reviewed)
            assertNull(entry.endPercent)
            assertEquals(1_000_000L, entry.endedAt)
            assertEquals(ChargingSession(), f.repository().observeSession().first())
            assertNull(f.database.storageQueries.latestSession().executeAsOneOrNull())
            assertNull(f.database.storageQueries.activeSession().executeAsOneOrNull())
        }

    }

    @Test
    fun unknownSelectionFallsBackToFirstPresetAndOldDraftIsCleared() = runTest {
        fixture { f ->
            f.repo.restoreBackup(sample)
            f.repo.restoreBackup(sample.copy(preferences = BackupPreferences("missing", 30f)))
            val config = f.repo.observeConfiguration().first()
            assertEquals("home", config.selectedId)
            assertEquals(30f, config.settings.startPercent)
            assertFalse(config.isEdited)
            assertNull(f.store.data.first()[SessionPreferences.draftPreset])
            assertNull(f.store.data.first()[SessionPreferences.draftValues])
        }
    }

    @Test
    fun rejectsMalformedTruncatedWrongTypesNewerAndOversizeWithoutChangingData() = runTest {
        fixture { f ->
            f.repo.restoreBackup(sample)
            val bytes = BackupFormat.encode(sample)
            val cases = listOf(
                "not json".encodeToByteArray() to BackupError.INVALID_JSON,
                bytes.copyOf(bytes.size / 2) to BackupError.INVALID_JSON,
                bytes.decodeToString().replace("\"formatVersion\": 2", "\"formatVersion\": \"one\"").encodeToByteArray() to BackupError.INVALID_JSON,
                bytes.decodeToString().replace("\"batteryCapacity\": 70.0", "\"batteryCapacity\": \"70.0\"").encodeToByteArray() to BackupError.INVALID_JSON,
                bytes.decodeToString().replace("\"formatVersion\": 2", "\"formatVersion\": 3").encodeToByteArray() to BackupError.NEWER_VERSION,
                "{}".encodeToByteArray() to BackupError.INVALID_JSON,
                byteArrayOf(0xc3.toByte(), 0x28) to BackupError.INVALID_JSON,
                ByteArray(BackupFormat.MAX_BYTES + 1) to BackupError.TOO_LARGE
            )
            val before = f.repo.exportBackup("test")
            val prefs = f.store.data.first()
            for ((input, expected) in cases) {
                val error = assertFailsWith<BackupException> { f.repo.restoreBackup(BackupFormat.decode(input)) }
                assertEquals(expected, error.error)
                assertTrue(error.message!!.isNotBlank())
                assertEquals(before, f.repo.exportBackup("test"))
                assertEquals(prefs, f.store.data.first())
            }
        }
    }

    @Test
    fun rejectsInvalidRangesNamesDuplicateIdsAndSegmentsBeforeAnyWrite() = runTest {
        fixture { f ->
            f.repo.restoreBackup(sample)
            val preset = sample.presets.first()
            val session = sample.sessions.first()
            val invalid = listOf(
                sample.copy(format = "another-format"),
                sample.copy(formatVersion = 0),
                sample.copy(exportedAt = "yesterday"),
                sample.copy(presets = emptyList()),
                sample.copy(presets = sample.presets + preset),
                sample.copy(presets = listOf(preset.copy(name = " "))),
                sample.copy(presets = listOf(preset.copy(batteryCapacity = 0.0))),
                sample.copy(presets = listOf(preset.copy(chargingPower = Double.NaN))),
                sample.copy(presets = listOf(preset.copy(chargingPower = Double.MAX_VALUE))),
                sample.copy(presets = listOf(preset.copy(targetPercent = 101.0))),
                sample.copy(presets = listOf(preset.copy(powerOptions = listOf(-1.0)))),
                sample.copy(preferences = sample.preferences.copy(startPercent = -1f)),
                sample.copy(preferences = sample.preferences.copy(draft = sample.preferences.draft!!.copy(presetId = "orphan"))),
                sample.copy(sessions = listOf(session, session)),
                sample.copy(sessions = listOf(session.copy(efficiency = 0.0))),
                sample.copy(sessions = listOf(session.copy(efficiency = 1.1))),
                sample.copy(sessions = listOf(session.copy(startPercent = -1.0))),
                sample.copy(sessions = listOf(session.copy(actualPercent = 101.0))),
                sample.copy(sessions = listOf(session.copy(endPercent = -1.0))),
                sample.copy(sessions = listOf(session.copy(endedAt = 1))),
                sample.copy(sessions = listOf(session.copy(segments = emptyList()))),
                sample.copy(sessions = listOf(session.copy(segments = listOf(BackupSegment(1, 0.0))))),
                sample.copy(sessions = listOf(session.copy(segments = listOf(BackupSegment(1, 7.0), BackupSegment(1, 11.0)))))
            )
            val before = f.repo.exportBackup("test")
            val prefs = f.store.data.first()
            invalid.forEach { backup ->
                val error = assertFailsWith<BackupException> { f.repo.restoreBackup(backup) }
                assertEquals(BackupError.INVALID_DATA, error.error)
                assertEquals(before, f.repo.exportBackup("test"))
                assertEquals(prefs, f.store.data.first())
            }
        }
    }

    @Test
    fun optionsAndSegmentsCannotBeOrphanedBecauseTheyRequireTheirParent() {
        val json = BackupFormat.encode(sample).decodeToString()
        // A nested parent without its required identity cannot introduce child rows.
        val orphanOptions = json.replace("\"id\": \"custom\",", "")
        val orphanSegments = json.replace("\"id\": 12,", "")
        for (invalid in listOf(orphanOptions, orphanSegments)) {
            assertEquals(BackupError.INVALID_JSON,
                assertFailsWith<BackupException> { BackupFormat.decode(invalid.encodeToByteArray()) }.error)
        }
    }

    @Test
    fun failureOnLastSegmentRollsBackDeletesInsertsAndPreferences() = runTest {
        fixture { f ->
            f.repo.restoreBackup(sample)
            f.repo.saveSession(ChargingSession(false, ChargingSessionSnapshot.from(ChargingSettings.DEFAULT, 1000)))
            val before = f.repo.exportBackup("test")
            val current = f.repo.observeSession().first()
            val prefs = f.store.data.first()
            f.driver.execute(null, """
                CREATE TRIGGER reject_last_backup_row BEFORE INSERT ON power_segment
                WHEN NEW.power_kw = 999
                BEGIN SELECT RAISE(ABORT, 'injected last-row failure'); END
            """.trimIndent(), 0)
            val last = sample.sessions.last().copy(segments = listOf(BackupSegment(50, 999.0)))
            assertFailsWith<Exception> {
                f.repo.restoreBackup(sample.copy(sessions = sample.sessions.dropLast(1) + last))
            }

            assertEquals(before, f.repo.exportBackup("test"))
            assertEquals(prefs, f.store.data.first())
            assertEquals(current, f.repo.observeSession().first())
        }
    }

    @Test
    fun legacyNamesRemainExportableAndRoundTripWithoutRenamingOrTruncation() = runTest {
        fixture { source ->
            source.repo.observeSettings().first()
            val names = listOf("a".repeat(33), "Default", "Home", "hOME", "  Legacy  ")
            names.forEachIndexed { index, name ->
                val id = "legacy-$index"
                source.database.storageQueries.putPreset(id, name, 60.0, 7.0, 80.0)
                source.database.storageQueries.putOption(id, 0, 7.0)
            }
            val exported = source.repo.exportBackup("test")
            for (populated in listOf(false, true)) fixture { target ->
                if (populated) target.repo.restoreBackup(sample)
                target.repo.restoreBackup(BackupFormat.decode(BackupFormat.encode(exported)))
                assertEquals(exported, target.repo.exportBackup("test"))
                assertEquals(names, target.repo.observePresets().first()
                    .filter { it.id.startsWith("legacy-") }.sortedBy { it.id }.map { it.name })
            }
        }
    }

    @Test
    fun deletedOrRenamedCustomRestoresExactlyIntoEmptyAndPopulatedDatabases() = runTest {
        val renamed = sample.presets.map { if (it.id == "custom") it.copy(name = "Renamed") else it }
        val deleted = listOf(
            BackupPreset("not-custom", "Default", 60.0, 7.0, 80.0, listOf(7.0), 2),
            BackupPreset("first", "First", 70.0, 11.0, 90.0, listOf(11.0, 7.0), -1)
        )
        for (presets in listOf(renamed, deleted)) {
            val input = sample.copy(preferences = BackupPreferences("missing", 20f), presets = presets, sessions = emptyList())
            for (populated in listOf(false, true)) fixture { target ->
                if (populated) {
                    target.repo.restoreBackup(sample)
                    target.repo.savePreset(ChargingPreset("existing", "Existing", ChargingSettings.DEFAULT))
                }
                target.repo.restoreBackup(BackupFormat.decode(BackupFormat.encode(input)))
                val expected = presets.sortedBy { it.position }
                assertEquals(expected, target.repo.exportBackup("test").presets)
                assertEquals(expected.first().id, target.repo.observeConfiguration().first().selectedId)
                val exported = target.repository().exportBackup("test")
                assertEquals(expected, exported.presets)
                assertEquals(expected.first().id, exported.preferences.selectedPresetId)
                fixture { roundTrip ->
                    roundTrip.repo.restoreBackup(BackupFormat.decode(BackupFormat.encode(exported)))
                    assertEquals(exported, roundTrip.repo.exportBackup("test"))
                }
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun restoredUnreviewedHistoryDoesNotReopenEndScreenOnRestart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            fixture { f ->
                f.repo.restoreBackup(sample.copy(sessions = sample.sessions.map { it.copy(reviewed = false) }))
                val restarted = f.repository()
                val scope = CoroutineScope(Dispatchers.Default + Job())
                val navigation = NavigationViewModel(ChargingServiceImpl(restarted, scope, f.clock), restarted)
                try {
                    assertEquals(listOf(Destination.Config), navigation.backStack.first { it.isNotEmpty() })
                    assertNull(navigation.summary.value)
                    assertFalse(restarted.observeHistory().first().last().reviewed)
                } finally {
                    navigation.viewModelScope.cancel()
                    scope.coroutineContext[Job]!!.cancelAndJoin()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun preferencesFailureAfterCommitIsExplicitlyReportedAsPartialRestore() = runTest {
        fixture { f ->
            f.repo.observeSettings().first()
            val before = f.store.data.first()
            f.failPreferenceWrites = true
            val error = assertFailsWith<BackupException> { f.repo.restoreBackup(sample) }
            assertEquals(BackupError.PREFERENCES, error.error)
            assertEquals(sample.sessions.map { it.id }, f.database.storageQueries.backupSessions().executeAsList().map { it.id })
            assertEquals(before, f.store.data.first())
            assertNull(f.database.storageQueries.latestSession().executeAsOneOrNull())
        }
    }

    @Test
    fun everySqlTableAndColumnHasAnExplicitBackupDisposition() {
        val covered = mapOf(
            "preset" to setOf("id", "name", "battery_capacity", "charging_power", "target_percent", "position"),
            // Parent identity is nesting; option position is array order (renumbered on import).
            "power_option" to setOf("preset_id", "position", "power_kw"),
            "charge_session" to setOf("id", "battery_capacity", "start_percent", "target_percent", "efficiency",
                "preset_id", "preset_name", "ended_at", "end_percent", "actual_percent", "reviewed",
                "interrupted", "preset_edited", "calibration_enabled"),
            "power_segment" to setOf("session_id", "started_at", "power_kw", "efficiency")
        )
        val excluded = mapOf(
            "charge_session" to mapOf("is_running" to "Never restore a running timer; export finished history only"),
            "storage_migration" to mapOf(
                "id" to "Device-local migration bookkeeping",
                "current_session_id" to "Cleared on restore to prevent a timer or summary from resuming"
            )
        )
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = ChargingDatabase.Schema).use { driver ->
            val tables = driver.executeQuery(null,
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                { cursor ->
                    val names = mutableSetOf<String>()
                    while (cursor.next().value) names += requireNotNull(cursor.getString(0))
                    app.cash.sqldelight.db.QueryResult.Value(names)
                }, 0).value
            assertEquals(covered.keys + excluded.keys, tables, "New tables need backup coverage or an exclusion reason")
            tables.forEach { table ->
                val columns = driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
                    val names = mutableSetOf<String>()
                    while (cursor.next().value) names += requireNotNull(cursor.getString(1))
                    app.cash.sqldelight.db.QueryResult.Value(names)
                }, 0).value
                assertEquals(covered[table].orEmpty() + excluded[table].orEmpty().keys, columns,
                    "$table: new columns need backup coverage or an exclusion reason")
                assertTrue(excluded[table].orEmpty().values.all { it.isNotBlank() })
            }
        }
    }

    @Test
    fun preferenceKeysCannotBeAddedWithoutBackupCoverageOrExclusion() {
        val source = File("src/commonMain/kotlin/info/anodsplace/evtimer/data/ChargingRepository.kt").readText()
        val keys = Regex("""(?:boolean|float|long|string)PreferencesKey\("([^"]+)"\)""")
            .findAll(source).map { it.groupValues[1] }.toSet()
        val included = setOf("notify_at_80", "selected_preset", "start_percent", "draft_preset", "draft_values")
        val excluded = mapOf(
            "battery_capacity" to "Legacy migration only; values now belong to presets",
            "charging_power" to "Legacy migration only; values now belong to presets",
            "max_percent" to "Legacy migration only; values now belong to presets",
            "available_powers" to "Legacy migration only; values now belong to preset options",
            "session_is_running" to "Legacy active timer must not resume",
            "session_start_time" to "Legacy active timer must not resume",
            "snapshot_capacity" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_start_percent" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_target_percent" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_efficiency" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_preset_id" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_preset_name" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_preset_edited" to "Legacy active snapshot; finished sessions are in SQL",
            "snapshot_segments" to "Legacy active snapshot; finished sessions are in SQL"
        )
        assertEquals(included + excluded.keys, keys)
    }

    @Test
    fun largeHistoryUsesConstantExportQueryCountAndNoReadBeforeDelete() = runTest {
        val presets = (0 until 50).map {
            sample.presets.first().copy(id = "preset-$it", name = "Preset $it", position = it.toLong())
        }
        val sessions = (0 until 2_000).map {
            sample.sessions.first().copy(id = it.toLong(), reviewed = true)
        }
        val large = sample.copy(preferences = BackupPreferences("preset-0", 25f), presets = presets, sessions = sessions)
        fixture { f ->
            f.repo.observeSettings().first()
            f.queryCount = 0
            f.repo.restoreBackup(large)
            assertEquals(1, f.queryCount, "Only the running-session guard reads SQL during a valid-selection import")
            f.queryCount = 0
            val exported = f.repo.exportBackup("test")
            assertEquals(4, f.queryCount, "Export must not issue a query per preset or history row")
            val decoded = BackupFormat.decode(BackupFormat.encode(exported))
            assertEquals(exported, decoded)
            assertEquals(2_000, decoded.sessions.size)
            assertEquals(4_000, decoded.sessions.sumOf { it.segments.size })
            f.queryCount = 0
            assertEquals(2_000, f.repo.observeHistory().first().size)
            assertEquals(2, f.queryCount, "History must bulk-read segments instead of issuing one query per session")
            f.queryCount = 0
            f.repo.observeCalibration().first()
            assertEquals(4, f.queryCount, "Factors must bulk-read history, segments, presets and options")
            f.queryCount = 0
            f.repo.createSnapshot(2_000_000L)
            assertEquals(4, f.queryCount, "Snapshot factors must not issue one query per preset or history row")
        }
    }

    private class Fixture {
        val file = Files.createTempFile("evtimer-manual-backup", ".preferences_pb").toFile()
        val job = Job()
        private val delegate = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }
        )
        var failPreferenceWrites = false
        val store = object : DataStore<Preferences> {
            override val data = delegate.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failPreferenceWrites) throw java.io.IOException("injected preferences write failure")
                return delegate.updateData(transform)
            }
        }
        private val sqlite = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            Properties().apply { setProperty("foreign_keys", "true") }, ChargingDatabase.Schema)
        var queryCount = 0
        val driver = object : SqlDriver by sqlite {
            override fun <R> executeQuery(
                identifier: Int?,
                sql: String,
                mapper: (SqlCursor) -> QueryResult<R>,
                parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?
            ): QueryResult<R> {
                queryCount++
                return sqlite.executeQuery(identifier, sql, mapper, parameters, binders)
            }
        }
        val database = ChargingDatabase(driver)
        val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(1_000_000) }
        fun repository() = ChargingRepositoryImpl(store, database, clock, LocalStorageRestoreState)
        val repo = repository()
        suspend fun close() {
            job.cancelAndJoin()
            driver.close()
            check(file.delete())
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { block(f) } finally { f.close() }
    }
}
