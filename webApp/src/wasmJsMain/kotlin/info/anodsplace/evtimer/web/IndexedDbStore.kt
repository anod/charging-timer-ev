package info.anodsplace.evtimer.web

import info.anodsplace.evtimer.data.BackupError
import info.anodsplace.evtimer.data.BackupException
import info.anodsplace.evtimer.data.BrowserDocumentStore
import info.anodsplace.evtimer.data.StoredBrowserDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

@JsFun("() => globalThis.evTimerStorage.read()")
private external fun readDocument(): Promise<JsString>

@JsFun("(revision, payload) => globalThis.evTimerStorage.write(revision, payload)")
private external fun writeDocument(revision: JsString?, payload: JsString): Promise<JsString>

@JsFun("() => globalThis.evTimerStorage.pickBackup()")
internal external fun pickBackup(): Promise<JsString?>

@JsFun("(text) => globalThis.evTimerStorage.downloadBackup(text)")
internal external fun downloadBackup(text: JsString)

internal class IndexedDbStore : BrowserDocumentStore {
    override suspend fun read(): StoredBrowserDocument? = io {
        Json.decodeFromString<StoredBrowserDocument?>(readDocument().await().toString())
    }

    override suspend fun compareAndSet(expectedRevision: String?, payload: String): StoredBrowserDocument = io {
        Json.decodeFromString<StoredBrowserDocument>(
            writeDocument(expectedRevision?.toJsString(), payload.toJsString()).await().toString()
        )
    }

    private suspend fun <T> io(action: suspend () -> T): T = try {
        action()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw BackupException(BackupError.IO, "IndexedDB operation failed: ${error.message}", error)
    }
}
