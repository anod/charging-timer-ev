package info.anodsplace.evtimer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Charging Timer for EV",
    ) {
        App()
    }
}