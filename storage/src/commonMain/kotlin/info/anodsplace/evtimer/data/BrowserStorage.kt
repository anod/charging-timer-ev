package info.anodsplace.evtimer.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
data class BrowserState(
    val schemaVersion: Int = 1,
    val backup: ManualBackup,
    val activeSession: BackupSession? = null
)

@Serializable
data class StoredBrowserDocument(val revision: String, val payload: String)

/** Implementations must resolve writes only after the durable transaction commits. */
interface BrowserDocumentStore {
    suspend fun read(): StoredBrowserDocument?
    suspend fun compareAndSet(expectedRevision: String?, payload: String): StoredBrowserDocument
}

/**
 * No cached state is authoritative. Each operation reads IndexedDB, and compare-and-set
 * rejects concurrent tab writes rather than overwriting another tab's changes.
 */
class BrowserStorage(
    private val store: BrowserDocumentStore,
    private val clock: Clock = Clock.System
) : ManualBackupRepository {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    suspend fun load(): BrowserState = mutex.withLock { readOrCreate().second }

    suspend fun update(transform: (BrowserState) -> BrowserState): BrowserState = mutex.withLock {
        val (record, before) = readOrCreate()
        val after = transform(before)
        validate(after)
        store.compareAndSet(record.revision, json.encodeToString(BrowserState.serializer(), after))
        after
    }

    private suspend fun readOrCreate(): Pair<StoredBrowserDocument, BrowserState> {
        val record = store.read()
        if (record != null) {
            val state = try {
                json.decodeFromString(BrowserState.serializer(), record.payload)
            } catch (error: IllegalArgumentException) {
                throw BackupException(BackupError.INVALID_DATA, "Cannot read browser storage; existing data was not replaced", error)
            }
            validate(state)
            return record to state
        }
        val defaults = ChargingSettings.DEFAULT
        val state = BrowserState(backup = ManualBackup(
            BackupFormat.FORMAT, BackupFormat.VERSION, clock.now().toString(), "browser",
            BackupPreferences("custom", defaults.startPercent),
            listOf(BackupPreset("custom", "Default", defaults.batteryCapacity.toDouble(),
                defaults.chargingPower.toDouble(), defaults.maxPercent.toDouble(),
                defaults.availablePowers.map { it.toDouble() })),
            emptyList()
        ))
        return store.compareAndSet(null, json.encodeToString(BrowserState.serializer(), state)) to state
    }

    private fun validate(state: BrowserState) {
        if (state.schemaVersion != 1) {
            throw BackupException(BackupError.NEWER_VERSION, "Unsupported browser storage version")
        }
        BackupFormat.validate(state.backup)
        require(state.backup.formatVersion == BackupFormat.VERSION) { "Browser storage must use backup v2" }
        require(state.backup.presets.any { it.id == state.backup.preferences.selectedPresetId }) {
            "Browser storage has an unknown selected preset"
        }
        require(state.backup.sessions.all { it.endedAt != null }) { "History cannot contain running sessions" }
        state.activeSession?.let {
            require(it.endedAt == null && state.backup.sessions.none { row -> row.id == it.id }) {
                "Invalid active browser session"
            }
            BackupFormat.validate(state.backup.copy(sessions = listOf(it)))
        }
    }

    override suspend fun exportBackup(appVersion: String): ManualBackup =
        load().backup.copy(exportedAt = clock.now().toString(), appVersion = appVersion)

    override suspend fun restoreBackup(backup: ManualBackup) {
        // The released reader owns v1 upgrading as well as all primitive/size validation.
        val validated = BackupFormat.decode(BackupFormat.encode(backup))
        update { before ->
            if (before.activeSession != null) {
                throw BackupException(BackupError.SESSION_RUNNING, "Stop the current session first")
            }
            val now = clock.now().toEpochMilliseconds()
            val newest = validated.sessions.maxWithOrNull(
                compareBy<BackupSession> { it.endedAt ?: maxOf(now, it.segments.last().startedAt) }.thenBy { it.id }
            )?.id
            val ordered = validated.presets.sortedWith(
                compareBy<BackupPreset> { it.position }.thenBy { it.name }.thenBy { it.id }
            )
            BrowserState(backup = validated.copy(
                presets = ordered,
                preferences = validated.preferences.copy(
                    selectedPresetId = validated.preferences.selectedPresetId
                        .takeIf { id -> ordered.any { it.id == id } } ?: ordered.first().id
                ),
                sessions = validated.sessions.map {
                    it.copy(
                        endedAt = it.endedAt ?: maxOf(now, it.segments.last().startedAt),
                        endPercent = if (it.endedAt == null) null else it.endPercent,
                        interrupted = it.interrupted || it.endedAt == null,
                        reviewed = it.reviewed || it.id == newest
                    )
                }
            ))
        }
    }
}
