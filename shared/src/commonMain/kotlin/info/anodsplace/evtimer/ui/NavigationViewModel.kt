package info.anodsplace.evtimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class Destination {
    Config, Running, EndSummary, History, Settings, Licenses, PresetEditor
}

class NavigationViewModel(
    service: ChargingService,
    private val history: ChargingHistoryRepository
) : ViewModel() {
    private var wasRunning: Boolean? = service.status.value.takeUnless { it.isLoading }?.isRunning
    private var pendingId: Long? = null
    private var historySummaryId: Long? = null
    private val mutableSummary = MutableStateFlow<ChargingHistoryEntry?>(null)
    val summary = mutableSummary.asStateFlow()
    private val mutableBackStack = MutableStateFlow(if (wasRunning == true) sessionStack(true) else emptyList())
    val backStack = mutableBackStack.asStateFlow()

    init {
        viewModelScope.launch {
            combine(service.status, history.observeHistory()) { status, entries -> status to entries }
                .collect { (status, entries) ->
                    if (status.isLoading) return@collect
                    // Status and history invalidate independently after the same storage transaction.
                    if (wasRunning == true && !status.isRunning && status.startTime > 0L &&
                        entries.none { it.id == status.startTime }
                    ) return@collect
                    val pending = entries.firstOrNull()?.takeIf { !it.reviewed && !status.isRunning }
                    // Keep the outgoing entry available until its navigation exit animation finishes.
                    val displayed = if (historySummaryId != null) {
                        entries.firstOrNull { it.id == historySummaryId }
                    } else pending ?: entries.firstOrNull { it.id == mutableSummary.value?.id }
                    if (displayed != null) mutableSummary.value = displayed
                    val runningChanged = status.isRunning != wasRunning
                    val pendingChanged = pending?.id != pendingId
                    wasRunning = status.isRunning
                    pendingId = pending?.id
                    if (runningChanged ||
                        (pendingChanged && Destination.History !in mutableBackStack.value) ||
                        mutableBackStack.value.isEmpty()
                    ) {
                        historySummaryId = null
                        if (pending != null) mutableSummary.value = pending
                        mutableBackStack.value = if (pending != null) {
                            listOf(Destination.Config, Destination.EndSummary)
                        } else sessionStack(status.isRunning)
                    }
                }
        }
    }

    fun openHistorySummary(entry: ChargingHistoryEntry) {
        if (mutableBackStack.value.lastOrNull() != Destination.History) return
        historySummaryId = entry.id
        mutableSummary.value = entry
        navigate(Destination.EndSummary)
    }

    fun navigate(destination: Destination) {
        if (mutableBackStack.value.isNotEmpty() && mutableBackStack.value.last() != destination) {
            mutableBackStack.value += destination
        }
    }

    fun onBack(): Boolean {
        val stack = mutableBackStack.value
        if (stack.size <= 1) return false
        if (stack.last() == Destination.EndSummary && historySummaryId == null) {
            val entry = mutableSummary.value ?: return false
            viewModelScope.launch { history.reviewSession(entry.id, entry.actualPercent, reviewed = true) }
            return true
        }
        historySummaryId = null
        mutableBackStack.value = stack.dropLast(1)
        return true
    }

    private fun sessionStack(isRunning: Boolean): List<Destination> =
        if (isRunning) listOf(Destination.Config, Destination.Running)
        else listOf(Destination.Config)
}
