package info.anodsplace.evtimer.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
private class FakeClock(var milliseconds: Long = 1_800_000_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(milliseconds)
}

private class FakeChargingRepository : ChargingRepository {
    val settings = MutableStateFlow(ChargingSettings(60f, 12f, 20f, 80f))
    val session = MutableStateFlow(ChargingSession())
    var writes = 0
    override suspend fun saveSettings(settings: ChargingSettings) {
        this.settings.value = settings
    }
    override fun observeSettings() = flow {
        emitAll(settings)
    }
    override suspend fun saveSession(session: ChargingSession) {
        writes++
        this.session.value = session
    }
    override fun observeSession() = flow {
        emitAll(session)
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ChargingServiceTest {
    @Test
    fun slowTickerWithArbitraryPhaseTracksEveryDisplayedBoundary() = verifyDisplayBoundaries(11f)

    @Test
    fun fastTickerWithArbitraryPhaseTracksEveryDisplayedBoundary() = verifyDisplayBoundaries(180f)

    private fun verifyDisplayBoundaries(power: Float) = runTest {
        val epoch = 1_800_000_000_000L
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(epoch + testScheduler.currentTime)
        }
        val snapshot = ChargingSessionSnapshot.from(ChargingSettings(83f, power, 60f, 100f), epoch - 30_000L)
        val repo = FakeChargingRepository()
        repo.saveSession(ChargingSession(true, snapshot))
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        runCurrent()
        val remaining = ChargingCalculator.minutesToReach(snapshot, epoch, 100f)
        val nextRounded = floor(ChargingCalculator.percentAt(snapshot, epoch).toDouble()) + 0.5
        val roundedTarget = if (nextRounded > ChargingCalculator.percentAt(snapshot, epoch)) {
            nextRounded.toFloat()
        } else (nextRounded + 1.0).toFloat()
        val percentBoundary = ceil(
            ChargingCalculator.minutesToReach(snapshot, epoch, roundedTarget) * 60_000.0
        ).toLong()
        val remainingBoundary = ceil((remaining - (ceil(remaining) - 1.0)) * 60_000.0).toLong()
        val checks = listOf(
            Triple(30_000L, { service.status.value.elapsedMinutes }, 1),
            Triple(remainingBoundary, { service.status.value.calculation.timeRemainingMinutes }, ceil(remaining).toInt() - 1),
            Triple(percentBoundary, { service.status.value.currentPercent.roundToInt() }, roundedTarget.roundToInt())
        ).sortedBy { it.first }
        for ((boundary, value, expected) in checks) {
            advanceTimeBy(boundary - 100L - testScheduler.currentTime)
            runCurrent()
            assertEquals(expected - if (boundary == remainingBoundary) -1 else 1, value(), "Before boundary at $boundary ms")
            advanceTimeBy(1_100L)
            runCurrent()
            assertEquals(expected, value(), "Within one second after boundary at $boundary ms")
        }
        // Notification's truncated percent must also update on its whole-percent boundary.
        val wholePercent = floor(ChargingCalculator.percentAt(snapshot, epoch).toDouble()).toFloat() + 1f
        val wholeBoundary = ceil(ChargingCalculator.minutesToReach(snapshot, epoch, wholePercent) * 60_000.0).toLong()
        if (wholeBoundary > testScheduler.currentTime + 100L) {
            advanceTimeBy(wholeBoundary - 100L - testScheduler.currentTime)
            runCurrent()
            assertEquals(wholePercent.toInt() - 1, service.status.value.currentPercent.toInt())
            advanceTimeBy(1_100L)
            runCurrent()
            assertEquals(wholePercent.toInt(), service.status.value.currentPercent.toInt())
        }
    }

    @Test
    fun startStopLifecycleSnapshotsCurrentSettings() = runTest {
        val repo = FakeChargingRepository()
        val clock = FakeClock()
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        assertTrue(service.status.value.isLoading)
        service.refresh()
        assertTrue(service.status.value.isLoading)
        runCurrent()
        assertFalse(service.status.value.isLoading)
        service.start()
        runCurrent()
        assertTrue(service.status.value.isRunning)
        assertEquals(clock.milliseconds, service.status.value.startTime)
        assertEquals(ChargingSessionSnapshot.from(repo.settings.value, clock.milliseconds), repo.session.value.snapshot)
        service.start()
        assertEquals(1, repo.writes)
        service.stop()
        runCurrent()
        assertFalse(service.status.value.isRunning)
        assertFalse(repo.session.value.isRunning)
        service.stop()
        assertEquals(2, repo.writes)
    }

    @Test
    fun restoredSessionUsesSnapshotNotNewDefaults() = runTest {
        val repo = FakeChargingRepository()
        val clock = FakeClock()
        val start = clock.milliseconds - 30 * 60_000L - 30_000L
        repo.saveSession(ChargingSession(true, ChargingSessionSnapshot.from(repo.settings.value, start)))
        repo.saveSettings(ChargingSettings(100f, 50f, 10f, 100f))
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        runCurrent()
        val status = service.status.value
        assertEquals(80f, status.targetPercent)
        assertEquals(30.166666f, status.currentPercent, 0.00001f)
        assertEquals(150, status.calculation.timeRemainingMinutes)
        assertEquals(start + 180 * 60_000L, status.estimatedEndTime)
        assertEquals(12f, status.calculation.chargingSpeed)
        assertEquals(30, status.elapsedMinutes)
        repo.saveSettings(ChargingSettings(75f, 7f, 50f, 90f))
        runCurrent()
        assertEquals(status, service.status.value)
    }

    @Test
    fun percentAndEtaWithMultipleSegmentsAndEfficiency() = runTest {
        val clock = FakeClock()
        val start = clock.milliseconds - 120 * 60_000L
        val snapshot = ChargingSessionSnapshot(
            60f, 20f, 100f,
            listOf(PowerSegment(start, 12f), PowerSegment(start + 60 * 60_000L, 24f)),
            efficiency = 0.5f
        )
        val repo = FakeChargingRepository()
        repo.saveSession(ChargingSession(true, snapshot))
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        runCurrent()
        assertEquals(50f, service.status.value.currentPercent)
        assertEquals(150, service.status.value.calculation.timeRemainingMinutes)
        assertEquals(start + 270 * 60_000L, service.status.value.estimatedEndTime)
        // 80% needs 90 more minutes; the configured 100% target needs 150.
        assertEquals(90.0, ChargingCalculator.minutesToReach(snapshot, clock.milliseconds, 80f), 0.000001)
    }

    @Test
    fun completedSessionKeepsTargetAndOriginalEta() = runTest {
        val repo = FakeChargingRepository()
        val clock = FakeClock()
        val start = clock.milliseconds - 181 * 60_000L
        repo.saveSession(ChargingSession(true, ChargingSessionSnapshot.from(repo.settings.value, start)))
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        runCurrent()
        val status = service.status.value
        assertTrue(status.isRunning)
        assertEquals(0, status.calculation.timeRemainingMinutes)
        assertEquals(80f, status.currentPercent)
        assertEquals(80f, status.targetPercent)
        assertEquals(start + 180 * 60_000L, status.estimatedEndTime)
    }

    @Test
    fun tickerUpdatesAtNextWholePercentEarlierThanOneMinute() = runTest {
        val repo = FakeChargingRepository()
        repo.saveSettings(ChargingSettings(60f, 360f, 20f, 80f))
        val clock = FakeClock()
        val epoch = clock.milliseconds
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        runCurrent()
        service.start()
        runCurrent()
        advanceTimeBy(5_999L)
        clock.milliseconds = epoch + testScheduler.currentTime
        runCurrent()
        assertEquals(20f, service.status.value.currentPercent)
        advanceTimeBy(1L)
        clock.milliseconds = epoch + testScheduler.currentTime
        runCurrent()
        assertEquals(21f, service.status.value.currentPercent)
    }

    @Test
    fun tickerNeverWaitsLongerThanOneMinute() = runTest {
        val repo = FakeChargingRepository()
        val clock = FakeClock()
        val epoch = clock.milliseconds
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        runCurrent()
        service.start()
        runCurrent()
        advanceTimeBy(60_000L)
        clock.milliseconds = epoch + testScheduler.currentTime
        runCurrent()
        assertEquals(20.333334f, service.status.value.currentPercent, 0.00001f)
    }

    @Test
    fun lifecycleWaitsUntilRepositoryHasLoaded() = runTest {
        val loaded = CompletableDeferred<Unit>()
        val settings = ChargingSettings(60f, 12f, 20f, 80f)
        val clock = FakeClock()
        val snapshot = ChargingSessionSnapshot.from(settings, clock.milliseconds - 60_000L)
        val repo = object : ChargingRepository {
            val session = MutableStateFlow(ChargingSession(true, snapshot))
            var writes = 0
            override fun observeSettings() = flow { loaded.await(); emit(settings) }
            override fun observeSession() = flow { loaded.await(); emitAll(session) }
            override suspend fun saveSettings(settings: ChargingSettings) = Unit
            override suspend fun saveSession(session: ChargingSession) {
                writes++
                this.session.value = session
            }
        }
        val service = ChargingServiceImpl(repo, backgroundScope, clock)
        val start = backgroundScope.launch { service.start() }
        runCurrent()
        assertTrue(service.status.value.isLoading)
        assertEquals(0, repo.writes)
        loaded.complete(Unit)
        runCurrent()
        assertTrue(start.isCompleted)
        assertFalse(service.status.value.isLoading)
        assertEquals(snapshot.startTime, service.status.value.startTime)
        assertEquals(0, repo.writes)
        service.stop()
        runCurrent()
        assertEquals(1, repo.writes)
    }
}
