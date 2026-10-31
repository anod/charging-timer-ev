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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingViewModelTest {
    @Test
    fun ignoresDefaultsAndEventsWhileSessionIsLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository(ChargingSettings(75f, 7f, 30f, 90f))
        val service = ViewModelTestService().apply { status.value = ChargingStatus() }
        val viewModel = ChargingViewModel(repository, service, ClockTimeFormatter { "" })
        try {
            runCurrent()
            assertTrue(viewModel.viewState.isLoading)
            viewModel.handleEvent(ChargingViewEvent.UpdateChargingPower(12f))
            runCurrent()
            assertEquals(emptyList(), repository.savedSettings)
            service.status.value = ChargingStatus(isLoading = false)
            runCurrent()
            assertFalse(viewModel.viewState.isLoading)
            assertEquals(75f, viewModel.viewState.settings.batteryCapacity)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun runningScreenSettingsComeFromSnapshotUntilStopped() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository()
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(60f, 12f, 20f, 80f), 123L)
        val service = ViewModelTestService().apply {
            status.value = ChargingStatus(
                isLoading = false, isRunning = true, snapshot = snapshot,
                calculation = ChargingCalculation(chargingSpeed = 12f), elapsedMinutes = 30
            )
        }
        val viewModel = ChargingViewModel(repository, service, ClockTimeFormatter { "" })
        try {
            runCurrent()
            repository.saveSettings(ChargingSettings(100f, 50f, 40f, 90f))
            runCurrent()
            assertEquals(60f, viewModel.viewState.settings.batteryCapacity)
            assertEquals(12f, viewModel.viewState.settings.chargingPower)
            assertEquals(20f, viewModel.viewState.settings.startPercent)
            assertEquals(80f, viewModel.viewState.settings.maxPercent)
            assertEquals(30, viewModel.viewState.elapsedMinutes)
            viewModel.handleEvent(ChargingViewEvent.UpdateChargingPower(22f))
            runCurrent()
            assertEquals(100f, repository.savedSettings.last().batteryCapacity)
            assertEquals(40f, repository.savedSettings.last().startPercent)
            assertEquals(90f, repository.savedSettings.last().maxPercent)
            service.status.value = ChargingStatus(isLoading = false)
            runCurrent()
            assertEquals(100f, viewModel.viewState.settings.batteryCapacity)
            assertEquals(22f, viewModel.viewState.settings.chargingPower)
            assertEquals(90f, viewModel.viewState.settings.maxPercent)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

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

            service.status.value = ChargingStatus(isLoading = false, isRunning = true, estimatedEndTime = 60_000L)
            runCurrent()
            assertEquals("15:40", viewModel.viewState.estimatedEndTimeText)
            assertEquals(60_000L, viewModel.viewState.estimatedEndTime)
            assertEquals(listOf(60_000L), formattedTimes)

            formattedTime = "4:05 PM"
            service.status.value = service.status.value.copy(estimatedEndTime = 120_000L)
            runCurrent()
            assertEquals("4:05 PM", viewModel.viewState.estimatedEndTimeText)
            assertEquals(listOf(60_000L, 120_000L), formattedTimes)

            service.status.value = ChargingStatus(isLoading = false)
            runCurrent()
            assertEquals("", viewModel.viewState.estimatedEndTimeText)
            assertEquals(listOf(60_000L, 120_000L), formattedTimes)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun addingNewPowerSavesAndSelectsItInOneWrite() = verifyAddedPower(
        power = 7.2f,
        expectedPowers = listOf(3.6f, 7f, 7.2f, 11f)
    )

    @Test
    fun addingExistingPowerSavesAndSelectsItInOneWrite() = verifyAddedPower(
        power = 11f,
        expectedPowers = listOf(3.6f, 7f, 11f)
    )

    private fun verifyAddedPower(power: Float, expectedPowers: List<Float>) = runTest {
        val settings = ChargingSettings(availablePowers = listOf(3.6f, 7f, 11f))
        val repository = ViewModelTestRepository(settings)
        val service = ViewModelTestService()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ChargingViewModel(repository, service, ClockTimeFormatter { "" })

        try {
            runCurrent()

            viewModel.handleEvent(ChargingViewEvent.AddCustomPower(power))
            runCurrent()

            val expected = settings.copy(chargingPower = power, availablePowers = expectedPowers)
            assertEquals(listOf(expected), repository.savedSettings)
            assertEquals(expected, viewModel.viewState.settings)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}

private class ViewModelTestRepository(initialSettings: ChargingSettings = ChargingSettings()) : ChargingRepository {
    private val settings = MutableStateFlow(initialSettings)
    private val session = MutableStateFlow(ChargingSession())
    val savedSettings = mutableListOf<ChargingSettings>()

    override fun observeSettings() = settings
    override fun observeSession() = session
    override suspend fun saveSettings(settings: ChargingSettings) {
        savedSettings.add(settings)
        this.settings.value = settings
    }
    override suspend fun saveSession(session: ChargingSession) {
        this.session.value = session
    }
}

private class ViewModelTestService : ChargingService {
    override val status = MutableStateFlow(ChargingStatus(isLoading = false))
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun refresh() = Unit
}
