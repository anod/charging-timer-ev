package info.anodsplace.evtimer.data

actual class ShareService {
    actual fun share(text: String): ShareResult {
        val copied: Boolean = js(
            """
            (() => {
                const field = document.createElement("textarea");
                field.value = text;
                field.style.position = "fixed";
                field.style.opacity = "0";
                document.body.appendChild(field);
                field.select();
                const result = document.execCommand("copy");
                field.remove();
                return result;
            })()
            """
        )
        check(copied) { "Browser clipboard access is unavailable" }
        return ShareResult.Copied
    }
}
