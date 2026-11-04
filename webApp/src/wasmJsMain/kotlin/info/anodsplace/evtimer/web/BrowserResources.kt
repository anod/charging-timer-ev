package info.anodsplace.evtimer.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import info.anodsplace.evtimer.generated.resources.Res as SharedRes
import info.anodsplace.evtimer.generated.resources.allPluralStringResources as sharedPlurals
import info.anodsplace.evtimer.generated.resources.allStringResources as sharedStrings
import info.anodsplace.evtimer.ui.AppTheme
import info.anodsplace.evtimer.web.generated.resources.*
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun BrowserResources(content: @Composable () -> Unit) {
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(ready) {
        document.body?.setAttribute("aria-busy", (!ready).toString())
        onDispose { document.body?.removeAttribute("aria-busy") }
    }
    LaunchedEffect(Unit) {
        try {
            // Web stringResource initially returns "". Warm each cached item, not
            // just the file: later screens and formatted strings must be synchronous too.
            for (resource in SharedRes.sharedStrings.values + Res.allStringResources.values) {
                getString(resource)
            }
            for (resource in SharedRes.sharedPlurals.values) getPluralString(resource, 1)
            ready = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("Browser resources failed; reload to retry: ${error.message}")
            throw error
        }
    }
    AppTheme {
        if (ready) content()
        else Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
