package info.anodsplace.evtimer.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
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
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopNavigationTest {
    @Test
    @Suppress("DEPRECATION")
    fun escapePopsRunningWithoutClickingOrFocusingAnyControl() = runDesktopComposeUiTest {
        val service = DesktopTestService()
        setContent {
            KoinApplication(application = {
                modules(uiModule, module {
                    single<ChargingRepository> { DesktopTestRepository() }
                    single<ChargingService> { service }
                    single<ClockTimeFormatter> { ClockTimeFormatter { "" } }
                })
            }) {
                ChargingTimerApp()
            }
        }
        onNodeWithText("Charging in Progress").assertExists()
        onRoot().performKeyInput {
            keyDown(Key.Escape)
            keyUp(Key.Escape)
        }
        onNodeWithText("Start Timer").assertExists()
        assertTrue(service.status.value.isRunning)
        // Escape at the root must leave Config in place.
        onRoot().performKeyInput {
            keyDown(Key.Escape)
            keyUp(Key.Escape)
        }
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
