package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import info.anodsplace.evtimer.initKoin
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.web_storage_unavailable
import kotlinx.browser.document
import org.jetbrains.compose.resources.stringResource

fun main() {
    initKoin()
    ComposeViewport(document.body ?: error("Browser document body is unavailable")) {
        // The shared app requires persisted storage, which is added in the next layer.
        StorageUnavailableScreen()
    }
}

@Composable
private fun StorageUnavailableScreen() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(Res.string.web_storage_unavailable))
        }
    }
}
