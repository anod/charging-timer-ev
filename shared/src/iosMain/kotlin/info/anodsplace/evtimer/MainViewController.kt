package info.anodsplace.evtimer

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin()
    }
    return ComposeUIViewController { App() }
}
