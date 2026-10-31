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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import info.anodsplace.evtimer.data.ChargingRepository
import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingSession
import info.anodsplace.evtimer.data.ChargingSettings
import info.anodsplace.evtimer.data.ChargingStatus
import info.anodsplace.evtimer.data.ChargingPreset
import info.anodsplace.evtimer.data.PresetConfiguration
import info.anodsplace.evtimer.data.PresetRepository
import info.anodsplace.evtimer.data.ClockTimeFormatter
import info.anodsplace.evtimer.uiModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.koin.compose.viewmodel.koinViewModel
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class DesktopNavigationTest {
    @Test
    fun settingsGearMovesPreferenceOutOfConfigurationAndPreservesItOnBack() = runDesktopComposeUiTest {
        val repository = DesktopTestRepository()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        setContent {
            KoinApplication(application = {
                modules(uiModule, module {
                    single<ChargingRepository> { repository }
                    single<PresetRepository> { repository }
                    single<ChargingService> { DesktopTestService(isRunning = false) }
                    single<ClockTimeFormatter> { ClockTimeFormatter { "" } }
                })
            }) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    DisposableEffect(owner) {
                        onDispose { owner.viewModelStore.clear() }
                    }
                    ChargingTimerApp()
                }
            }
        }
        onNodeWithText("Notify at 80%").assertDoesNotExist()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Notify at 80%").assertIsOn().performClick()
        onNodeWithText("Notify at 80%").assertIsOff()
        onNodeWithText("About").assertExists()
        onNodeWithText("Source code").assertExists()
        onNodeWithText("Privacy policy").assertExists()
        onNodeWithText("Open-source licenses").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start Timer").assertExists()
        onNodeWithText("Notify at 80%").assertDoesNotExist()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Notify at 80%").assertIsOff()
        onNodeWithText("Open-source licenses").performClick()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("About").assertExists()
        onNodeWithText("Notify at 80%").assertIsOff()
    }

    @Test
    @Suppress("DEPRECATION")
    fun escapePopsRunningWithoutClickingOrFocusingAnyControl() = runDesktopComposeUiTest {
        val service = DesktopTestService()
        val repository = DesktopTestRepository()
        lateinit var navigation: NavigationViewModel
        lateinit var focusManager: FocusManager
        setContent {
            KoinApplication(application = {
                modules(uiModule, module {
                    single<ChargingRepository> { repository }
                    single<PresetRepository> { repository }
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

private class DesktopTestService(isRunning: Boolean = true) : ChargingService {
    override val status = MutableStateFlow(ChargingStatus(isLoading = false, isRunning = isRunning))
    override suspend fun start() = Unit
    override suspend fun stop() { status.value = ChargingStatus(isLoading = false) }
    override suspend fun changePower(powerKw: Float) = Unit
    override fun refresh() = Unit
}

private class DesktopTestRepository : ChargingRepository, PresetRepository {
    private val notifyAt80 = MutableStateFlow(true)
    override fun observeNotifyAt80() = notifyAt80
    override suspend fun setNotifyAt80(enabled: Boolean) { notifyAt80.value = enabled }
    private val settings = MutableStateFlow(ChargingSettings())
    private val session = MutableStateFlow(ChargingSession())
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
    override suspend fun saveSettings(settings: ChargingSettings) { this.settings.value = settings }
    override suspend fun saveSession(session: ChargingSession) { this.session.value = session }
}
