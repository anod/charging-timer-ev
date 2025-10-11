package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    data class StartCharging(val now: Long) : ChargingViewEvent
    data object StopCharging : ChargingViewEvent
    data object UpdateCalculation : ChargingViewEvent
    data class UpdateBatteryCapacity(val capacity: Float) : ChargingViewEvent
    data class UpdateChargingPower(val power: Float) : ChargingViewEvent
    data class AddCustomPower(val power: Float) : ChargingViewEvent
    data class UpdateStartPercent(val percent: Float) : ChargingViewEvent
    data class UpdateMaxPercent(val percent: Float) : ChargingViewEvent
}

class ChargingViewModel(
    private val repository: ChargingRepository
) : BaseFlowViewModel<ChargingViewState, ChargingViewEvent, ChargingViewAction>() {

    init {
        viewState = ChargingViewState()
        viewModelScope.launch {
            repository.observeSettings().collect {
                viewState = viewState.copy(
                    settings = it,
                    calculation = ChargingCalculator.getChargingCalculation(
                        settings = it,
                        isRunning = viewState.isRunning,
                        startTime = viewState.startTime
                    )
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

            ChargingViewEvent.UpdateCalculation -> {
                viewState = viewState.copy(
                    calculation = ChargingCalculator.getChargingCalculation(
                        settings = viewState.settings,
                        isRunning = viewState.isRunning,
                        startTime = viewState.startTime
                    )
                )
            }
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

    @OptIn(ExperimentalTime::class)
    private fun startCharging() {
        val now = Clock.System.now().toEpochMilliseconds()
        val settings = viewState.settings
        val totalMinutes = ChargingCalculator.calculateChargingTime(
            batteryCapacity = settings.batteryCapacity,
            startPercent = settings.startPercent,
            maxPercent = settings.maxPercent,
            chargingPower = settings.chargingPower
        )
        viewState = viewState.copy(
            isRunning = true,
            startTime = now,
            currentPercent = settings.startPercent,
            estimatedEndTime = ChargingCalculator.estimateEndTime(now, totalMinutes),
            calculation = ChargingCalculator.getChargingCalculation(settings, true, now)
        )
    }

    private fun stopCharging() {
        viewState = viewState.copy(
            isRunning = false,
            calculation = ChargingCalculator.getChargingCalculation(viewState.settings, false, viewState.startTime)
        )
    }


    private fun saveSettings(settings: ChargingSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }


}
