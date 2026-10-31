package info.anodsplace.evtimer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Domain-level status emitted by ChargingService (does NOT include settings themselves)
data class ChargingStatus(
    val isLoading: Boolean = true,
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val currentPercent: Float = 0f,
    val estimatedEndTime: Long = 0L,
    val calculation: ChargingCalculation = ChargingCalculation(),
    val targetPercent: Float = ChargingSettings.DEFAULT.maxPercent,
    val snapshot: ChargingSessionSnapshot? = null,
    val elapsedMinutes: Int = 0
)

/**
 * Service responsible for managing charging session lifecycle and exposing
 * current charging status via a StateFlow independent of UI specific state.
 */
interface ChargingService {
    val status: StateFlow<ChargingStatus>
    suspend fun start()
    suspend fun stop()
    fun refresh()
}

@OptIn(ExperimentalTime::class)
class ChargingServiceImpl(
    private val repository: ChargingRepository,
    private val scope: CoroutineScope,
    private val clock: Clock,
) : ChargingService {

    private val mutableStatus = MutableStateFlow(ChargingStatus())
    override val status: StateFlow<ChargingStatus> = mutableStatus.asStateFlow()

    private var lastSettings: ChargingSettings = ChargingSettings()
    private var lastSession: ChargingSession = ChargingSession()

    private var tickerJob: Job? = null
    private val sessionMutex = Mutex()

    init {
        scope.launch {
            combine(
                repository.observeSettings(),
                repository.observeSession()
            ) { settings, session -> settings to session }
                .collectLatest { (settings, session) ->
                    lastSettings = settings
                    lastSession = session
                    recompute()
                    ensureTicker(session.isRunning)
                }
        }
    }

    private fun ensureTicker(isRunning: Boolean) {
        if (isRunning) {
            if (tickerJob == null) {
                tickerJob = scope.launch {
                    while (true) {
                        delay(nextTickDelay())
                        recompute()
                    }
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    private fun recompute() {
        val settings = lastSettings
        val session = lastSession
        val now = clock.now().toEpochMilliseconds()
        val snapshot = if (session.isRunning) requireNotNull(session.snapshot) else
            ChargingSessionSnapshot.from(settings, now)
        val remaining = ChargingCalculator.minutesToReach(snapshot, now, snapshot.targetPercent)
        val currentPercent = ChargingCalculator.percentAt(snapshot, now)
        val calculation = ChargingCalculation(
            timeRemainingMinutes = ceil(remaining).toInt(),
            estimatedPercent = currentPercent,
            chargingSpeed = snapshot.segments.lastOrNull { it.startedAt <= now }?.powerKw
                ?: snapshot.segments.first().powerKw
        )
        val estimatedEndTime = if (session.isRunning) snapshot.startTime + ceil(
            ChargingCalculator.minutesToReach(snapshot, snapshot.startTime, snapshot.targetPercent) * 60_000.0
        ).toLong() else 0L
        mutableStatus.value = ChargingStatus(
            isLoading = false,
            isRunning = session.isRunning,
            startTime = session.startTime,
            currentPercent = currentPercent,
            estimatedEndTime = estimatedEndTime,
            calculation = calculation,
            targetPercent = snapshot.targetPercent,
            snapshot = if (session.isRunning) snapshot else null,
            elapsedMinutes = if (session.isRunning) {
                ((now - snapshot.startTime).coerceAtLeast(0L) / 60_000L).toInt()
            } else 0
        )
    }

    private fun nextTickDelay(): Long {
        val snapshot = lastSession.snapshot ?: return 60_000L
        val now = clock.now().toEpochMilliseconds()
        val elapsed = (now - snapshot.startTime).coerceAtLeast(0L)
        val elapsedBoundary = 60_000L - elapsed % 60_000L
        val percent = ChargingCalculator.percentAt(snapshot, now)
        if (percent >= snapshot.targetPercent) return elapsedBoundary
        val remaining = ChargingCalculator.minutesToReach(snapshot, now, snapshot.targetPercent)
        val remainingBoundary = ceil((remaining - (ceil(remaining) - 1.0)) * 60_000.0).toLong()
        val nextPercent = minOf(
            ((floor(percent.toDouble() * 2.0) + 1.0) / 2.0).toFloat(),
            snapshot.targetPercent
        )
        val percentBoundary = ceil(
            ChargingCalculator.minutesToReach(snapshot, now, nextPercent) * 60_000.0
        ).toLong()
        return minOf(elapsedBoundary, remainingBoundary, percentBoundary, 60_000L).coerceAtLeast(10L)
    }

    override suspend fun start() = sessionMutex.withLock {
        status.first { !it.isLoading }
        if (repository.observeSession().first().isRunning) return@withLock
        val now = clock.now().toEpochMilliseconds()
        repository.saveSession(
            ChargingSession(isRunning = true, snapshot = repository.createSnapshot(now))
        )
    }

    override suspend fun stop() = sessionMutex.withLock {
        status.first { !it.isLoading }
        val session = repository.observeSession().first()
        if (!session.isRunning) return@withLock
        repository.saveSession(session.copy(isRunning = false))
    }

    override fun refresh() {
        if (!status.value.isLoading) recompute()
    }
}
