package info.anodsplace.evtimer.data

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual class ShareService {
    actual fun share(text: String): ShareResult {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        return ShareResult.Copied
    }
}
