package com.anod.chargingtimer.data

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

object ChargingCalculator {
    
    fun calculateChargingTime(
        batteryCapacity: Float,
        startPercent: Float,
        maxPercent: Float,
        chargingPower: Float
    ): Int {
        val percentToCharge = maxPercent - startPercent
        val energyNeeded = (batteryCapacity * percentToCharge) / 100f
        val hours = energyNeeded / chargingPower
        return (hours * 60).roundToInt() // Convert to minutes
    }
    
    fun calculateCurrentPercent(
        startTime: Long,
        startPercent: Float,
        maxPercent: Float,
        totalMinutes: Int
    ): Float {
        val now = Clock.System.now().toEpochMilliseconds()
        val elapsed = ((now - startTime) / 1000 / 60).toInt() // minutes
        
        if (elapsed <= 0) return startPercent
        if (elapsed >= totalMinutes) return maxPercent
        
        val percentPerMinute = (maxPercent - startPercent) / totalMinutes
        return startPercent + (percentPerMinute * elapsed)
    }
    
    fun estimateEndTime(startTime: Long, totalMinutes: Int): Long {
        return startTime + (totalMinutes * 60 * 1000)
    }
    
    fun getChargingCalculation(
        settings: ChargingSettings,
        state: ChargingState
    ): ChargingCalculation {
        val totalMinutes = calculateChargingTime(
            settings.batteryCapacity,
            settings.startPercent,
            settings.maxPercent,
            settings.chargingPower
        )
        
        val currentPercent = if (state.isRunning) {
            calculateCurrentPercent(
                state.startTime,
                settings.startPercent,
                settings.maxPercent,
                totalMinutes
            )
        } else {
            settings.startPercent
        }
        
        val now = Clock.System.now().toEpochMilliseconds()
        val elapsed = ((now - state.startTime) / 1000 / 60).toInt()
        val remaining = (totalMinutes - elapsed).coerceAtLeast(0)
        
        return ChargingCalculation(
            timeRemainingMinutes = remaining,
            estimatedPercent = currentPercent,
            chargingSpeed = settings.chargingPower
        )
    }
}
