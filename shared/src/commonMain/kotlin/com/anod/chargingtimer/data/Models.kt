package com.anod.chargingtimer.data

data class ChargingSettings(
    val batteryCapacity: Float = 60f, // kWh
    val chargingPower: Float = 7f, // kW
    val startPercent: Float = 20f,
    val maxPercent: Float = 80f,
    val availablePowers: List<Float> = listOf(3.6f, 7f, 11f, 22f)
)

data class ChargingState(
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val currentPercent: Float = 0f,
    val estimatedEndTime: Long = 0L
)

data class ChargingCalculation(
    val timeRemainingMinutes: Int,
    val estimatedPercent: Float,
    val chargingSpeed: Float // kW
)
