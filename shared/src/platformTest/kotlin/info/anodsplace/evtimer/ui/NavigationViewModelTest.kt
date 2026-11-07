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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    @Test
    fun failedHistoryLoadCanBeRetriedAtStartup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val base = TestHistoryRepository()
        var failures = 1
        val history = object : info.anodsplace.evtimer.data.ChargingHistoryRepository by base {
            override fun observeHistory() = flow {
                if (failures-- > 0) error("history unavailable")
                emitAll(base.observeHistory())
            }
        }
        val navigation = NavigationViewModel(NavigationTestService(false), history)
        try {
            runCurrent()
            assertTrue(navigation.loadFailed.value)
            assertTrue(navigation.backStack.value.isEmpty())
            navigation.retryLoad()
            runCurrent()
            assertFalse(navigation.loadFailed.value)
            assertFalse(navigation.loadFailed.value)
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun savingReadingKeepsCompletionOpenAndAcknowledgeCannotOverwriteIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = ChargingHistoryEntry(
            1L, ChargingSessionSnapshot.from(ChargingSettings(60f, 6f, 20f, 90f), 1L),
            3_600_001L, 30f, null, false
        )
        val history = TestHistoryRepository(listOf(entry))
        val navigation = NavigationViewModel(NavigationTestService(false), history)
        try {
            runCurrent()
            navigation.saveReading(entry.id, 28.5f, true)
            runCurrent()
            assertEquals(Destination.EndSummary, navigation.backStack.value.last())
            assertEquals(28.5f, navigation.summary.value!!.actualPercent)
            assertFalse(navigation.summary.value!!.reviewed)
            assertTrue(navigation.onBack())
            // Change the reading before the queued acknowledgment executes.
            navigation.saveReading(entry.id, 29f, false)
            runCurrent()
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
            assertEquals(entry.copy(actualPercent = 29f, reviewed = true), history.entries.value.single())
            navigation.navigate(Destination.History)
            navigation.openHistorySummary(history.entries.value.single())
            navigation.saveReading(entry.id, null, false)
            runCurrent()
            assertEquals(Destination.EndSummary, navigation.backStack.value.last())
            assertNull(navigation.summary.value!!.actualPercent)
            assertTrue(navigation.onBack())
            assertEquals(Destination.History, navigation.backStack.value.last())
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun settingsHistorySummaryRoundTripPreservesReviewState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(), 1L)
        val old = ChargingHistoryEntry(1L, snapshot, 60_001L, 21f, null, false)
        val history = TestHistoryRepository(listOf(old.copy(id = 2L, reviewed = true), old))
        val navigation = NavigationViewModel(NavigationTestService(false), history)
        try {
            runCurrent()
            val before = history.entries.value
            navigation.navigate(Destination.Settings)
            navigation.navigate(Destination.Presets)
            assertEquals(listOf(Destination.Config, Destination.Settings, Destination.Presets), navigation.backStack.value)
            assertTrue(navigation.onBack())
            assertEquals(listOf(Destination.Config, Destination.Settings), navigation.backStack.value)
            navigation.navigate(Destination.History)
            repeat(2) {
                navigation.openHistorySummary(old)
                assertEquals(listOf(Destination.Config, Destination.Settings, Destination.History, Destination.EndSummary),
                    navigation.backStack.value)
                assertTrue(navigation.onBack())
                runCurrent()
                assertEquals(listOf(Destination.Config, Destination.Settings, Destination.History), navigation.backStack.value)
                assertEquals(before, history.entries.value)
            }
            assertTrue(navigation.onBack())
            assertEquals(listOf(Destination.Config, Destination.Settings), navigation.backStack.value)
            assertTrue(navigation.onBack())
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun historySummaryReturnsToHistoryWithoutReviewingOrReplacingItOnTicks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(), 1L)
        val old = ChargingHistoryEntry(1L, snapshot, 60_001L, 21f, null, false)
        val newest = old.copy(id = 2L, reviewed = true)
        val history = TestHistoryRepository(listOf(newest, old))
        val service = NavigationTestService(true)
        val navigation = NavigationViewModel(service, history)
        try {
            runCurrent()
            navigation.navigate(Destination.History)
            navigation.openHistorySummary(old)
            assertEquals(old, navigation.summary.value)
            service.status.value = service.status.value.copy(currentPercent = 40f)
            runCurrent()
            assertEquals(old, navigation.summary.value)
            assertEquals(Destination.EndSummary, navigation.backStack.value.last())
            assertTrue(navigation.onBack())
            runCurrent()
            assertEquals(Destination.History, navigation.backStack.value.last())
            assertEquals(listOf(newest, old), history.entries.value)
            history.deleteSession(newest.id)
            runCurrent()
            assertEquals(Destination.History, navigation.backStack.value.last())
            assertTrue(history.entries.value.single().reviewed)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletingNewestWhileBrowsingDoesNotOpenOlderUnreviewedSummary() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(), 1L)
        val old = ChargingHistoryEntry(1L, snapshot, 60_001L, 21f, null, false)
        val newest = old.copy(id = 2L, reviewed = true)
        val history = TestHistoryRepository(listOf(newest, old))
        val navigation = NavigationViewModel(NavigationTestService(false), history)
        try {
            runCurrent()
            navigation.navigate(Destination.History)
            history.deleteSession(newest.id)
            runCurrent()
            assertEquals(Destination.History, navigation.backStack.value.last())
            navigation.openHistorySummary(old)
            navigation.onBack()
            runCurrent()
            assertEquals(Destination.History, navigation.backStack.value.last())
            assertTrue(history.entries.value.single().reviewed)
            navigation.onBack()
            runCurrent()
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
            val cold = NavigationViewModel(NavigationTestService(false), history)
            try {
                runCurrent()
                assertEquals(listOf(Destination.Config), cold.backStack.value)
            } finally {
                cold.viewModelScope.cancel()
            }
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

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
            assertEquals(entries.first().copy(reviewed = true), navigation.summary.value)
            assertEquals(listOf(true, false, false), history.entries.value.map { it.reviewed })
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun doneRetainsOutgoingSummaryAndNewPendingSessionReplacesIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entry = ChargingHistoryEntry(
            1L, ChargingSessionSnapshot.from(ChargingSettings(), 1L), 60_001L, 21f, null, false
        )
        val history = TestHistoryRepository(listOf(entry))
        val service = NavigationTestService(false)
        val navigation = NavigationViewModel(service, history)
        try {
            runCurrent()
            assertTrue(navigation.onBack())
            runCurrent()
            assertEquals(listOf(Destination.Config), navigation.backStack.value)
            assertEquals(entry.copy(reviewed = true), navigation.summary.value)

            service.start()
            runCurrent()
            assertEquals(listOf(Destination.Config, Destination.Running), navigation.backStack.value)
            assertEquals(entry.copy(reviewed = true), navigation.summary.value)
            val next = entry.copy(
                id = 100_000L,
                snapshot = ChargingSessionSnapshot.from(ChargingSettings(), 100_000L),
                endedAt = 160_000L
            )
            history.entries.value = listOf(next, entry.copy(reviewed = true))
            service.stop()
            runCurrent()
            assertEquals(listOf(Destination.Config, Destination.EndSummary), navigation.backStack.value)
            assertEquals(next, navigation.summary.value)
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
    override fun retryLoad() = Unit
    override val status = MutableStateFlow(ChargingStatus(isLoading = false, isRunning = active))
    override suspend fun start() { status.value = ChargingStatus(isLoading = false, isRunning = true) }
    override suspend fun stop() { status.value = ChargingStatus(isLoading = false) }
    override suspend fun changePower(powerKw: Float) = Unit
    override fun refresh() = Unit
}
