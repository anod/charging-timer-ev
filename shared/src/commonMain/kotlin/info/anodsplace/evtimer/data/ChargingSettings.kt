package info.anodsplace.evtimer.data

data class ChargingSettings(
    val batteryCapacity: Float = DEFAULT.batteryCapacity, // kWh
    val chargingPower: Float = DEFAULT.chargingPower, // kW
    val startPercent: Float = DEFAULT.startPercent,
    val maxPercent: Float = DEFAULT.maxPercent,
    val availablePowers: List<Float> = DEFAULT.availablePowers
) {
    companion object {
        val DEFAULT = ChargingSettings(
            batteryCapacity = 83f,
            chargingPower = 11f,
            startPercent = 60f,
            maxPercent = 100f,
            availablePowers = listOf(3.6f, 7f, 11f, 22f)
        )
    }

    fun withAddedPower(power: Float): ChargingSettings = copy(
        chargingPower = power,
        availablePowers = (availablePowers + power).distinct().sorted()
    )
}