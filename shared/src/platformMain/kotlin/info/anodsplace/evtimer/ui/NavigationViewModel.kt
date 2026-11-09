package info.anodsplace.evtimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingHistoryRepository
import info.anodsplace.evtimer.data.RetryableLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Destination {
    Config, Running, EndSummary, History, Settings, ChargingPowers, Presets, Licenses, PresetEditor
}

class NavigationViewModel(
    private val service: ChargingService,
    private val history: ChargingHistoryRepository
) : ViewModel() {
    private var wasRunning: Boolean? = service.status.value.takeUnless { it.isLoading }?.isRunning
    private var pendingId: Long? = null
    private var historySummaryId: Long? = null
    private var settingsTwoPane = false
    private val mutableSummary = MutableStateFlow<ChargingHistoryEntry?>(null)
    val summary = mutableSummary.asStateFlow()
    private val mutableBackStack = MutableStateFlow(if (wasRunning == true) sessionStack(true) else emptyList())
    val backStack = mutableBackStack.asStateFlow()
    private val mutableLoadFailed = MutableStateFlow(false)
    val loadFailed = combine(mutableLoadFailed, service.status) { historyFailed, status ->
        historyFailed || status.loadFailed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val loader = RetryableLoad(viewModelScope, "Navigation",
        onLoading = { mutableLoadFailed.value = false },
        onError = { mutableLoadFailed.value = true }
    ) {
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

    init { loader.retry() }

    fun retryLoad() {
        if (service.status.value.loadFailed) service.retryLoad()
        loader.retry()
    }

    fun openHistorySummary(entry: ChargingHistoryEntry) {
        if (mutableBackStack.value.lastOrNull() != Destination.History) return
        historySummaryId = entry.id
        mutableSummary.value = entry
        navigate(Destination.EndSummary)
    }

    fun setSettingsTwoPane(twoPane: Boolean) {
        settingsTwoPane = twoPane
    }

    fun onDesktopBack(): Boolean = onBack(settingsTwoPane)

    fun navigate(destination: Destination) {
        if (mutableBackStack.value.isNotEmpty() && mutableBackStack.value.last() != destination) {
            mutableBackStack.value += destination
        }
    }

    fun onBack(twoPane: Boolean = false): Boolean {
        val stack = mutableBackStack.value
        if (stack.size <= 1) return false
        if (stack.last() == Destination.EndSummary && historySummaryId == null) {
            val entry = mutableSummary.value ?: return false
            viewModelScope.launch { history.acknowledgeSession(entry.id) }
            return true
        }
        if (stack.last() == Destination.EndSummary) historySummaryId = null
        val exitsSettings = twoPane &&
            stack.last().isSettingsTopLevelDetail() &&
            stack.getOrNull(stack.lastIndex - 1) == Destination.Settings
        mutableBackStack.value = stack.dropLast(if (exitsSettings) 2 else 1)
        return true
    }

    private fun sessionStack(isRunning: Boolean): List<Destination> =
        if (isRunning) listOf(Destination.Config, Destination.Running)
        else listOf(Destination.Config)

    suspend fun saveReading(id: Long, actualPercent: Float?, calibrationEnabled: Boolean) {
        history.saveReading(id, actualPercent, calibrationEnabled)
    }

    private fun Destination.isSettingsTopLevelDetail(): Boolean = when (this) {
        Destination.ChargingPowers, Destination.Presets, Destination.History, Destination.Licenses -> true
        else -> false
    }
}
