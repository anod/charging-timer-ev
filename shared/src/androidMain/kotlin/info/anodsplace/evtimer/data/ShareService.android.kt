package info.anodsplace.evtimer.data

import android.content.Context
import android.content.Intent

actual class ShareService(private val context: Context) {
    actual fun share(text: String): ShareResult {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ShareResult.Shared
    }
}
