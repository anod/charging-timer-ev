package info.anodsplace.evtimer.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ChargingViewModel(
    private val repository: ChargingRepository
) {
    var settings by mutableStateOf(ChargingSettings())
        private set
    
    var state by mutableStateOf(ChargingState())
        private set
    
    var calculation by mutableStateOf(
        ChargingCalculator.getChargingCalculation(settings, state)
    )
        private set
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        repository.loadSettings { loaded ->
            settings = loaded
            updateCalculation()
        }
    }
    
    fun updateBatteryCapacity(capacity: Float) {
        settings = settings.copy(batteryCapacity = capacity)
        saveSettings()
        updateCalculation()
    }
    
    fun updateChargingPower(power: Float) {
        settings = settings.copy(chargingPower = power)
        saveSettings()
        updateCalculation()
    }
    
    fun addCustomPower(power: Float) {
        val newPowers = settings.availablePowers.toMutableList()
        if (!newPowers.contains(power)) {
            newPowers.add(power)
            newPowers.sort()
            settings = settings.copy(availablePowers = newPowers)
            saveSettings()
        }
    }
    
    fun updateStartPercent(percent: Float) {
        settings = settings.copy(startPercent = percent)
        saveSettings()
        updateCalculation()
    }
    
    fun updateMaxPercent(percent: Float) {
        settings = settings.copy(maxPercent = percent)
        saveSettings()
        updateCalculation()
    }
    
    @OptIn(ExperimentalTime::class)
    fun startCharging() {
        val now = Clock.System.now().toEpochMilliseconds()
        val totalMinutes = ChargingCalculator.calculateChargingTime(
            settings.batteryCapacity,
            settings.startPercent,
            settings.maxPercent,
            settings.chargingPower
        )
        state = ChargingState(
            isRunning = true,
            startTime = now,
            currentPercent = settings.startPercent,
            estimatedEndTime = ChargingCalculator.estimateEndTime(now, totalMinutes)
        )
        updateCalculation()
    }
    
    fun stopCharging() {
        state = ChargingState(isRunning = false)
        updateCalculation()
    }
    
    fun updateCalculation() {
        calculation = ChargingCalculator.getChargingCalculation(settings, state)
    }
    
    private fun saveSettings() {
        repository.saveSettings(settings)
    }
}
