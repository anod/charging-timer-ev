package info.anodsplace.evtimer.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
    fun configurationAndRunningRetryFailedStorageSubscriptions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = ViewModelTestRepository()
        var settingsFailures = 1
        var configurationFailures = 1
        val repository = object : ChargingRepository by base {
            override fun observeSettings() = flow {
                if (settingsFailures-- > 0) error("settings unavailable")
                emitAll(base.observeSettings())
            }
        }
        val presets = object : PresetRepository by base {
            override fun observeConfiguration() = flow {
                if (configurationFailures-- > 0) error("configuration unavailable")
                emitAll(base.observeConfiguration())
            }
        }
        val service = ViewModelTestService()
        val config = ConfigurationViewModel(repository, service, presets)
        val running = RunningViewModel(repository, service, ClockTimeFormatter { "" })
        try {
            runCurrent()
            assertTrue(config.viewState.loadFailed)
            assertTrue(running.viewState.loadFailed)
            config.retryLoad()
            running.retryLoad()
            assertTrue(config.viewState.isLoading)
            assertTrue(running.viewState.isLoading)
            runCurrent()
            assertFalse(config.viewState.isLoading)
            assertFalse(running.viewState.isLoading)
        } finally {
            config.viewModelScope.cancel()
            running.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun chargingPowersRetryFailedSettingsSubscription() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = ViewModelTestRepository()
        var failures = 1
        val repository = object : ChargingRepository by base {
            override fun observeSettings() = flow {
                if (failures-- > 0) error("settings unavailable")
                emitAll(base.observeSettings())
            }
        }
        val viewModel = SettingsViewModel(repository, base, base)
        try {
            runCurrent()
            assertTrue(viewModel.settingsLoadFailed.value)
            viewModel.retrySettingsLoad()
            assertFalse(viewModel.settingsLoadFailed.value)
            runCurrent()
            assertEquals(ChargingSettings(), viewModel.settings.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun settingsRetryRestartsOtherEagerStorageObservers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = ViewModelTestRepository()
        var failures = 1
        val repository = object : ChargingRepository by base {
            override fun observeNotifyAt80() = flow {
                if (failures-- > 0) error("notification settings unavailable")
                emitAll(base.observeNotifyAt80())
            }
        }
        val viewModel = SettingsViewModel(repository, base, base)
        try {
            runCurrent()
            assertTrue(viewModel.settingsLoadFailed.value)
            viewModel.retrySettingsLoad()
            runCurrent()
            assertFalse(viewModel.settingsLoadFailed.value)
            assertEquals(true, viewModel.notifyAt80.value)
            assertEquals(ChargingSettings(), viewModel.settings.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun settingsPowerEditsSelectAddedPowerAndGuardMissingAndLastOptions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = ChargingSettings.DEFAULT.copy(availablePowers = listOf(7f, 11f))
        val repository = ViewModelTestRepository(initial)
        val viewModel = SettingsViewModel(repository, repository, repository)
        try {
            runCurrent()
            assertEquals(initial, viewModel.settings.value)
            viewModel.removePower(7f)
            runCurrent()
            assertEquals(initial.copy(availablePowers = listOf(11f)), viewModel.settings.value)
            viewModel.removePower(11f)
            viewModel.removePower(50f)
            listOf(0f, -7f, Float.NaN, Float.POSITIVE_INFINITY).forEach(viewModel::addPower)
            runCurrent()
            assertEquals(1, repository.savedSettings.size)
            viewModel.addPower(50f)
            runCurrent()
            assertEquals(listOf(11f, 50f), viewModel.settings.value!!.availablePowers)
            assertEquals(50f, viewModel.settings.value!!.chargingPower)
            viewModel.addPower(11f)
            runCurrent()
            assertEquals(11f, viewModel.settings.value!!.chargingPower)
            assertEquals(listOf(11f, 50f), viewModel.settings.value!!.availablePowers)
            viewModel.removePower(11f)
            runCurrent()
            assertEquals(50f, viewModel.settings.value!!.chargingPower)
            assertEquals(initial.copy(chargingPower = 50f, availablePowers = listOf(50f)),
                viewModel.settings.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rapidSettingsPowerEditsReadLatestPersistedSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository().apply { beforeSave = { yield() } }
        val viewModel = SettingsViewModel(repository, repository, repository)
        try {
            runCurrent()
            viewModel.addPower(50f)
            viewModel.addPower(100f)
            viewModel.removePower(50f)
            viewModel.removePower(11f)
            runCurrent()
            assertEquals(listOf(3.6f, 7f, 22f, 100f), viewModel.settings.value!!.availablePowers)
            assertEquals(100f, viewModel.settings.value!!.chargingPower)
            assertEquals(4, repository.savedSettings.size)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun completedPresentationSurvivesStoppedStatusAndSettingsDuringSummaryHandoff() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository(ChargingSettings(60f, 150f, 75f, 80f))
        val snapshot = ChargingSessionSnapshot.from(repository.observeSettings().value, 100_000L)
        val service = ViewModelTestService().apply {
            status.value = ChargingStatus(
                isLoading = false, isRunning = true, startTime = snapshot.startTime,
                snapshot = snapshot, estimatedEndTime = 172_000L, evaluatedAt = 172_000L,
                calculation = ChargingCalculation(0, 80f, 150f), elapsedMinutes = 1
            )
        }
        val viewModel = RunningViewModel(repository, service, ClockTimeFormatter { "ETA:$it" })
        try {
            runCurrent()
            val completed = viewModel.viewState
            assertTrue(completed.isFinished)
            assertEquals(80f, completed.calculation.estimatedPercent)
            service.status.value = ChargingStatus(isLoading = false, startTime = snapshot.startTime)
            repository.saveSettings(ChargingSettings(100f, 7f, 20f, 90f))
            runCurrent()
            assertFalse(service.status.value.isRunning)
            assertEquals(completed, viewModel.viewState)
            assertEquals(1, viewModel.viewState.elapsedMinutes)
            assertEquals(0, viewModel.viewState.calculation.timeRemainingMinutes)
            assertEquals(80f, viewModel.viewState.calculation.estimatedPercent)
            service.status.value = ChargingStatus(
                isLoading = false, isRunning = true, startTime = 200_000L,
                snapshot = snapshot.copy(segments = listOf(PowerSegment(200_000L, 150f))),
                estimatedEndTime = 272_000L, calculation = ChargingCalculation(2, 75f, 150f)
            )
            runCurrent()
            assertEquals(200_000L, viewModel.viewState.startTime)
            assertEquals(75f, viewModel.viewState.calculation.estimatedPercent)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun powerRemovalUpdatesSelectionAndRejectsMissingAndLastOptions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = ChargingSettings.DEFAULT.copy(availablePowers = listOf(7f, 11f))
        val repo = ViewModelTestRepository(initial)
        val viewModel = ConfigurationViewModel(repo, ViewModelTestService(), repo)
        try {
            runCurrent()
            viewModel.handleEvent(ConfigurationViewEvent.RemoveCustomPower(11f))
            runCurrent()
            assertEquals(listOf(initial.withRemovedPower(11f)), repo.savedSettings)
            assertEquals(7f, viewModel.viewState.settings.chargingPower)
            viewModel.handleEvent(ConfigurationViewEvent.RemoveCustomPower(7f))
            viewModel.handleEvent(ConfigurationViewEvent.RemoveCustomPower(50f))
            runCurrent()
            assertEquals(1, repo.savedSettings.size)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

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
        val navigation = NavigationViewModel(service, TestHistoryRepository())
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
            assertEquals(before.settings, running.viewState.settings)
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
    fun runningScreenSettingsComeFromSnapshotThroughStopHandoff() = runTest {
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
            val beforeStop = viewModel.viewState
            service.status.value = ChargingStatus(isLoading = false)
            runCurrent()
            assertEquals(beforeStop, viewModel.viewState)
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
    fun formatsEtaUpdatesAndPreservesItDuringStopHandoff() = runTest {
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
            assertEquals("4:05 PM", viewModel.viewState.estimatedEndTimeText)
            assertEquals(listOf(60_000L, 120_000L), formattedTimes)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun formatsMilestoneEtaWithTheSameClockTimeFormatter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = ViewModelTestRepository()
        val service = ViewModelTestService()
        val formattedTimes = mutableListOf<Long>()
        val viewModel = RunningViewModel(repository, service, ClockTimeFormatter {
            formattedTimes += it
            "ETA:$it"
        })
        try {
            runCurrent()
            val estimate = MilestoneEstimate(minutesRemaining = 196, estimatedTime = 12_345L)
            service.status.value = ChargingStatus(
                isLoading = false,
                isRunning = true,
                estimatedEndTime = 60_000L,
                milestoneEstimate = estimate
            )
            runCurrent()

            assertEquals(estimate, viewModel.viewState.milestoneEstimate)
            assertEquals("ETA:12345", viewModel.viewState.milestoneEtaText)
            assertEquals(listOf(60_000L, 12_345L), formattedTimes)

            service.status.value = service.status.value.copy(
                milestoneEstimate = estimate.copy(minutesRemaining = 0, reached = true)
            )
            runCurrent()
            assertTrue(viewModel.viewState.milestoneEstimate!!.reached)
            assertEquals("ETA:12345", viewModel.viewState.milestoneEtaText)

            service.status.value = ChargingStatus(isLoading = false)
            runCurrent()
            assertEquals(estimate.copy(minutesRemaining = 0, reached = true), viewModel.viewState.milestoneEstimate)
            assertEquals("ETA:12345", viewModel.viewState.milestoneEtaText)
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
    fun changePowerCallsServiceAndAddPowerOnlySavesSortedUniqueOptions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = ChargingSettings(chargingPower = 7f, availablePowers = listOf(3.6f, 7f, 11f))
        val repository = ViewModelTestRepository(settings)
        val service = ViewModelTestService()
        val viewModel = RunningViewModel(repository, service, ClockTimeFormatter { "" })
        try {
            runCurrent()
            viewModel.handleEvent(RunningViewEvent.ChangePower(22f))
            runCurrent()
            assertEquals(listOf(22f), service.powerChanges)
            assertEquals(emptyList(), repository.savedSettings)

            viewModel.handleEvent(RunningViewEvent.AddPower(5f))
            runCurrent()
            viewModel.handleEvent(RunningViewEvent.AddPower(5f))
            viewModel.handleEvent(RunningViewEvent.AddPower(11f))
            viewModel.handleEvent(RunningViewEvent.AddPower(0f))
            viewModel.handleEvent(RunningViewEvent.AddPower(-3f))
            viewModel.handleEvent(RunningViewEvent.AddPower(Float.NaN))
            runCurrent()
            val expected = settings.copy(availablePowers = listOf(3.6f, 5f, 7f, 11f))
            assertEquals(listOf(expected), repository.savedSettings.takeLast(1))
            assertEquals(expected, repository.observeSettings().value)
            assertEquals(7f, repository.observeSettings().value.chargingPower)
            assertEquals(listOf(22f), service.powerChanges)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

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

private class ViewModelTestRepository(initialSettings: ChargingSettings = ChargingSettings()) :
    ChargingRepository, PresetRepository, ManualBackupRepository {
    override suspend fun exportBackup(appVersion: String): ManualBackup = error("Not used")
    override suspend fun restoreBackup(backup: ManualBackup): Unit = error("Not used")
    var beforeSave: suspend () -> Unit = {}
    private val notifyAt80 = MutableStateFlow(true)
    override fun observeNotifyAt80() = notifyAt80
    override suspend fun setNotifyAt80(enabled: Boolean) { notifyAt80.value = enabled }
    private val settings = MutableStateFlow(initialSettings)
    private val session = MutableStateFlow(ChargingSession())
    val savedSettings = mutableListOf<ChargingSettings>()

    override fun observeSettings() = settings
    override fun observeConfiguration() = settings.map {
        PresetConfiguration(listOf(ChargingPreset("custom", "Default", it)), "custom", it)
    }
    override suspend fun removePowerForUndo(power: Float): RemovedPower {
        val (updated, removed) = observeConfiguration().first().removePower(power)
        saveSettings(updated.settings)
        return removed
    }
    override suspend fun restorePower(removed: RemovedPower) {
        saveSettings(observeConfiguration().first().restorePower(removed).settings)
    }
    override fun observePresets() = observeConfiguration().map { it.presets }
    override suspend fun updateSelectedPreset() = Unit
    override suspend fun saveAsPreset(name: String) = Unit
    override suspend fun resetPreset() = Unit
    override suspend fun savePreset(preset: ChargingPreset) = Unit
    override suspend fun renamePreset(id: String, name: String) = Unit
    override suspend fun deletePreset(id: String) = Unit
    override suspend fun selectPreset(id: String) = Unit
    override suspend fun reorderPresets(ids: List<String>) { require(ids == listOf("custom")) }
    override fun observeSession() = session
    override suspend fun saveSettings(settings: ChargingSettings) {
        beforeSave()
        savedSettings.add(settings)
        this.settings.value = settings
    }
    override suspend fun saveSession(session: ChargingSession) {
        this.session.value = session
    }
}

private class ViewModelTestService : ChargingService {
    override fun retryLoad() = Unit
    var starts = 0
    var stops = 0
    var refreshes = 0
    override val status = MutableStateFlow(ChargingStatus(isLoading = false))
    override suspend fun start() { starts++ }
    override suspend fun stop() { stops++ }
    val powerChanges = mutableListOf<Float>()
    override suspend fun changePower(powerKw: Float) { powerChanges += powerKw }
    override fun refresh() { refreshes++ }
}
