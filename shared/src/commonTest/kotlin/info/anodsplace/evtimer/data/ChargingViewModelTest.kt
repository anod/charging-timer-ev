package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingViewModelTest {
    @Test
    fun formatsEtaUpdatesAndClearsItWhenStopped() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository()
        val service = ViewModelTestService()
        val formattedTimes = mutableListOf<Long>()
        var formattedTime = "15:40"
        val viewModel = ChargingViewModel(repository, service, ClockTimeFormatter {
            formattedTimes += it
            formattedTime
        })
        try {
            runCurrent()
            assertEquals("", viewModel.viewState.estimatedEndTimeText)
            assertEquals(emptyList(), formattedTimes)

            service.status.value = ChargingStatus(isRunning = true, estimatedEndTime = 60_000L)
            runCurrent()
            assertEquals("15:40", viewModel.viewState.estimatedEndTimeText)
            assertEquals(60_000L, viewModel.viewState.estimatedEndTime)
            assertEquals(listOf(60_000L), formattedTimes)

            formattedTime = "4:05 PM"
            service.status.value = service.status.value.copy(estimatedEndTime = 120_000L)
            runCurrent()
            assertEquals("4:05 PM", viewModel.viewState.estimatedEndTimeText)
            assertEquals(listOf(60_000L, 120_000L), formattedTimes)

            service.status.value = ChargingStatus()
            runCurrent()
            assertEquals("", viewModel.viewState.estimatedEndTimeText)
            assertEquals(listOf(60_000L, 120_000L), formattedTimes)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}

private class ViewModelTestRepository : ChargingRepository {
    private val settings = MutableStateFlow(ChargingSettings())
    private val session = MutableStateFlow(ChargingSession())

    override fun observeSettings() = settings
    override fun observeSession() = session
    override suspend fun saveSettings(settings: ChargingSettings) {
        this.settings.value = settings
    }
    override suspend fun saveSession(session: ChargingSession) {
        this.session.value = session
    }
}

private class ViewModelTestService : ChargingService {
    override val status = MutableStateFlow(ChargingStatus())
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun refresh() = Unit
}
