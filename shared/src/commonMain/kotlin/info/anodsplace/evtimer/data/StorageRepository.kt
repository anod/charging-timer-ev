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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class ChargingPreset(val id: String, val name: String, val settings: ChargingSettings)

interface PresetRepository {
    fun observePresets(): Flow<List<ChargingPreset>>
    suspend fun savePreset(preset: ChargingPreset)
    suspend fun deletePreset(id: String)
    suspend fun selectPreset(id: String)
}

data class ChargingHistoryEntry(
    val id: Long,
    val snapshot: ChargingSessionSnapshot,
    val endedAt: Long,
    val endPercent: Float?,
    val actualPercent: Float?,
    val reviewed: Boolean
)

interface ChargingHistoryRepository {
    fun observeHistory(): Flow<List<ChargingHistoryEntry>>
    suspend fun reviewSession(id: Long, actualPercent: Float?, reviewed: Boolean)
}

class ChargingRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val database: ChargingDatabase,
    private val clock: Clock
) : ChargingRepository, PresetRepository, ChargingHistoryRepository {
    private val queries = database.storageQueries
    private val mutex = Mutex()
    private var initialized = false

    private suspend fun initialize() = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (initialized) return@withLock
            val prefs = dataStore.data.first()
            database.transaction {
                if (queries.migrationDone().executeAsOne() == 0L) {
                    putPreset(ChargingPreset(CUSTOM_ID, "Custom", SessionPreferences.readSettings(prefs)))
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
                    println("Charging storage: missing Custom preset; restoring defaults")
                    putPreset(ChargingPreset(CUSTOM_ID, "Custom", ChargingSettings.DEFAULT))
                }
                recoverActiveSession()
            }
            // The SQL marker commits first: a crash before cleanup retries cleanup, never imports twice.
            dataStore.edit {
                if (it[SessionPreferences.selectedPreset] == null) it[SessionPreferences.selectedPreset] = CUSTOM_ID
                SessionPreferences.clearStructuredData(it)
            }
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
        validate(preset.settings)
        val settings = preset.settings
        queries.putPreset(preset.id, preset.name, settings.batteryCapacity.toDouble(),
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
                queries.allPresets().executeAsList().map {
                    ChargingPreset(it.id, it.name, settings(it, ChargingSettings.DEFAULT.startPercent))
                }
            }
        })
    }

    override suspend fun savePreset(preset: ChargingPreset) { transaction { putPreset(preset) } }

    override suspend fun deletePreset(id: String) {
        require(id != CUSTOM_ID) { "The Custom preset cannot be deleted" }
        transaction {
            require(queries.presetById(id).executeAsOneOrNull() != null) { "Unknown preset: $id" }
            queries.deletePreset(id)
        }
        dataStore.edit { if (it[SessionPreferences.selectedPreset] == id) it[SessionPreferences.selectedPreset] = CUSTOM_ID }
    }

    override suspend fun selectPreset(id: String) {
        transaction { require(queries.presetById(id).executeAsOneOrNull() != null) { "Unknown preset: $id" } }
        dataStore.edit { it[SessionPreferences.selectedPreset] = id }
    }

    override suspend fun saveSettings(settings: ChargingSettings) {
        validate(settings)
        initialize()
        val id = dataStore.data.first()[SessionPreferences.selectedPreset] ?: CUSTOM_ID
        transaction {
            val row = queries.presetById(id).executeAsOneOrNull() ?: queries.presetById(CUSTOM_ID).executeAsOne()
            putPreset(ChargingPreset(row.id, row.name, settings))
        }
        dataStore.edit { it[SessionPreferences.startPercent] = settings.startPercent }
    }

    override fun observeSettings(): Flow<ChargingSettings> = flow {
        initialize()
        emitAll(combine(dataStore.data, observePresets()) { prefs, presets ->
            val selected = presets.firstOrNull { it.id == prefs[SessionPreferences.selectedPreset] }
                ?: presets.first { it.id == CUSTOM_ID }
            selected.settings.copy(startPercent = SessionPreferences.readSettings(prefs).startPercent)
        })
    }

    private fun snapshot(row: Charge_session) = ChargingSessionSnapshot(
        row.battery_capacity.toFloat(), row.start_percent.toFloat(), row.target_percent.toFloat(),
        queries.segments(row.id).executeAsList().map { PowerSegment(it.started_at, it.power_kw.toFloat()) },
        row.preset_id, row.preset_name, row.efficiency.toFloat()
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
            snapshot.targetPercent.toDouble(), snapshot.efficiency.toDouble(), snapshot.presetId, snapshot.presetName, session.isRunning)
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
                            row.end_percent?.toFloat(), row.actual_percent?.toFloat(), row.reviewed)
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
