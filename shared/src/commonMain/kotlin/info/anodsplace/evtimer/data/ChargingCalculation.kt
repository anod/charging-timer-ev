package info.anodsplace.evtimer.data

data class ChargingCalculation(
    val timeRemainingMinutes: Int = 0,
    val estimatedPercent: Float = 0f,
    val chargingSpeed: Float = 0f // kW
)
