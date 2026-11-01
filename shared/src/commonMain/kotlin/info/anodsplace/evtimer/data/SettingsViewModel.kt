package info.anodsplace.evtimer.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: ChargingRepository) : ViewModel() {
    val notifyAt80: StateFlow<Boolean?> =
        repository.observeNotifyAt80().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setNotifyAt80(enabled: Boolean) {
        viewModelScope.launch { repository.setNotifyAt80(enabled) }
    }
}
