package info.anodsplace.evtimer.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RetryableLoad(
    private val scope: CoroutineScope,
    private val label: String,
    private val onLoading: () -> Unit,
    private val onError: () -> Unit,
    private val load: suspend () -> Unit
) {
    private var job: Job? = null
    private val mutex = Mutex()

    fun retry() {
        job?.cancel()
        onLoading()
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                mutex.withLock { load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                fail(error)
            }
        }
        job = next
        next.start()
    }

    fun fail(error: Exception) {
        job?.cancel()
        println("$label: load failed: $error")
        onError()
    }
}
