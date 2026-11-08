package info.anodsplace.evtimer.data

import kotlin.math.ceil
import kotlin.time.Clock

/** Epoch snapshots, not the browser ticker, are the source of charging progress. */
class BrowserTimerRuntime(
    val storage: BrowserStorage,
    private val clock: Clock = Clock.System
) {
    suspend fun reconcile(): BrowserState {
        val state = storage.load()
        val active = state.activeSession ?: return state
        val end = active.snapshot().targetTime()
        if (clock.now().toEpochMilliseconds() < end) return state
        return storage.update { latest ->
            if (latest.activeSession != active) latest else latest.finish(active, end)
        }
    }

    suspend fun configure(event: ConfigurationViewEvent): BrowserState = storage.update { state ->
        val before = state.configuration()
        val settings = before.settings
        val after = when (event) {
            is ConfigurationViewEvent.SelectPreset -> {
                require(before.presets.any { it.id == event.id }) { "Unknown preset: ${event.id}" }
                before.reduce(PresetEvent.Select(event.id))
            }
            ConfigurationViewEvent.UpdatePreset -> before.reduce(PresetEvent.Update)
            ConfigurationViewEvent.ResetPreset -> before.reduce(PresetEvent.Reset)
            is ConfigurationViewEvent.SaveAsPreset -> before.reduce(PresetEvent.SaveNew(
                newPresetId(state), event.name
            ))
            is ConfigurationViewEvent.UpdateBatteryCapacity -> before.reduce(PresetEvent.Edit(settings.copy(batteryCapacity = event.capacity)))
            is ConfigurationViewEvent.UpdateChargingPower -> before.reduce(PresetEvent.Edit(settings.copy(chargingPower = event.power)))
            is ConfigurationViewEvent.UpdateStartPercent -> before.reduce(PresetEvent.Edit(settings.copy(startPercent = event.percent)))
            is ConfigurationViewEvent.UpdateMaxPercent -> before.reduce(PresetEvent.Edit(settings.copy(maxPercent = event.percent)))
            is ConfigurationViewEvent.AddCustomPower -> before.reduce(PresetEvent.Edit(settings.withAddedPower(event.power)))
            is ConfigurationViewEvent.RemoveCustomPower -> {
                require(event.power in settings.availablePowers && settings.availablePowers.size > 1)
                before.reduce(PresetEvent.Edit(settings.withRemovedPower(event.power)))
            }
            ConfigurationViewEvent.StartCharging -> error("Use start()")
        }
        state.withConfiguration(after)
    }

    suspend fun start(): BrowserState = storage.update { state ->
        if (state.activeSession != null) return@update state
        val configuration = state.configuration()
        val settings = configuration.settings
        require(settings.startPercent < settings.maxPercent) { "Start must be below target" }
        val now = clock.now().toEpochMilliseconds()
        // History IDs must remain unique even after a backwards system clock adjustment.
        val id = maxOf(now, (state.backup.sessions.maxOfOrNull { it.id } ?: 0L) + 1L)
        val calibration = state.calibration(configuration,
            mapOf(configuration.selectedId to settings.batteryCapacity))
        val factor = calibration.factor(configuration.selectedId, settings.chargingPower)
        val snapshot = ChargingSessionSnapshot.from(settings, now).copy(
            presetId = configuration.selectedId,
            presetName = configuration.selected.name,
            presetEdited = configuration.isEdited,
            efficiency = factor,
            segments = listOf(PowerSegment(now, settings.chargingPower, factor))
        )
        state.copy(
            activeSession = snapshot.backupSession(id),
            notificationSessionId = id,
            milestoneNotificationAttempted = false,
            completionNotificationAttempted = false
        )
    }

    suspend fun stop(): BrowserState = storage.update { state ->
        val active = state.activeSession ?: return@update state
        val snapshot = active.snapshot()
        val now = clock.now().toEpochMilliseconds()
        state.finish(active, minOf(maxOf(now, snapshot.segments.last().startedAt), snapshot.targetTime()))
    }

    suspend fun changePower(powerKw: Float): BrowserState {
        reconcile()
        return storage.update { state ->
            val active = state.activeSession ?: return@update state
            val snapshot = active.snapshot()
            val calibration = state.calibration(capacityOverrides =
                snapshot.presetId?.let { mapOf(it to snapshot.batteryCapacity) } ?: emptyMap())
            val changed = snapshot.withPower(
                clock.now().toEpochMilliseconds(), powerKw,
                calibration.factor(snapshot.presetId, powerKw)
            ) ?: return@update state
            state.copy(activeSession = changed.backupSession(active.id))
        }
    }

    suspend fun addPower(powerKw: Float): BrowserState = storage.update { state ->
        val configuration = state.configuration()
        state.withConfiguration(configuration.reduce(PresetEvent.Edit(
            configuration.settings.withAddedPower(powerKw)
        )))
    }

    suspend fun acknowledgeCompletion(id: Long? = null): BrowserState = storage.update { state ->
        state.copy(backup = state.backup.copy(sessions = state.backup.sessions.map {
            if (it.id == (id ?: state.notificationSessionId)) it.copy(reviewed = true) else it
        }))
    }

    suspend fun deleteHistory(id: Long): BrowserState = storage.update { state ->
        state.copy(backup = state.backup.copy(sessions = state.backup.sessions.filterNot { it.id == id }))
    }

    suspend fun renamePreset(id: String, name: String): BrowserState = storage.update { state ->
        val configuration = state.configuration()
        val preset = requireNotNull(configuration.presets.firstOrNull { it.id == id }) { "Unknown preset: $id" }
        if (preset.name == name) return@update state
        val validated = normalizeAndValidatePresetName(name, configuration.presets.filterNot { it.id == id }.map { it.name })
        require(validated.isValid) { "Invalid preset name: ${validated.error}" }
        state.withConfiguration(configuration.copy(presets = configuration.presets.map {
            if (it.id == id) it.copy(name = validated.name) else it
        }))
    }

    suspend fun deletePreset(id: String): BrowserState = storage.update { state ->
        state.withConfiguration(state.configuration().reduce(PresetEvent.Delete(id)))
    }

    suspend fun reorderPresets(ids: List<String>): BrowserState = storage.update { state ->
        val configuration = state.configuration()
        require(ids.size == configuration.presets.size && ids.toSet() == configuration.presets.map { it.id }.toSet()) {
            "Reorder must contain every preset exactly once"
        }
        state.withConfiguration(configuration.copy(presets = ids.map { id -> configuration.presets.first { it.id == id } }))
    }

    suspend fun movePreset(id: String, direction: Int): BrowserState = storage.update { state ->
        val configuration = state.configuration()
        val presets = configuration.presets.toMutableList()
        val index = presets.indexOfFirst { it.id == id }
        val target = index + direction
        require(index >= 0 && target in presets.indices) { "Invalid preset move" }
        presets.add(target, presets.removeAt(index))
        state.withConfiguration(configuration.copy(presets = presets))
    }

    suspend fun removePower(powerKw: Float): BrowserState = removePowerInternal(powerKw).first

    suspend fun removePowerForUndo(powerKw: Float): RemovedPower = removePowerInternal(powerKw).second

    private suspend fun removePowerInternal(powerKw: Float): Pair<BrowserState, RemovedPower> {
        var removed: RemovedPower? = null
        val updated = storage.update { state ->
            val configuration = state.configuration()
            val (after, token) = configuration.removePower(powerKw)
            removed = token
            state.withConfiguration(after)
        }
        return updated to requireNotNull(removed)
    }

    suspend fun restorePower(removed: RemovedPower): BrowserState = storage.update { state ->
        state.withConfiguration(state.configuration().restorePower(removed))
    }

    suspend fun setNotifyAt80(enabled: Boolean): BrowserState = storage.update { state ->
        state.copy(backup = state.backup.copy(preferences = state.backup.preferences.copy(notifyAt80 = enabled)))
    }

    suspend fun saveReading(id: Long, actualPercent: Float?, calibrationEnabled: Boolean): BrowserState =
        storage.update { state ->
            require(actualPercent == null || (actualPercent.isFinite() && actualPercent in 0f..100f))
            require(state.backup.sessions.any { it.id == id }) { "Unknown completed session: $id" }
            state.copy(backup = state.backup.copy(sessions = state.backup.sessions.map {
                if (it.id == id) it.copy(actualPercent = actualPercent?.toDouble(),
                    calibrationEnabled = actualPercent != null && calibrationEnabled) else it
            }))
        }

    suspend fun resetLearnedEstimates(presetId: String, powerKw: Float): BrowserState = storage.update { state ->
        require(presetId.isNotBlank() && powerKw.isFinite() && powerKw > 0f)
        val ids = state.historyEntries().filter {
            it.snapshot.presetId == presetId && ChargingCalibration.singleSpeedPower(it) == powerKw
        }.map { it.id }.toSet()
        state.copy(backup = state.backup.copy(sessions = state.backup.sessions.map {
            if (it.id in ids) it.copy(calibrationEnabled = false) else it
        }))
    }

    private fun newPresetId(state: BrowserState): String {
        val prefix = "browser-${clock.now().toEpochMilliseconds()}"
        return generateSequence(0) { it + 1 }.map { "$prefix-$it" }
            .first { candidate -> state.backup.presets.none { it.id == candidate } }
    }
}

