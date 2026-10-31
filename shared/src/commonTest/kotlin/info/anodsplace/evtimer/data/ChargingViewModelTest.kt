package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import info.anodsplace.evtimer.ui.NavigationViewModel
import info.anodsplace.evtimer.ui.Destination
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingViewModelTest {
    @Test
    @OptIn(ExperimentalTime::class)
    fun backThenPowerAndTargetEditsLeaveRunningPredictionsSnapshotDriven() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = ChargingSettings(60f, 12f, 20f, 80f)
        val repository = ViewModelTestRepository(initial)
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_800_000_000_000L)
        }
        val service = ChargingServiceImpl(repository, backgroundScope, clock)
        val running = RunningViewModel(repository, service, ClockTimeFormatter { "ETA:$it" })
        val config = ConfigurationViewModel(repository, service, repository)
        val navigation = NavigationViewModel(service)
        try {
            runCurrent()
            service.start()
            runCurrent()
            val before = running.viewState
            assertEquals(Destination.Running, navigation.backStack.value.last())
            assertTrue(navigation.onBack())
            assertEquals(Destination.Config, navigation.backStack.value.last())
            config.handleEvent(ConfigurationViewEvent.UpdateChargingPower(22f))
            runCurrent()
            config.handleEvent(ConfigurationViewEvent.UpdateMaxPercent(90f))
            runCurrent()

            assertTrue(service.status.value.isRunning)
            assertEquals(before.calculation.estimatedPercent, running.viewState.calculation.estimatedPercent)
            assertEquals(before.calculation.timeRemainingMinutes, running.viewState.calculation.timeRemainingMinutes)
            assertEquals(before.estimatedEndTime, running.viewState.estimatedEndTime)
            assertEquals(before.estimatedEndTimeText, running.viewState.estimatedEndTimeText)
            assertEquals(initial, running.viewState.settings)
            assertEquals(initial.copy(chargingPower = 22f, maxPercent = 90f), config.viewState.settings)
            assertEquals(Destination.Config, navigation.backStack.value.last())
            service.stop()
            runCurrent()
            assertEquals(config.viewState.settings, running.viewState.settings)
        } finally {
            running.viewModelScope.cancel()
            config.viewModelScope.cancel()
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rejectsNonFiniteCustomPowerWithoutPersistingIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = ViewModelTestRepository()
        val viewModel = ConfigurationViewModel(repo, ViewModelTestService(), repo)
        try {
            runCurrent()
            listOf(Float.POSITIVE_INFINITY, Float.NaN, 0f, -7f).forEach {
                viewModel.handleEvent(ConfigurationViewEvent.AddCustomPower(it))
            }
            runCurrent()
            assertEquals(emptyList(), repo.savedSettings)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun ignoresDefaultsAndEventsWhileSessionIsLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository(ChargingSettings(75f, 7f, 30f, 90f))
        val service = ViewModelTestService().apply { status.value = ChargingStatus() }
        val viewModel = ConfigurationViewModel(repository, service, repository)
        try {
            runCurrent()
            assertTrue(viewModel.viewState.isLoading)
            viewModel.handleEvent(ConfigurationViewEvent.UpdateChargingPower(12f))
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
        val viewModel = RunningViewModel(repository, service, ClockTimeFormatter { "" })
        val config = ConfigurationViewModel(repository, service, repository)
        try {
            runCurrent()
            repository.saveSettings(ChargingSettings(100f, 50f, 40f, 90f))
            runCurrent()
            assertEquals(60f, viewModel.viewState.settings.batteryCapacity)
            assertEquals(12f, viewModel.viewState.settings.chargingPower)
            assertEquals(20f, viewModel.viewState.settings.startPercent)
            assertEquals(80f, viewModel.viewState.settings.maxPercent)
            assertEquals(30, viewModel.viewState.elapsedMinutes)
            config.handleEvent(ConfigurationViewEvent.UpdateChargingPower(22f))
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
            config.viewModelScope.cancel()
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun configurationEventsSaveSettingsAndStartOpensRunning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository()
        val service = ViewModelTestService()
        val viewModel = ConfigurationViewModel(repository, service, repository)
        val actions = mutableListOf<ConfigurationViewAction>()
        val actionJob = backgroundScope.launch { viewModel.viewActions.collect { actions += it } }
        try {
            runCurrent()
            viewModel.handleEvent(ConfigurationViewEvent.UpdateBatteryCapacity(75f))
            runCurrent()
            viewModel.handleEvent(ConfigurationViewEvent.UpdateChargingPower(11f))
            runCurrent()
            viewModel.handleEvent(ConfigurationViewEvent.UpdateStartPercent(30f))
            runCurrent()
            viewModel.handleEvent(ConfigurationViewEvent.UpdateMaxPercent(80f))
            runCurrent()
            assertEquals(
                ChargingSettings().copy(batteryCapacity = 75f, chargingPower = 11f, startPercent = 30f, maxPercent = 80f),
                viewModel.viewState.settings
            )
            assertEquals(4, repository.savedSettings.size)
            viewModel.handleEvent(ConfigurationViewEvent.StartCharging)
            runCurrent()
            assertEquals(1, service.starts)
            assertEquals(listOf<ConfigurationViewAction>(ConfigurationViewAction.OpenRunning), actions)
        } finally {
            actionJob.cancel()
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun runningEventsOnlyRefreshAndStopTheService() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository()
        val service = ViewModelTestService()
        val viewModel = RunningViewModel(repository, service, ClockTimeFormatter { "" })
        try {
            runCurrent()
            viewModel.handleEvent(RunningViewEvent.UpdateCalculation)
            viewModel.handleEvent(RunningViewEvent.StopCharging)
            runCurrent()
            assertEquals(1, service.refreshes)
            assertEquals(1, service.stops)
            assertEquals(emptyList(), repository.savedSettings)
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
        val viewModel = RunningViewModel(repository, service, ClockTimeFormatter {
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
        val viewModel = ConfigurationViewModel(repository, service, repository)

        try {
            runCurrent()

            viewModel.handleEvent(ConfigurationViewEvent.AddCustomPower(power))
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

private class ViewModelTestRepository(initialSettings: ChargingSettings = ChargingSettings()) : ChargingRepository, PresetRepository {
    private val settings = MutableStateFlow(initialSettings)
    private val session = MutableStateFlow(ChargingSession())
    val savedSettings = mutableListOf<ChargingSettings>()

    override fun observeSettings() = settings
    override fun observeConfiguration() = settings.map {
        PresetConfiguration(listOf(ChargingPreset("custom", "Default", it)), "custom", it)
    }
    override fun observePresets() = observeConfiguration().map { it.presets }
    override suspend fun updateSelectedPreset() = Unit
    override suspend fun saveAsPreset(name: String) = Unit
    override suspend fun resetPreset() = Unit
    override suspend fun savePreset(preset: ChargingPreset) = Unit
    override suspend fun deletePreset(id: String) = Unit
    override suspend fun selectPreset(id: String) = Unit
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
    var starts = 0
    var stops = 0
    var refreshes = 0
    override val status = MutableStateFlow(ChargingStatus(isLoading = false))
    override suspend fun start() { starts++ }
    override suspend fun stop() { stops++ }
    override fun refresh() { refreshes++ }
}
