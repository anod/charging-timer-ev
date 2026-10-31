package info.anodsplace.evtimer.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingSession
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.uiModule
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.koin.compose.viewmodel.koinViewModel
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class DesktopNavigationTest {
    @Test
    @Suppress("DEPRECATION")
    fun escapePopsRunningWithoutClickingOrFocusingAnyControl() = runDesktopComposeUiTest {
        val service = DesktopTestService()
        lateinit var navigation: NavigationViewModel
        lateinit var focusManager: FocusManager
        setContent {
            KoinApplication(application = {
                modules(uiModule, module {
                    single<ChargingRepository> { DesktopTestRepository() }
                    single<ChargingService> { service }
                    single<ClockTimeFormatter> { ClockTimeFormatter { "" } }
                })
            }) {
                navigation = koinViewModel()
                focusManager = LocalFocusManager.current
                ChargingTimerApp(navigation = navigation)
            }
        }
        onNodeWithText("Charging in Progress").assertExists()
        // The test scene has no Window callback. Invoke the same handler with pure Compose
        // events, before focus dispatch, just as the desktop Window does.
        fun escape(expectsPop: Boolean = true) = runOnIdle {
            assertFalse(navigation.handleDesktopKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)))
            if (expectsPop) {
                assertTrue(navigation.handleDesktopKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp)))
            } else {
                assertFalse(navigation.handleDesktopKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp)))
            }
        }
        escape()
        onNodeWithText("Start Timer").assertExists()
        assertTrue(service.status.value.isRunning)
        onNodeWithContentDescription("Start input field").performClick()
        onNodeWithText("Start Timer").performClick()
        onNodeWithText("Charging in Progress").assertExists()
        escape()
        onNodeWithText("Start Timer").assertExists()
        assertTrue(service.status.value.isRunning)

        onNodeWithText("Start Timer").performClick()
        onNodeWithText("Charging in Progress").assertExists()
        runOnIdle { focusManager.clearFocus(force = true) }
        escape()
        onNodeWithText("Start Timer").assertExists()
        assertTrue(service.status.value.isRunning)
        // Escape at the root must leave Config in place.
        escape(expectsPop = false)
        onNodeWithText("Start Timer").assertExists()
    }
}

private class DesktopTestService : ChargingService {
    override val status = MutableStateFlow(ChargingStatus(isRunning = true))
    override suspend fun start() = Unit
    override suspend fun stop() { status.value = ChargingStatus() }
    override fun refresh() = Unit
}

private class DesktopTestRepository : ChargingRepository {
    private val settings = MutableStateFlow(ChargingSettings())
    private val session = MutableStateFlow(ChargingSession(isRunning = true))
    override fun observeSettings() = settings
    override fun observeSession() = session
    override suspend fun saveSettings(settings: ChargingSettings) { this.settings.value = settings }
    override suspend fun saveSession(session: ChargingSession) { this.session.value = session }
}
