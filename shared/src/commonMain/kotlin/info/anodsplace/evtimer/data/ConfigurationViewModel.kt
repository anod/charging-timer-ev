package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import info.anodsplace.viewmodel.BaseFlowViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel

data class ConfigurationViewState(
    val isLoading: Boolean = true,
    val settings: ChargingSettings = ChargingSettings(),
    val presets: List<ChargingPreset> = listOf(ChargingPreset(ChargingRepositoryImpl.CUSTOM_ID, "Default", ChargingSettings.DEFAULT)),
    val selectedPresetId: String = ChargingRepositoryImpl.CUSTOM_ID
) {
    val presetConfiguration get() = PresetConfiguration(presets, selectedPresetId, settings)
}

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
    data class SelectPreset(val id: String) : ConfigurationViewEvent
    data object UpdatePreset : ConfigurationViewEvent
    data class SaveAsPreset(val name: String) : ConfigurationViewEvent
    data object ResetPreset : ConfigurationViewEvent
}

class ConfigurationViewModel(
    private val repository: ChargingRepository,
    private val service: ChargingService,
    private val presetRepository: PresetRepository
) : BaseFlowViewModel<ConfigurationViewState, ConfigurationViewEvent, ConfigurationViewAction>() {
    private val events = Channel<ConfigurationViewEvent>(Channel.UNLIMITED)

    init {
        viewState = ConfigurationViewState()
        viewModelScope.launch {
            combine(
                presetRepository.observeConfiguration(), service.status
            ) { configuration, status -> configuration to status }
                .collect { (configuration, status) ->
                    if (status.isLoading) return@collect
                    viewState = ConfigurationViewState(
                        isLoading = false, settings = configuration.settings,
                        presets = configuration.presets, selectedPresetId = configuration.selectedId
                    )
                }
        }
        viewModelScope.launch {
            for (event in events) processEvent(event)
        }
    }

    override fun handleEvent(event: ConfigurationViewEvent) {
        if (viewState.isLoading) return
        check(events.trySend(event).isSuccess)
    }

    private suspend fun processEvent(event: ConfigurationViewEvent) {
        val latestSettings = repository.observeSettings().first()
        when (event) {
            ConfigurationViewEvent.StartCharging -> {
                service.start()
                emitAction(ConfigurationViewAction.OpenRunning)
            }
            is ConfigurationViewEvent.SelectPreset -> presetRepository.selectPreset(event.id)
            ConfigurationViewEvent.UpdatePreset -> presetRepository.updateSelectedPreset()
            is ConfigurationViewEvent.SaveAsPreset -> presetRepository.saveAsPreset(event.name)
            ConfigurationViewEvent.ResetPreset -> presetRepository.resetPreset()
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

    private suspend fun saveSettings(settings: ChargingSettings) = repository.saveSettings(settings)
}
