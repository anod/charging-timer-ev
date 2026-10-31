package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

data class ConfigurationViewState(
    val isLoading: Boolean = true,
    val settings: ChargingSettings = ChargingSettings()
)

sealed interface ConfigurationViewAction {
    data object OpenRunning : ConfigurationViewAction
}

sealed interface ConfigurationViewEvent {
    data object StartCharging : ConfigurationViewEvent
    data class UpdateBatteryCapacity(val capacity: Float) : ConfigurationViewEvent
    data class UpdateChargingPower(val power: Float) : ConfigurationViewEvent
    data class AddCustomPower(val power: Float) : ConfigurationViewEvent
    data class UpdateStartPercent(val percent: Float) : ConfigurationViewEvent
    data class UpdateMaxPercent(val percent: Float) : ConfigurationViewEvent
}

class ConfigurationViewModel(
    private val repository: ChargingRepository,
    private val service: ChargingService
) : BaseFlowViewModel<ConfigurationViewState, ConfigurationViewEvent, ConfigurationViewAction>() {
    private var latestSettings = ChargingSettings.DEFAULT

    init {
        viewState = ConfigurationViewState()
        viewModelScope.launch {
            combine(repository.observeSettings(), service.status) { settings, status -> settings to status }
                .collect { (settings, status) ->
                    if (status.isLoading) return@collect
                    latestSettings = settings
                    viewState = ConfigurationViewState(isLoading = false, settings = settings)
                }
        }
    }

    override fun handleEvent(event: ConfigurationViewEvent) {
        if (viewState.isLoading) return
        when (event) {
            ConfigurationViewEvent.StartCharging -> viewModelScope.launch {
                service.start()
                emitAction(ConfigurationViewAction.OpenRunning)
            }
            is ConfigurationViewEvent.UpdateBatteryCapacity ->
                saveSettings(latestSettings.copy(batteryCapacity = event.capacity))
            is ConfigurationViewEvent.UpdateChargingPower ->
                saveSettings(latestSettings.copy(chargingPower = event.power))
            is ConfigurationViewEvent.AddCustomPower -> {
                if (event.power.isFinite() && event.power > 0f) {
                    saveSettings(latestSettings.withAddedPower(event.power))
                } else println("Charging settings: rejecting invalid custom power")
            }
            is ConfigurationViewEvent.UpdateStartPercent ->
                saveSettings(latestSettings.copy(startPercent = event.percent))
            is ConfigurationViewEvent.UpdateMaxPercent ->
                saveSettings(latestSettings.copy(maxPercent = event.percent))
        }
    }

    private fun saveSettings(settings: ChargingSettings) {
        viewModelScope.launch { repository.saveSettings(settings) }
    }
}
