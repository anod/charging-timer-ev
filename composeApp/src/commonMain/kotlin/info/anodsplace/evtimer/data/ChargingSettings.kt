package info.anodsplace.evtimer.data

data class ChargingSettings(
    val batteryCapacity: Float = 60f, // kWh
    val chargingPower: Float = 7f, // kW
    val startPercent: Float = 20f,
    val maxPercent: Float = 80f,
    val availablePowers: List<Float> = listOf(3.6f, 7f, 11f, 22f)
)