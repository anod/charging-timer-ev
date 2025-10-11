package info.anodsplace.evtimer.data

import platform.Foundation.NSUserDefaults

actual class ChargingRepositoryImpl : ChargingRepository {
    private val defaults = NSUserDefaults.standardUserDefaults

    private val keyBatteryCapacity = "battery_capacity"
    private val keyChargingPower = "charging_power"
    private val keyStartPercent = "start_percent"
    private val keyMaxPercent = "max_percent"
    private val keyAvailablePowers = "available_powers"

    override fun saveSettings(settings: ChargingSettings) {
        defaults.setFloat(settings.batteryCapacity, forKey = keyBatteryCapacity)
        defaults.setFloat(settings.chargingPower, forKey = keyChargingPower)
        defaults.setFloat(settings.startPercent, forKey = keyStartPercent)
        defaults.setFloat(settings.maxPercent, forKey = keyMaxPercent)
        defaults.setObject(settings.availablePowers.joinToString(","), forKey = keyAvailablePowers)
        defaults.synchronize()
    }

    override fun loadSettings(onLoaded: (ChargingSettings) -> Unit) {
        val capacity = defaults.floatForKey(keyBatteryCapacity).takeIf { it > 0 } ?: 60f
        val power = defaults.floatForKey(keyChargingPower).takeIf { it > 0 } ?: 7f
        val start = defaults.floatForKey(keyStartPercent).takeIf { it > 0 } ?: 20f
        val max = defaults.floatForKey(keyMaxPercent).takeIf { it > 0 } ?: 80f
        val powersStr = defaults.stringForKey(keyAvailablePowers)
        val powers = powersStr?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }
            ?: listOf(3.6f, 7f, 11f, 22f)
        onLoaded(
            ChargingSettings(
                batteryCapacity = capacity,
                chargingPower = power,
                startPercent = start,
                maxPercent = max,
                availablePowers = powers
            )
        )
    }
}

