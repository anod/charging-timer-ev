package info.anodsplace.evtimer.data

enum class ShareResult { Shared, Copied }

expect class ShareService {
    fun share(text: String): ShareResult
}
