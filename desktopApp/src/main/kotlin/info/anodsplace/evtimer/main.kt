package info.anodsplace.evtimer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import info.anodsplace.evtimer.ui.NavigationViewModel
import info.anodsplace.evtimer.ui.handleDesktopKeyEvent
import org.koin.compose.viewmodel.koinViewModel

fun main() {
    initKoin()
    application {
        val backHandler = remember { DesktopWindowBackHandler() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Charging Timer for EV",
            onPreviewKeyEvent = { backHandler.onKeyEvent(it) },
        ) {
            val navigation = koinViewModel<NavigationViewModel>()
            SideEffect { backHandler.onKeyEvent = navigation::handleDesktopKeyEvent }
            App(navigation)
        }

    }
}

private class DesktopWindowBackHandler {
    var onKeyEvent: (KeyEvent) -> Boolean = { false }
}
