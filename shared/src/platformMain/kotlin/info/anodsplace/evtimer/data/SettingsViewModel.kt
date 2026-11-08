package info.anodsplace.evtimer.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import info.anodsplace.evtimer.APP_VERSION
import kotlin.time.Clock

data class BackupState(
    val busy: Boolean = false,
    val pending: ManualBackup? = null,
    val message: BackupMessage? = null,
    val error: BackupError? = null
)

enum class BackupMessage { EXPORTED, RESTORED }

class SettingsViewModel(
    private val repository: ChargingRepository,
    private val presetRepository: PresetRepository,
    private val backups: ManualBackupRepository
) : ViewModel() {
    private val powerEdits = Mutex()
    private val mutableSettings = MutableStateFlow<ChargingSettings?>(null)
    val settings: StateFlow<ChargingSettings?> = mutableSettings.asStateFlow()
    private val mutableSettingsLoadFailed = MutableStateFlow(false)
    val settingsLoadFailed = mutableSettingsLoadFailed.asStateFlow()
    private val mutableSession = MutableStateFlow<ChargingSession?>(null)
    val session: StateFlow<ChargingSession?> = mutableSession.asStateFlow()
    private val mutableNotifyAt80 = MutableStateFlow<Boolean?>(null)
    val notifyAt80: StateFlow<Boolean?> = mutableNotifyAt80.asStateFlow()
    private val mutablePresets = MutableStateFlow<List<ChargingPreset>>(emptyList())
    val presets: StateFlow<List<ChargingPreset>> = mutablePresets.asStateFlow()
    private val mutableCalibration = MutableStateFlow(ChargingCalibration())
    val calibration: StateFlow<ChargingCalibration> = mutableCalibration.asStateFlow()
    private val settingsLoader = RetryableLoad(viewModelScope, "Charging powers",
        onLoading = { mutableSettings.value = null; mutableSettingsLoadFailed.value = false },
        onError = { mutableSettingsLoadFailed.value = true }
    ) {
        combine(
            repository.observeSettings(), repository.observeSession(), repository.observeNotifyAt80(),
            presetRepository.observePresets(), presetRepository.observeCalibration()
        ) { settings, session, notify, presets, calibration ->
            SettingsData(settings, session, notify, presets, calibration)
        }.collect {
            mutableSession.value = it.session
            mutableNotifyAt80.value = it.notifyAt80
            mutablePresets.value = it.presets
            mutableCalibration.value = it.calibration
            mutableSettings.value = it.settings
        }
    }

    init { settingsLoader.retry() }

    fun retrySettingsLoad() = settingsLoader.retry()

    fun addPower(power: Float) {
        viewModelScope.launch {
            powerEdits.withLock {
                if (!power.isFinite() || power <= 0f) {
                    println("Charging settings: rejecting invalid custom power")
                    return@withLock
                }
                val latest = repository.observeSettings().first()
                repository.saveSettings(latest.withAddedPower(power))
            }
        }
    }

    fun removePower(power: Float) {
        viewModelScope.launch {
            powerEdits.withLock {
                val latest = repository.observeSettings().first()
                if (power !in latest.availablePowers || latest.availablePowers.none { it != power }) {
                    println("Charging settings: rejecting missing or last power option removal")
                    return@withLock
                }
                repository.saveSettings(latest.withRemovedPower(power))
            }
        }
    }

    private val mutableBackup = MutableStateFlow(BackupState())
    val backup = mutableBackup.asStateFlow()

    fun backupFileName(): String = "ev-charging-timer-${Clock.System.now().toString().substringBefore('T')}.json"

    fun exportBackup(destination: BackupDestination) = backupOperation {
        val bytes = withContext(Dispatchers.Default) { BackupFormat.encode(backups.exportBackup(APP_VERSION)) }
        destination.write(bytes)
        mutableBackup.value = BackupState(message = BackupMessage.EXPORTED)
    }

    fun readBackup(source: BackupSource) = backupOperation {
        val pending = withContext(Dispatchers.Default) { BackupFormat.decode(source.read()) }
        mutableBackup.value = BackupState(pending = pending)
    }

    fun confirmRestore() {
        val pending = mutableBackup.value.pending ?: return
        backupOperation {
            backups.restoreBackup(pending)
            mutableBackup.value = BackupState(message = BackupMessage.RESTORED)
        }
    }

    fun dismissBackup() {
        if (!mutableBackup.value.busy) mutableBackup.value = BackupState()
    }

    fun documentPickerError() {
        mutableBackup.value = BackupState(error = BackupError.IO)
    }

    private fun backupOperation(block: suspend () -> Unit) {
        if (mutableBackup.value.busy) return
        mutableBackup.value = mutableBackup.value.copy(busy = true, message = null, error = null)
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("Manual backup failed: $error")
                mutableBackup.value = BackupState(error = (error as? BackupException)?.error ?: BackupError.IO)
            }
        }
    }

    suspend fun resetLearnedEstimates(presetId: String, powerKw: Float) =
        presetRepository.resetLearnedEstimates(presetId, powerKw)

    fun renamePreset(id: String, name: String) {
        viewModelScope.launch { presetRepository.renamePreset(id, name) }
    }

    private data class SettingsData(
        val settings: ChargingSettings,
        val session: ChargingSession,
        val notifyAt80: Boolean,
        val presets: List<ChargingPreset>,
        val calibration: ChargingCalibration
    )

    fun deletePreset(id: String) {
        viewModelScope.launch { presetRepository.deletePreset(id) }
    }

    fun reorderPresets(ids: List<String>) {
        viewModelScope.launch { presetRepository.reorderPresets(ids) }
    }

    fun movePreset(id: String, direction: Int) {
        viewModelScope.launch {
            val ids = presets.value.map { it.id }.toMutableList()
            val index = ids.indexOf(id)
            val target = index + direction
            require(index >= 0 && target in ids.indices) { "Invalid preset move" }
            ids.add(target, ids.removeAt(index))
            presetRepository.reorderPresets(ids)
        }
    }

    fun setNotifyAt80(enabled: Boolean) {
        viewModelScope.launch { repository.setNotifyAt80(enabled) }
    }
}
