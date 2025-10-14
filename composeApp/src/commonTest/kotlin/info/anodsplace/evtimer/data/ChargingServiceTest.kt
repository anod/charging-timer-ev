package info.anodsplace.evtimer.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private class FakeChargingRepository : ChargingRepository {
    private val settingsFlow = MutableStateFlow(ChargingSettings())
    private val sessionFlow = MutableStateFlow(ChargingSession())

    override suspend fun saveSettings(settings: ChargingSettings) {
        settingsFlow.value = settings
    }

    override fun observeSettings() = settingsFlow

    override suspend fun saveSession(session: ChargingSession) {
        sessionFlow.value = session
    }

    override fun observeSession() = sessionFlow

    // Helpers for assertions
    val currentSession: ChargingSession get() = sessionFlow.value
    val currentSettings: ChargingSettings get() = settingsFlow.value
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingServiceTest {

    @Test
    fun startStopLifecycle() = runTest {
        val repo = FakeChargingRepository()
        val service = ChargingServiceImpl(repo, this, tickerIntervalMs = 10_000L)

        // Initially not running
        assertFalse(service.status.value.isRunning)

        service.start()
        testScheduler.advanceUntilIdle()
        assertTrue(service.status.value.isRunning)
        assertTrue(service.status.value.startTime > 0L)
        assertTrue(repo.currentSession.isRunning)

        service.stop()
        testScheduler.advanceUntilIdle()
        assertFalse(service.status.value.isRunning)
        assertFalse(repo.currentSession.isRunning)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun percentAndEndTimeCalculationWithPastStartTime() = runTest {
        val repo = FakeChargingRepository()
        // Configure settings to make math easy: 60 kWh, 20% -> 80%, delta 60% of 60 = 36 kWh at 12 kW => 3h = 180 min
        val settings = ChargingSettings(
            batteryCapacity = 60f,
            chargingPower = 12f,
            startPercent = 20f,
            maxPercent = 80f,
            availablePowers = listOf(12f)
        )
        repo.saveSettings(settings)

        val service = ChargingServiceImpl(repo, this, tickerIntervalMs = 100_000L)
        testScheduler.advanceUntilIdle()

        // Simulate started 30 minutes ago
        val now = Clock.System.now().toEpochMilliseconds()
        val elapsedMinutes = 30
        val startTime = now - elapsedMinutes * 60_000L
        repo.saveSession(ChargingSession(isRunning = true, startTime = startTime))
        testScheduler.advanceUntilIdle()

        val status = service.status.value
        // Expected percent increase: (60% / 180min) * 30 = 10% => 30% total
        val expectedPercent = 30f
        assertApprox(expectedPercent, status.currentPercent, 0.6f) // allow some rounding tolerance

        // Expected remaining time: 150 minutes (180 - 30)
        val expectedRemaining = 150
        assertEquals(expectedRemaining, status.calculation.timeRemainingMinutes, "Remaining minutes mismatch")

        // Estimated end time should equal startTime + totalMinutes * 60_000
        val expectedEndTime = startTime + 180 * 60_000L
        assertEquals(expectedEndTime, status.estimatedEndTime, "Estimated end time mismatch")
    }

    private fun assertApprox(expected: Float, actual: Float, tolerance: Float) {
        if (abs(expected - actual) > tolerance) {
            throw AssertionError("Expected ~${expected} (±$tolerance) but was $actual")
        }
    }
}
