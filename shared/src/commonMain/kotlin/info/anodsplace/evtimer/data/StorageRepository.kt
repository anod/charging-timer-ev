package info.anodsplace.evtimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.cash.sqldelight.coroutines.asFlow
import info.anodsplace.evtimer.db.Charge_session
import info.anodsplace.evtimer.db.ChargingDatabase
import info.anodsplace.evtimer.db.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class ChargingPreset(val id: String, val name: String, val settings: ChargingSettings)

interface PresetRepository {
    fun observeConfiguration(): Flow<PresetConfiguration>
    suspend fun updateSelectedPreset()
    suspend fun saveAsPreset(name: String)
    suspend fun resetPreset()
    fun observePresets(): Flow<List<ChargingPreset>>
    suspend fun savePreset(preset: ChargingPreset)
    suspend fun deletePreset(id: String)
    suspend fun selectPreset(id: String)
    suspend fun reorderPresets(ids: List<String>)
}

data class ChargingHistoryEntry(
    val id: Long,
    val snapshot: ChargingSessionSnapshot,
    val endedAt: Long,
    val endPercent: Float?,
    val actualPercent: Float?,
    val reviewed: Boolean,
    val interrupted: Boolean = false
)

interface ChargingHistoryRepository {
    fun observeHistory(): Flow<List<ChargingHistoryEntry>>
    suspend fun reviewSession(id: Long, actualPercent: Float?, reviewed: Boolean)
}

class ChargingRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val database: ChargingDatabase,
    private val clock: Clock,
    private val restoreState: StorageRestoreState
) : ChargingRepository, PresetRepository, ChargingHistoryRepository {
    private val queries = database.storageQueries
    private val mutex = Mutex()
    private var initialized = false

    override fun observeNotifyAt80(): Flow<Boolean> =
        dataStore.data.map { it[SessionPreferences.notifyAt80] ?: true }

    override suspend fun setNotifyAt80(enabled: Boolean) {
        dataStore.edit { it[SessionPreferences.notifyAt80] = enabled }
    }

    private suspend fun initialize() = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (initialized) return@withLock
            val prefs = dataStore.data.first()
            database.transaction {
                if (queries.migrationDone().executeAsOne() == 0L) {
                    putPreset(ChargingPreset(CUSTOM_ID, "Default", SessionPreferences.readSettings(prefs)))
                    queries.markMigrated()
                    val snapshot = try {
                        SessionPreferences.readSnapshot(prefs) ?: if (prefs[SessionPreferences.isRunning] == true) {
                            ChargingSessionSnapshot.from(
                                SessionPreferences.readSettings(prefs),
                                requireNotNull(prefs[SessionPreferences.startTime])
                            )
                        } else null
                    } catch (_: IllegalArgumentException) {
                        println("Charging storage: invalid legacy snapshot; clearing session")
                        null
                    }
                    if (snapshot != null) putSession(ChargingSession(prefs[SessionPreferences.isRunning] == true, snapshot))
                }
                if (queries.presetById(CUSTOM_ID).executeAsOneOrNull() == null) {
                    println("Charging storage: missing Default preset; restoring defaults")
                    putPreset(ChargingPreset(CUSTOM_ID, "Default", ChargingSettings.DEFAULT))
                }
                queries.normalizeDefaultName()
                recoverActiveSession()
                if (restoreState.requiresInterruption) {
                    println("Charging storage: restored timer interrupted; explicit start required")
                    queries.interruptRestoredSession(clock.now().toEpochMilliseconds())
                    queries.setCurrentSession(null)
                }
            }
            // The SQL marker commits first: a crash before cleanup retries cleanup, never imports twice.
            dataStore.edit {
                val selected = it[SessionPreferences.selectedPreset]
                if (selected == null || queries.presetById(selected).executeAsOneOrNull() == null) {
                    println("Charging storage: missing preset selection; selecting Default")
                    it[SessionPreferences.selectedPreset] = CUSTOM_ID
                }
                val draftId = it[SessionPreferences.draftPreset]
                val draftValues = it[SessionPreferences.draftValues]
                if ((draftId != null || draftValues != null) &&
                    (draftId == CUSTOM_ID || draftId != it[SessionPreferences.selectedPreset] ||
                        draftValues == null || decodePresetDraft(draftValues) == null)) {
                    println("Charging storage: invalid preset draft; selecting Default")
                    it[SessionPreferences.selectedPreset] = CUSTOM_ID
                    it.remove(SessionPreferences.draftPreset)
                    it.remove(SessionPreferences.draftValues)
                }
                SessionPreferences.clearStructuredData(it)
            }
            restoreState.markReady()
            initialized = true
        }
    }

    private suspend fun <T> transaction(block: () -> T): T {
        initialize()
        return withContext(Dispatchers.Default) { mutex.withLock { database.transactionWithResult { block() } } }
    }

    private fun validate(settings: ChargingSettings) {
        require(settings.batteryCapacity.isFinite() && settings.batteryCapacity > 0f)
        require(settings.chargingPower.isFinite() && settings.chargingPower > 0f)
        require(settings.startPercent.isFinite() && settings.startPercent in 0f..100f)
        require(settings.maxPercent.isFinite() && settings.maxPercent in 0f..100f)
        require(settings.availablePowers.isNotEmpty() && settings.availablePowers.all { it.isFinite() && it > 0f })
    }

    private fun putPreset(preset: ChargingPreset) {
        require(preset.id.isNotBlank() && preset.name.isNotBlank())
        require(preset.id != CUSTOM_ID || preset.name == "Default") { "The Default preset cannot be renamed" }
        val name = if (preset.id == CUSTOM_ID) {
            preset.name
        } else if (queries.presetById(preset.id).executeAsOneOrNull()?.name == preset.name) {
            // Settings updates preserve names accepted before the current name policy.
            preset.name
        } else {
            val validation = normalizeAndValidatePresetName(
                preset.name,
                queries.allPresets().executeAsList().filterNot { it.id == preset.id }.map { it.name }
            )
            require(validation.isValid) { "Invalid preset name: ${validation.error}" }
            validation.name
        }
        validate(preset.settings)
        val settings = preset.settings
        queries.putPreset(preset.id, name, settings.batteryCapacity.toDouble(),
            settings.chargingPower.toDouble(), settings.maxPercent.toDouble())
        queries.clearOptions(preset.id)
        settings.availablePowers.forEachIndexed { index, power -> queries.putOption(preset.id, index.toLong(), power.toDouble()) }
    }

    private fun settings(row: Preset, startPercent: Float): ChargingSettings {
        val raw = androidx.datastore.preferences.core.mutablePreferencesOf(
            SessionPreferences.batteryCapacity to row.battery_capacity.toFloat(),
            SessionPreferences.chargingPower to row.charging_power.toFloat(),
            SessionPreferences.maxPercent to row.target_percent.toFloat(),
            SessionPreferences.startPercent to startPercent,
            SessionPreferences.availablePowers to queries.options(row.id).executeAsList().joinToString(",") { it.power_kw.toString() }
        )
        return SessionPreferences.readSettings(raw)
    }

    override fun observePresets(): Flow<List<ChargingPreset>> = flow {
        initialize()
        emitAll(combine(
            queries.allPresets().asFlow(),
            queries.allOptions().asFlow()
        ) { _, _ ->
            transaction {
                readPresets()
            }
        })
    }

    override suspend fun savePreset(preset: ChargingPreset) { transaction { putPreset(preset) } }

    override suspend fun reorderPresets(ids: List<String>) {
        transaction {
            val existing = readPresets().map { it.id }
            require(ids.firstOrNull() == CUSTOM_ID) { "The Default preset must stay first" }
            require(ids.size == existing.size && ids.toSet() == existing.toSet()) { "Invalid preset order" }
            ids.forEachIndexed { index, id -> queries.setPresetPosition(index.toLong(), id) }
        }
    }

    override suspend fun deletePreset(id: String) {
        changeConfiguration(PresetEvent.Delete(id))
    }

    override suspend fun selectPreset(id: String) {
        changeConfiguration(PresetEvent.Select(id))
    }

    override suspend fun saveSettings(settings: ChargingSettings) {
        validate(settings)
        changeConfiguration(PresetEvent.Edit(settings))
    }

    private fun readPresets(): List<ChargingPreset> = queries.allPresets().executeAsList().map {
        ChargingPreset(it.id, it.name, settings(it, ChargingSettings.DEFAULT.startPercent))
    }.sortedBy { if (it.id == CUSTOM_ID) 0 else 1 }

    private fun configuration(prefs: Preferences, presets: List<ChargingPreset>): PresetConfiguration {
        val selected = presets.firstOrNull { it.id == prefs[SessionPreferences.selectedPreset] }
            ?: presets.first { it.id == CUSTOM_ID }
        val draft = if (selected.id != CUSTOM_ID && prefs[SessionPreferences.draftPreset] == selected.id) {
            prefs[SessionPreferences.draftValues]?.let(::decodePresetDraft)
        } else null
        return PresetConfiguration(presets, selected.id, (draft ?: selected.settings).copy(
            startPercent = SessionPreferences.readSettings(prefs).startPercent
        ))
    }

    override fun observeConfiguration(): Flow<PresetConfiguration> = flow {
        initialize()
        emitAll(combine(dataStore.data, observePresets()) { _, _ ->
            withContext(Dispatchers.Default) {
                mutex.withLock { configuration(dataStore.data.first(), readPresets()) }
            }
        })
    }

    override fun observeSettings(): Flow<ChargingSettings> =
        observeConfiguration().map { it.settings }

    override suspend fun updateSelectedPreset() = changeConfiguration(PresetEvent.Update)
    override suspend fun resetPreset() = changeConfiguration(PresetEvent.Reset)

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun saveAsPreset(name: String) =
        changeConfiguration(PresetEvent.SaveNew(kotlin.uuid.Uuid.random().toString(), name))

    private suspend fun changeConfiguration(event: PresetEvent) {
        initialize()
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val before = configuration(dataStore.data.first(), readPresets())
                if (event is PresetEvent.Select) require(before.presets.any { it.id == event.id }) { "Unknown preset: ${event.id}" }
                val after = before.reduce(event)
                database.transaction {
                    after.presets.filter { it !in before.presets }.forEach(::putPreset)
                    before.presets.filter { old -> after.presets.none { it.id == old.id } }
                        .forEach { queries.deletePreset(it.id) }
                }
                dataStore.edit {
                    it[SessionPreferences.selectedPreset] = after.selectedId
                    it[SessionPreferences.startPercent] = after.settings.startPercent
                    if (after.isEdited) {
                        it[SessionPreferences.draftPreset] = after.selectedId
                        it[SessionPreferences.draftValues] = after.settings.encodeDraft()
                    } else {
                        it.remove(SessionPreferences.draftPreset)
                        it.remove(SessionPreferences.draftValues)
                    }
                }
            }
        }
    }

    override suspend fun createSnapshot(startedAt: Long): ChargingSessionSnapshot {
        initialize()
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val state = configuration(dataStore.data.first(), readPresets())
                ChargingSessionSnapshot.from(state.settings, startedAt).copy(
                    presetId = state.selectedId, presetName = state.selected.name, presetEdited = state.isEdited
                )
            }
        }
    }

    private fun snapshot(row: Charge_session) = ChargingSessionSnapshot(
        row.battery_capacity.toFloat(), row.start_percent.toFloat(), row.target_percent.toFloat(),
        queries.segments(row.id).executeAsList().map { PowerSegment(it.started_at, it.power_kw.toFloat()) },
        row.preset_id, row.preset_name, row.efficiency.toFloat(), row.preset_edited
    )

    private fun recoverActiveSession() {
        val row = queries.activeSession().executeAsOneOrNull() ?: return
        try {
            snapshot(row)
        } catch (_: IllegalArgumentException) {
            println("Charging storage: invalid persisted active snapshot; clearing session ${row.id}")
            queries.clearRunning()
            queries.setCurrentSession(null)
        }
    }

    private fun putSession(session: ChargingSession) {
        val snapshot = session.snapshot ?: return
        queries.putSession(snapshot.startTime, snapshot.batteryCapacity.toDouble(), snapshot.startPercent.toDouble(),
            snapshot.targetPercent.toDouble(), snapshot.efficiency.toDouble(), snapshot.presetId, snapshot.presetName,
            session.isRunning, snapshot.presetEdited)
        queries.clearSegments(snapshot.startTime)
        snapshot.segments.forEach { queries.putSegment(snapshot.startTime, it.startedAt, it.powerKw.toDouble()) }
        queries.setCurrentSession(snapshot.startTime)
    }

    override suspend fun saveSession(session: ChargingSession) {
        transaction {
            val active = queries.activeSession().executeAsOneOrNull()
            if (!session.isRunning || (active != null && active.id != session.startTime)) {
                val now = clock.now().toEpochMilliseconds()
                queries.finishSession(now, active?.let { ChargingCalculator.percentAt(snapshot(it), now).toDouble() })
            }
            if (session.snapshot != null) putSession(session) else queries.setCurrentSession(null)
        }
    }

    override fun observeSession(): Flow<ChargingSession> = flow {
        initialize()
        emitAll(combine(
            queries.latestSession().asFlow(),
            queries.allSegments().asFlow()
        ) { _, _ ->
            transaction {
                val row = queries.latestSession().executeAsOneOrNull()
                if (row == null) ChargingSession() else try {
                    ChargingSession(row.is_running, snapshot(row))
                } catch (_: IllegalArgumentException) {
                    println("Charging storage: invalid persisted snapshot ${row.id}; clearing active state")
                    queries.clearRunning()
                    queries.setCurrentSession(null)
                    ChargingSession()
                }
            }
        })
    }

    override fun observeHistory(): Flow<List<ChargingHistoryEntry>> = flow {
        initialize()
        emitAll(combine(
            queries.history(mapper = ::Charge_session).asFlow(),
            queries.allSegments().asFlow()
        ) { _, _ ->
            transaction {
                queries.history(mapper = ::Charge_session).executeAsList().mapNotNull { row ->
                    try {
                        ChargingHistoryEntry(row.id, snapshot(row), requireNotNull(row.ended_at),
                            row.end_percent?.toFloat(), row.actual_percent?.toFloat(), row.reviewed, row.interrupted)
                    } catch (_: IllegalArgumentException) {
                        println("Charging storage: invalid history snapshot ${row.id}; skipping")
                        null
                    }
                }
            }
        })
    }

    override suspend fun reviewSession(id: Long, actualPercent: Float?, reviewed: Boolean) {
        require(actualPercent == null || (actualPercent.isFinite() && actualPercent in 0f..100f))
        transaction {
            require(queries.history().executeAsList().any { it.id == id }) { "Unknown completed session: $id" }
            queries.reviewSession(actualPercent?.toDouble(), reviewed, id)
        }
    }

    companion object {
        const val CUSTOM_ID = "custom"
    }
}
