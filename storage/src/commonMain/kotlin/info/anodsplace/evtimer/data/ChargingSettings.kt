package info.anodsplace.evtimer.data

data class ChargingSettings(
    val batteryCapacity: Float = DEFAULT.batteryCapacity, // kWh
    val chargingPower: Float = DEFAULT.chargingPower, // kW
    val startPercent: Float = DEFAULT.startPercent,
    val maxPercent: Float = DEFAULT.maxPercent,
    val availablePowers: List<Float> = DEFAULT.availablePowers,
    val taperStartPercent: Float = DEFAULT.taperStartPercent,
    val taperEndPercent: Float = DEFAULT.taperEndPercent,
    val taperPowerCapKw: Float = DEFAULT.taperPowerCapKw
) {
    init {
        require(taperStartPercent.isFinite() && taperStartPercent in 0f..100f)
        require(taperEndPercent.isFinite() && taperEndPercent in 0f..100f)
        require(taperStartPercent < taperEndPercent)
        require(taperPowerCapKw.isFinite() && taperPowerCapKw > 0f)
    }

    companion object {
        val DEFAULT = ChargingSettings(
            batteryCapacity = 83f,
            chargingPower = 11f,
            startPercent = 60f,
            maxPercent = 100f,
            availablePowers = listOf(3.6f, 7f, 11f, 22f),
            taperStartPercent = 80f,
            taperEndPercent = 100f,
            taperPowerCapKw = 22f
        )
    }

    fun withAddedPower(power: Float): ChargingSettings = copy(
        chargingPower = power,
        availablePowers = (availablePowers + power).distinct().sorted()
    )

    fun withRestoredPower(power: Float, wasSelected: Boolean): ChargingSettings = copy(
        chargingPower = if (wasSelected) power else chargingPower,
        availablePowers = (availablePowers + power).distinct().sorted()
    )

    fun withRemovedPower(power: Float): ChargingSettings {
        require(power in availablePowers) { "Unknown power option" }
        val remaining = availablePowers.filterNot { it == power }
        require(remaining.isNotEmpty()) { "At least one power option is required" }
        return copy(
            availablePowers = remaining,
            chargingPower = if (chargingPower == power) remaining.first() else chargingPower
        )
    }
}