fun BrowserState.configuration(): PresetConfiguration {
    val preferences = backup.preferences
    val presets = backup.presets.map {
        ChargingPreset(it.id, it.name, ChargingSettings(
            it.batteryCapacity.toFloat(), it.chargingPower.toFloat(), preferences.startPercent,
            it.targetPercent.toFloat(), it.powerOptions.map(Double::toFloat)
        ))
    }
    val selected = presets.first { it.id == preferences.selectedPresetId }
    return PresetConfiguration(presets, selected.id,
        preferences.draft?.settings(preferences.startPercent) ?: selected.settings)
}

private fun BrowserState.withConfiguration(configuration: PresetConfiguration): BrowserState = copy(
    backup = backup.copy(
        preferences = backup.preferences.copy(
            selectedPresetId = configuration.selectedId,
            startPercent = configuration.settings.startPercent,
            draft = if (configuration.isEdited) configuration.settings.let {
                BackupDraft(configuration.selectedId, it.batteryCapacity, it.chargingPower, it.maxPercent, it.availablePowers)
            } else null
        ),
        presets = configuration.presets.mapIndexed { index, preset ->
            preset.settings.let {
                BackupPreset(preset.id, preset.name, it.batteryCapacity.toDouble(), it.chargingPower.toDouble(),
                    it.maxPercent.toDouble(), it.availablePowers.map(Float::toDouble), index.toLong())
            }
        }
    )
)

