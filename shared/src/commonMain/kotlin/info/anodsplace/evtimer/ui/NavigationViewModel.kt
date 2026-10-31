package info.anodsplace.evtimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.anodsplace.evtimer.data.ChargingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class Destination {
    Config, Running, EndSummary, History, Settings, PresetEditor
}

class NavigationViewModel(service: ChargingService) : ViewModel() {
    private var wasRunning: Boolean? = service.status.value.takeUnless { it.isLoading }?.isRunning
    private val mutableBackStack = MutableStateFlow(wasRunning?.let(::sessionStack) ?: emptyList())
    val backStack = mutableBackStack.asStateFlow()

    init {
        viewModelScope.launch {
            service.status.map { if (it.isLoading) null else it.isRunning }.distinctUntilChanged().collect { isRunning ->
                if (isRunning == null) return@collect
                if (isRunning != wasRunning) {
                    wasRunning = isRunning
                    mutableBackStack.value = sessionStack(isRunning)
                }
            }
        }
    }

    fun navigate(destination: Destination) {
        if (mutableBackStack.value.isNotEmpty() && mutableBackStack.value.last() != destination) {
            mutableBackStack.value += destination
        }
    }

    fun onBack(): Boolean {
        val stack = mutableBackStack.value
        if (stack.size <= 1) return false
        mutableBackStack.value = stack.dropLast(1)
        return true
    }

    private fun sessionStack(isRunning: Boolean): List<Destination> =
        if (isRunning) listOf(Destination.Config, Destination.Running)
        // Switch the stopped destination to EndSummary when its screen is implemented (#23).
        else listOf(Destination.Config)
}
