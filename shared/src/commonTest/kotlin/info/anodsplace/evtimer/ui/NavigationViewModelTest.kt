package info.anodsplace.evtimer.ui

import androidx.lifecycle.viewModelScope
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingStatus
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
        val futureScreens = listOf(Destination.EndSummary, Destination.History, Destination.Settings, Destination.PresetEditor)
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
        val navigation = NavigationViewModel(service)
        try {
            block(navigation, service)
        } finally {
            navigation.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}

private class NavigationTestService(active: Boolean) : ChargingService {
    override val status = MutableStateFlow(ChargingStatus(isLoading = false, isRunning = active))
    override suspend fun start() { status.value = ChargingStatus(isLoading = false, isRunning = true) }
    override suspend fun stop() { status.value = ChargingStatus(isLoading = false) }
    override fun refresh() = Unit
}
