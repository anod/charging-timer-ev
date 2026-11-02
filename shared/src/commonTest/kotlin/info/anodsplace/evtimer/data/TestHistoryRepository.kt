package info.anodsplace.evtimer.data

import kotlinx.coroutines.flow.MutableStateFlow

class TestHistoryRepository(entries: List<ChargingHistoryEntry> = emptyList()) : ChargingHistoryRepository {
    val entries = MutableStateFlow(entries)
    override fun observeHistory() = entries
    override suspend fun deleteSession(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }
    override suspend fun reviewSession(id: Long, actualPercent: Float?, reviewed: Boolean) {
        entries.value = entries.value.map {
            if (it.id == id) it.copy(actualPercent = actualPercent, reviewed = reviewed) else it
        }
    }
}
