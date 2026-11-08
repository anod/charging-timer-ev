package info.anodsplace.evtimer.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.retryWhen

class HistoryViewModel(private val repository: ChargingHistoryRepository) : ViewModel() {
    val entries: StateFlow<List<ChargingHistoryEntry>?> =
        repository.observeHistory().retryWhen { error, _ ->
            if (error !is Exception || error is CancellationException) throw error
            println("Charging history: load failed; retrying: $error")
            delay(5_000)
            true
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun deleteSession(id: Long) = repository.deleteSession(id)
}
