package info.anodsplace.evtimer

import info.anodsplace.evtimer.data.ChargingService
import info.anodsplace.evtimer.data.ChargingStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargingRestoreStatusTest {
    private class Service : ChargingService {
        override fun retryLoad() = Unit
        override val status = MutableStateFlow(ChargingStatus())
        var refreshCount = 0
        var onRefresh: () -> Unit = {}
        override suspend fun start() = error("Restore must not start a new session")
        override suspend fun stop() = error("Completion is owned by recompute")
        override suspend fun changePower(powerKw: Float) = error("Restore must not change power")
        override fun refresh() {
            refreshCount++
            onRefresh()
        }
    }

    @Test
    fun coldOverdueRestoreWaitsForLoadedAndPersistedStoppedStatus() = runBlocking {
        withTimeout(5_000) {
            val service = Service()
            val restoring = async(start = CoroutineStart.UNDISPATCHED) { service.awaitRestoreStatus() }
            assertFalse(restoring.isCompleted)
            assertEquals(0, service.refreshCount)
            service.status.value = ChargingStatus(
                isLoading = false, isRunning = true, startTime = 1, estimatedEndTime = 2
            )
            yield()
            assertEquals(1, service.refreshCount)
            assertFalse(restoring.isCompleted)
            // ChargingService publishes this only after saveSession has persisted completion.
            service.status.value = ChargingStatus(isLoading = false)
            assertFalse(restoring.await().isRunning)
        }
    }

    @Test
    fun refreshReevaluatesWarmStatusBeforeDecidingWhetherToWait() = runBlocking {
        withTimeout(5_000) {
            val service = Service()
            service.status.value = ChargingStatus(
                isLoading = false, isRunning = true, estimatedEndTime = Long.MAX_VALUE
            )
            service.onRefresh = {
                service.status.value = service.status.value.copy(estimatedEndTime = 1)
            }
            val restoring = async(start = CoroutineStart.UNDISPATCHED) { service.awaitRestoreStatus() }
            assertFalse(restoring.isCompleted)
            service.status.value = ChargingStatus(isLoading = false)
            assertFalse(restoring.await().isRunning)
        }
    }

    @Test
    fun futureRunningAndInterruptedStoppedSessionsReturnWithoutWaiting() = runBlocking {
        for (status in listOf(
            ChargingStatus(isLoading = false, isRunning = true, estimatedEndTime = Long.MAX_VALUE),
            ChargingStatus(isLoading = false)
        )) {
            val service = Service()
            service.status.value = status
            assertEquals(status, withTimeout(5_000) { service.awaitRestoreStatus() })
            assertTrue(service.refreshCount == 1)
        }
    }
}
