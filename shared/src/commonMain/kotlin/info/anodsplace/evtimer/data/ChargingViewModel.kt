package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ChargingViewState(
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val currentPercent: Float = 0f,
    val estimatedEndTime: Long = 0L,
    val estimatedEndTimeText: String = "",
    val settings: ChargingSettings = ChargingSettings(),
    val calculation: ChargingCalculation = ChargingCalculation(),
    val elapsedMinutes: Int = 0
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
    private val service: ChargingService,
    private val clockTimeFormatter: ClockTimeFormatter
) : BaseFlowViewModel<ChargingViewState, ChargingViewEvent, ChargingViewAction>() {
    private var latestSettings = ChargingSettings.DEFAULT

    init {
        viewState = ChargingViewState()
        // Combine domain status with current settings to expose full UI state
        viewModelScope.launch {
            combine(
                repository.observeSettings(),
                service.status
            ) { settings, status -> settings to status }
                .collect { (settings, status) ->
                    if (status.isLoading) return@collect
                    latestSettings = settings
                    val sessionSettings = status.snapshot?.let {
                        settings.copy(
                            batteryCapacity = it.batteryCapacity,
                            startPercent = it.startPercent,
                            maxPercent = it.targetPercent,
                            chargingPower = status.calculation.chargingSpeed
                        )
                    } ?: settings
                    viewState = ChargingViewState(
                        isLoading = false,
                        isRunning = status.isRunning,
                        startTime = status.startTime,
                        currentPercent = status.currentPercent,
                        estimatedEndTime = status.estimatedEndTime,
                        estimatedEndTimeText = if (status.estimatedEndTime > 0L) {
                            clockTimeFormatter.format(status.estimatedEndTime)
                        } else "",
                        settings = sessionSettings,
                        calculation = status.calculation,
                        elapsedMinutes = status.elapsedMinutes
                    )
                }
        }
    }

    override fun handleEvent(event: ChargingViewEvent) {
        if (viewState.isLoading) return
        when (event) {
            is ChargingViewEvent.StartCharging -> startCharging()
            is ChargingViewEvent.StopCharging -> stopCharging()
            is ChargingViewEvent.UpdateBatteryCapacity -> saveSettings(
                latestSettings.copy(
                    batteryCapacity = event.capacity
                )
            )
            is ChargingViewEvent.UpdateChargingPower -> saveSettings(
                latestSettings.copy(
                    chargingPower = event.power
                )
            )
            is ChargingViewEvent.AddCustomPower -> addCustomPower(event.power)
            is ChargingViewEvent.UpdateStartPercent -> saveSettings(
                latestSettings.copy(
                    startPercent = event.percent
                )
            )
            is ChargingViewEvent.UpdateMaxPercent -> saveSettings(
                latestSettings.copy(
                    maxPercent = event.percent
                )
            )
            ChargingViewEvent.UpdateCalculation -> service.refresh()
        }
    }

    private fun addCustomPower(power: Float) {
        saveSettings(latestSettings.withAddedPower(power))
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
