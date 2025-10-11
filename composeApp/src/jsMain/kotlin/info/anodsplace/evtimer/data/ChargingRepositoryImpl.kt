package info.anodsplace.evtimer.data

import kotlinx.browser.window

actual class ChargingRepositoryImpl : ChargingRepository {
    private val storage = window.localStorage

    private val keyBatteryCapacity = "battery_capacity"
    private val keyChargingPower = "charging_power"
    private val keyStartPercent = "start_percent"
    private val keyMaxPercent = "max_percent"
    private val keyAvailablePowers = "available_powers"

    override fun saveSettings(settings: ChargingSettings) {
        storage.setItem(keyBatteryCapacity, settings.batteryCapacity.toString())
        storage.setItem(keyChargingPower, settings.chargingPower.toString())
        storage.setItem(keyStartPercent, settings.startPercent.toString())
        storage.setItem(keyMaxPercent, settings.maxPercent.toString())
        storage.setItem(keyAvailablePowers, settings.availablePowers.joinToString(","))
    }

    override fun loadSettings(onLoaded: (ChargingSettings) -> Unit) {
        val settings = ChargingSettings(
            batteryCapacity = storage.getItem(keyBatteryCapacity)?.toFloatOrNull() ?: 60f,
            chargingPower = storage.getItem(keyChargingPower)?.toFloatOrNull() ?: 7f,
            startPercent = storage.getItem(keyStartPercent)?.toFloatOrNull() ?: 20f,
            maxPercent = storage.getItem(keyMaxPercent)?.toFloatOrNull() ?: 80f,
            availablePowers = storage.getItem(keyAvailablePowers)
                ?.split(",")
                ?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(3.6f, 7f, 11f, 22f)
        )
        onLoaded(settings)
    }
}

