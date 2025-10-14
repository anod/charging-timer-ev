package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ChargingViewState(
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val currentPercent: Float = 0f,
    val estimatedEndTime: Long = 0L,
    val settings: ChargingSettings = ChargingSettings(),
    val calculation: ChargingCalculation = ChargingCalculation()
)

data class ChargingCalculation(
    val timeRemainingMinutes: Int = 0,
    val estimatedPercent: Float = 0f,
    val chargingSpeed: Float = 0f // kW
)

sealed interface ChargingViewAction

sealed interface ChargingViewEvent {
    data object StartCharging : ChargingViewEvent
    data object StopCharging : ChargingViewEvent
    data object UpdateCalculation : ChargingViewEvent
    data class UpdateBatteryCapacity(val capacity: Float) : ChargingViewEvent
    data class UpdateChargingPower(val power: Float) : ChargingViewEvent
    data class AddCustomPower(val power: Float) : ChargingViewEvent
    data class UpdateStartPercent(val percent: Float) : ChargingViewEvent
    data class UpdateMaxPercent(val percent: Float) : ChargingViewEvent
}

class ChargingViewModel(
    private val repository: ChargingRepository,
    private val service: ChargingService
) : BaseFlowViewModel<ChargingViewState, ChargingViewEvent, ChargingViewAction>() {

    init {
        viewState = ChargingViewState()
        // Combine domain status with current settings to expose full UI state
        viewModelScope.launch {
            combine(
                repository.observeSettings(),
                service.status
            ) { settings, status -> settings to status }
                .collect { (settings, status) ->
                    viewState = ChargingViewState(
                        isRunning = status.isRunning,
                        startTime = status.startTime,
                        currentPercent = status.currentPercent,
                        estimatedEndTime = status.estimatedEndTime,
                        settings = settings,
                        calculation = status.calculation
                    )
                }
        }
    }

    override fun handleEvent(event: ChargingViewEvent) {
        when (event) {
            is ChargingViewEvent.StartCharging -> startCharging()
            is ChargingViewEvent.StopCharging -> stopCharging()
            is ChargingViewEvent.UpdateBatteryCapacity -> saveSettings(
                viewState.settings.copy(
                    batteryCapacity = event.capacity
                )
            )
            is ChargingViewEvent.UpdateChargingPower -> saveSettings(
                viewState.settings.copy(
                    chargingPower = event.power
                )
            )
            is ChargingViewEvent.AddCustomPower -> addCustomPower(event.power)
            is ChargingViewEvent.UpdateStartPercent -> saveSettings(
                viewState.settings.copy(
                    startPercent = event.percent
                )
            )
            is ChargingViewEvent.UpdateMaxPercent -> saveSettings(
                viewState.settings.copy(
                    maxPercent = event.percent
                )
            )
            ChargingViewEvent.UpdateCalculation -> service.refresh()
        }
    }

    private fun addCustomPower(power: Float) {
        val newPowers = viewState.settings.availablePowers.toMutableList()
        if (!newPowers.contains(power)) {
            newPowers.add(power)
            newPowers.sort()
            saveSettings(viewState.settings.copy(availablePowers = newPowers))
        }
    }

    private fun startCharging() {
        viewModelScope.launch { service.start() }
    }

    private fun stopCharging() {
        viewModelScope.launch { service.stop() }
    }

    private fun saveSettings(settings: ChargingSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }
}
