@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.web

import info.anodsplace.evtimer.data.ShareResult
import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise

@JsFun("""async (text) => {
    if (navigator.share) {
        try { await navigator.share({text}); return 'shared'; }
        catch (error) { if (error.name === 'AbortError') return 'shared'; throw error; }
    }
    if (!navigator.clipboard?.writeText) throw new Error('Sharing and clipboard are unavailable');
    await navigator.clipboard.writeText(text);
    return 'copied';
}""")
private external fun shareText(text: String): Promise<JsString>

internal suspend fun shareSummary(text: String): ShareResult =
    if (shareText(text).await().toString() == "copied") ShareResult.Copied else ShareResult.Shared
