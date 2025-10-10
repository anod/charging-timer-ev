package com.anod.chargingtimer.data

interface ChargingRepository {
    fun saveSettings(settings: ChargingSettings)
    fun loadSettings(onLoaded: (ChargingSettings) -> Unit)
}

expect class ChargingRepositoryImpl() : ChargingRepository
