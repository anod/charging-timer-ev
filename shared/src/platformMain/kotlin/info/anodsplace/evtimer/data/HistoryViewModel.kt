package info.anodsplace.evtimer.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(private val repository: ChargingHistoryRepository) : ViewModel() {
    val entries: StateFlow<List<ChargingHistoryEntry>?> =
        repository.observeHistory().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun deleteSession(id: Long) = repository.deleteSession(id)
}
