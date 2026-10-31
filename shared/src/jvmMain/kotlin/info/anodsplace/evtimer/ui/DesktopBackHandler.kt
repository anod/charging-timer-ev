package info.anodsplace.evtimer.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

fun NavigationViewModel.handleDesktopKeyEvent(event: KeyEvent): Boolean =
    event.key == Key.Escape && event.type == KeyEventType.KeyUp && onBack()
