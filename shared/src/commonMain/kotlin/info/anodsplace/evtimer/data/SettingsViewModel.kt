package info.anodsplace.evtimer.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ChargingRepository,
    private val presetRepository: PresetRepository
) : ViewModel() {
    val notifyAt80: StateFlow<Boolean?> =
        repository.observeNotifyAt80().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val presets: StateFlow<List<ChargingPreset>> =
        presetRepository.observePresets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun renamePreset(id: String, name: String) {
        viewModelScope.launch { presetRepository.renamePreset(id, name) }
    }

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
