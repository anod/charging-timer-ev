package info.anodsplace.evtimer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.KeyEvent
import info.anodsplace.evtimer.ui.NavigationViewModel
import info.anodsplace.evtimer.ui.handleDesktopKeyEvent
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.app_title
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource

fun main() {
    initKoin()
    application {
        val backHandler = remember { DesktopWindowBackHandler() }
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_title),
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
