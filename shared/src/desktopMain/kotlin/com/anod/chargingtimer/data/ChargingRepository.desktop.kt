package com.anod.chargingtimer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties

actual class ChargingRepositoryImpl : ChargingRepository {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private val settingsFile = File(System.getProperty("user.home"), ".charging-timer-settings.properties")
    
    override fun saveSettings(settings: ChargingSettings) {
        scope.launch {
            val props = Properties()
            props.setProperty("battery_capacity", settings.batteryCapacity.toString())
            props.setProperty("charging_power", settings.chargingPower.toString())
            props.setProperty("start_percent", settings.startPercent.toString())
            props.setProperty("max_percent", settings.maxPercent.toString())
            props.setProperty("available_powers", settings.availablePowers.joinToString(","))
            
            settingsFile.outputStream().use {
                props.store(it, "Charging Timer Settings")
            }
        }
    }
    
    override fun loadSettings(onLoaded: (ChargingSettings) -> Unit) {
        scope.launch {
            val settings = if (settingsFile.exists()) {
                val props = Properties()
                settingsFile.inputStream().use {
                    props.load(it)
                }
                ChargingSettings(
                    batteryCapacity = props.getProperty("battery_capacity")?.toFloatOrNull() ?: 60f,
                    chargingPower = props.getProperty("charging_power")?.toFloatOrNull() ?: 7f,
                    startPercent = props.getProperty("start_percent")?.toFloatOrNull() ?: 20f,
                    maxPercent = props.getProperty("max_percent")?.toFloatOrNull() ?: 80f,
                    availablePowers = props.getProperty("available_powers")?.split(",")
                        ?.mapNotNull { it.toFloatOrNull() }
                        ?: listOf(3.6f, 7f, 11f, 22f)
                )
            } else {
                ChargingSettings()
            }
            onLoaded(settings)
        }
    }
}
