package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import info.anodsplace.evtimer.web.generated.resources.Res
import info.anodsplace.evtimer.web.generated.resources.web_storage_unavailable
import kotlinx.browser.document
import org.jetbrains.compose.resources.stringResource

fun main() {
    ComposeViewport(document.body ?: error("Browser document body is unavailable")) {
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