fun BackupSession.snapshot() = ChargingSessionSnapshot(
    batteryCapacity.toFloat(), startPercent.toFloat(), targetPercent.toFloat(),
    segments.map { PowerSegment(it.startedAt, it.powerKw.toFloat(), it.efficiency?.toFloat()) },
    presetId, presetName, efficiency.toFloat(), presetEdited
)

private fun ChargingSessionSnapshot.backupSession(id: Long) = BackupSession(
    id, batteryCapacity.toDouble(), startPercent.toDouble(), targetPercent.toDouble(), efficiency.toDouble(),
    segments.map { BackupSegment(it.startedAt, it.powerKw.toDouble(), it.efficiency?.toDouble()) },
    presetId, presetName, presetEdited = presetEdited
)

fun ChargingSessionSnapshot.targetTime(): Long = startTime + ceil(
    ChargingCalculator.minutesToReach(this, startTime, targetPercent) * 60_000.0
).toLong()

private fun BrowserState.finish(active: BackupSession, endedAt: Long): BrowserState = copy(
    activeSession = null,
    backup = backup.copy(sessions = backup.sessions + active.copy(
        endedAt = endedAt,
        endPercent = ChargingCalculator.percentAt(active.snapshot(), endedAt).toDouble()
    ))
)

fun BrowserState.calibration(
    configuration: PresetConfiguration = configuration(),
    capacityOverrides: Map<String, Float> = emptyMap()
) = ChargingCalibration.derive(
    historyEntries(),
    configuration.presets,
    capacityOverrides
)

fun BrowserState.historyEntries(): List<ChargingHistoryEntry> = backup.sessions.map {
    ChargingHistoryEntry(it.id, it.snapshot(), requireNotNull(it.endedAt), it.endPercent?.toFloat(),
        it.actualPercent?.toFloat(), it.reviewed, it.interrupted, it.calibrationEnabled)
}.sortedByDescending { it.endedAt }

fun BrowserState.runningViewState(now: Long, formatter: ClockTimeFormatter): RunningViewState? {
    val active = activeSession ?: return null
    val snapshot = active.snapshot()
    val end = snapshot.targetTime()
    val milestone = ChargingCalculator.estimateMilestone(snapshot, now)
    return RunningViewState(
        isLoading = false,
        startTime = snapshot.startTime,
        estimatedEndTime = end,
        estimatedEndTimeText = formatter.format(end),
        settings = configuration().settings.copy(
            batteryCapacity = snapshot.batteryCapacity, startPercent = snapshot.startPercent,
            maxPercent = snapshot.targetPercent, chargingPower = snapshot.powerAt(now)
        ),
        calculation = ChargingCalculation(
            ceil(ChargingCalculator.minutesToReach(snapshot, now, snapshot.targetPercent)).toInt(),
            ChargingCalculator.percentAt(snapshot, now), snapshot.powerAt(now)
        ),
        elapsedMinutes = ((now - snapshot.startTime).coerceAtLeast(0L) / 60_000L).toInt(),
        milestoneEstimate = milestone,
        milestoneEtaText = milestone?.let { formatter.format(it.estimatedTime) }.orEmpty(),
        startTimeText = formatter.format(snapshot.startTime),
        timeProgress = timeProgress(snapshot, now, end)
    )
}
