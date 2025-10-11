package info.anodsplace.evtimer.data

interface ChargingRepository {
    fun saveSettings(settings: ChargingSettings)
    fun loadSettings(onLoaded: (ChargingSettings) -> Unit)
}

