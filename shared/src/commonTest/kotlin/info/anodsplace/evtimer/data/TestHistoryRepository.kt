package info.anodsplace.evtimer.data

import kotlinx.coroutines.flow.MutableStateFlow

class TestHistoryRepository(entries: List<ChargingHistoryEntry> = emptyList()) : ChargingHistoryRepository {
    val entries = MutableStateFlow(entries)
    override fun observeHistory() = entries
    override suspend fun saveReading(id: Long, actualPercent: Float?, calibrationEnabled: Boolean) {
        entries.value = entries.value.map {
            if (it.id == id) it.copy(actualPercent = actualPercent,
                calibrationEnabled = actualPercent != null && calibrationEnabled) else it
        }
    }
    override suspend fun acknowledgeSession(id: Long) {
        entries.value = entries.value.map { if (it.id == id) it.copy(reviewed = true) else it }
    }
    override suspend fun deleteSession(id: Long) {
        val newest = entries.value.firstOrNull()?.id == id
        entries.value = entries.value.filterNot { it.id == id }.mapIndexed { index, entry ->
            if (newest && index == 0) entry.copy(reviewed = true) else entry
        }
    }
    override suspend fun reviewSession(id: Long, actualPercent: Float?, reviewed: Boolean) {
        entries.value = entries.value.map {
            if (it.id == id) it.copy(actualPercent = actualPercent, reviewed = reviewed) else it
        }
    }
}
