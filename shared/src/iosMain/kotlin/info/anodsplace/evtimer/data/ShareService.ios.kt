package info.anodsplace.evtimer.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual class ShareService {
    @OptIn(ExperimentalForeignApi::class)
    actual fun share(text: String): ShareResult {
        var presenter = requireNotNull(UIApplication.sharedApplication.keyWindow?.rootViewController) {
            "No active window for sharing"
        }
        while (presenter.presentedViewController != null) {
            presenter = requireNotNull(presenter.presentedViewController)
        }
        val controller = UIActivityViewController(listOf(text), null)
        controller.popoverPresentationController?.let {
            it.sourceView = presenter.view
            it.sourceRect = presenter.view.bounds
        }
        presenter.presentViewController(controller, animated = true, completion = null)
        return ShareResult.Shared
    }
}
