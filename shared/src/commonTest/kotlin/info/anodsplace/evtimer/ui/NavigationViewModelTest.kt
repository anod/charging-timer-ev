package info.anodsplace.evtimer.ui

import androidx.lifecycle.viewModelScope
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.ChargingHistoryEntry
import info.anodsplace.evtimer.data.ChargingSessionSnapshot
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.TestHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    @Test
    fun loadingNeverSeedsConfigBeforeRestoringActiveRunning() = withNavigation { navigation, service ->
        service.status.value = ChargingStatus()
        val coldStart = NavigationViewModel(service, TestHistoryRepository())
        try {
            runCurrent()
            assertEquals(emptyList(), coldStart.backStack.value)
            coldStart.navigate(Destination.Config)
            assertEquals(emptyList(), coldStart.backStack.value)
            assertFalse(coldStart.onBack())
            service.status.value = ChargingStatus(isLoading = false, isRunning = true)
            runCurrent()
            assertEquals(listOf(Destination.Config, Destination.Running), coldStart.backStack.value)
        } finally {
            coldStart.viewModelScope.cancel()
        }
    }

    @Test
    fun inactiveSessionStartsAtConfigAndRootBackIsNotConsumed() = withNavigation { navigation, _ ->
        assertEquals(listOf(Destination.Config), navigation.backStack.value)
        assertFalse(navigation.onBack())
        assertEquals(listOf(Destination.Config), navigation.backStack.value)
    }

    @Test
    fun restoredActiveSessionOpensRunningBeforeCollection() = withNavigation(active = true) { navigation, _ ->
        assertEquals(listOf(Destination.Config, Destination.Running), navigation.backStack.value)
    }

    @Test
    fun startAndStopFollowPersistedServiceState() = withNavigation { navigation, service ->
        runCurrent()
        service.status.value = ChargingStatus(isLoading = false, isRunning = true)
        runCurrent()
        assertEquals(Destination.Running, navigation.backStack.value.last())
        service.status.value = ChargingStatus(isLoading = false)
        runCurrent()
        assertEquals(listOf(Destination.Config), navigation.backStack.value)
    }

    @Test
    fun backDoesNotStopSessionAndTicksDoNotReopenRunning() = withNavigation(active = true) { navigation, service ->
        runCurrent()
        assertTrue(navigation.onBack())
        assertTrue(service.status.value.isRunning)
        service.status.value = service.status.value.copy(currentPercent = 42f)
        runCurrent()
        assertEquals(listOf(Destination.Config), navigation.backStack.value)
        navigation.navigate(Destination.Running)
        navigation.navigate(Destination.Running)
        assertEquals(listOf(Destination.Config, Destination.Running), navigation.backStack.value)
    }

    @Test
    fun futureDestinationsPopInOrderAndStoppingClearsStaleScreens() = withNavigation(active = true) { navigation, service ->
        runCurrent()
        val futureScreens = listOf(Destination.History, Destination.Settings, Destination.PresetEditor)
        futureScreens.forEach(navigation::navigate)
        futureScreens.reversed().forEach {
            assertEquals(it, navigation.backStack.value.last())
            assertTrue(navigation.onBack())
        }
        navigation.navigate(Destination.Settings)
        service.status.value = ChargingStatus(isLoading = false)
        runCurrent()
        assertEquals(listOf(Destination.Config), navigation.backStack.value)
    }

    private fun withNavigation(
        active: Boolean = false,
        block: TestScope.(NavigationViewModel, NavigationTestService) -> Unit
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val service = NavigationTestService(active)
        val navigation = NavigationViewModel(service, TestHistoryRepository())
        try {
            runCurrent()
            block(navigation, service)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }

    }

    @Test
    fun unreviewedSummarySurvivesRelaunchUntilDoneOrBack() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = ChargingHistoryEntry(
            1L, ChargingSessionSnapshot.from(ChargingSettings(), 1L), 60_001L, 21f, null, false
        )
        val history = TestHistoryRepository(listOf(entry))
        val service = NavigationTestService(false)
        var navigation = NavigationViewModel(service, history)
        try {
            runCurrent()
            assertEquals(listOf(Destination.Config, Destination.EndSummary), navigation.backStack.value)
            assertEquals(entry, navigation.summary.value)
            assertFalse(history.entries.value.single().reviewed)
            navigation.viewModelScope.cancel()
            navigation = NavigationViewModel(service, history)
            runCurrent()
            assertEquals(Destination.EndSummary, navigation.backStack.value.last())
            assertTrue(navigation.onBack())
            runCurrent()
            assertTrue(history.entries.value.single().reviewed)
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
            navigation.viewModelScope.cancel()
            navigation = NavigationViewModel(service, history)
            runCurrent()
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reviewingNewestLegacySessionDoesNotOpenOlderUnreviewedSessions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(), 1L)
        val entries = listOf(3L, 2L, 1L).map {
            ChargingHistoryEntry(it, snapshot, it * 60_000L, 21f, null, false)
        }
        val history = TestHistoryRepository(entries)
        val navigation = NavigationViewModel(NavigationTestService(false), history)
        try {
            runCurrent()
            assertEquals(3L, navigation.summary.value?.id)
            assertEquals(Destination.EndSummary, navigation.backStack.value.last())
            navigation.onBack()
            runCurrent()
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
            assertEquals(null, navigation.summary.value)
            assertEquals(listOf(true, false, false), history.entries.value.map { it.reviewed })
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun activeTimerWinsUntilSavedStopThenSummaryOpens() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val history = TestHistoryRepository()
        val service = NavigationTestService(true)
        val navigation = NavigationViewModel(service, history)
        try {
            runCurrent()
            history.entries.value = listOf(ChargingHistoryEntry(
                1L, ChargingSessionSnapshot.from(ChargingSettings(), 1L), 60_001L, null, null, false,
                interrupted = true
            ))
            runCurrent()
            assertEquals(Destination.Running, navigation.backStack.value.last())
            service.stop()
            runCurrent()
            assertEquals(Destination.EndSummary, navigation.backStack.value.last())
            assertTrue(requireNotNull(navigation.summary.value).interrupted)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stopGoesDirectlyFromRunningToSummaryInEitherHistoryEmissionOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (historyFirst in listOf(false, true)) {
                val old = ChargingHistoryEntry(
                    1L, ChargingSessionSnapshot.from(ChargingSettings(), 1L), 60_001L, 21f, null, true
                )
                val ended = ChargingHistoryEntry(
                    100_000L, ChargingSessionSnapshot.from(ChargingSettings(), 100_000L),
                    160_000L, 21f, null, false
                )
                val history = TestHistoryRepository(listOf(old))
                val service = NavigationTestService(true).apply {
                    status.value = ChargingStatus(isLoading = false, isRunning = true, startTime = ended.id)
                }
                val navigation = NavigationViewModel(service, history)
                try {
                    runCurrent()
                    assertEquals(listOf(Destination.Config, Destination.Running), navigation.backStack.value)
                    if (historyFirst) history.entries.value = listOf(ended, old)
                    else service.status.value = ChargingStatus(isLoading = false, startTime = ended.id)
                    runCurrent()
                    assertEquals(listOf(Destination.Config, Destination.Running), navigation.backStack.value)
                    if (historyFirst) service.status.value = ChargingStatus(isLoading = false, startTime = ended.id)
                    else history.entries.value = listOf(ended, old)
                    runCurrent()
                    assertEquals(listOf(Destination.Config, Destination.EndSummary), navigation.backStack.value)
                } finally {
                    navigation.viewModelScope.cancel()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class NavigationTestService(active: Boolean) : ChargingService {
    override val status = MutableStateFlow(ChargingStatus(isLoading = false, isRunning = active))
    override suspend fun start() { status.value = ChargingStatus(isLoading = false, isRunning = true) }
    override suspend fun stop() { status.value = ChargingStatus(isLoading = false) }
    override suspend fun changePower(powerKw: Float) = Unit
    override fun refresh() = Unit
}
