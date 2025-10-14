package info.anodsplace.evtimer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Domain-level status emitted by ChargingService (does NOT include settings themselves)
data class ChargingStatus(
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val currentPercent: Float = 0f,
    val estimatedEndTime: Long = 0L,
    val calculation: ChargingCalculation = ChargingCalculation()
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
    private val tickerIntervalMs: Long = 60_000L,
) : ChargingService {

    private val mutableStatus = MutableStateFlow(ChargingStatus())
    override val status: StateFlow<ChargingStatus> = mutableStatus.asStateFlow()

    private var lastSettings: ChargingSettings = ChargingSettings()
    private var lastSession: ChargingSession = ChargingSession()

    private var tickerJob: Job? = null

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
                        delay(tickerIntervalMs)
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
        val calculation = ChargingCalculator.getChargingCalculation(
            settings = settings,
            isRunning = session.isRunning,
            startTime = session.startTime
        )
        val totalMinutes = ChargingCalculator.calculateChargingTime(
            settings.batteryCapacity,
            settings.startPercent,
            settings.maxPercent,
            settings.chargingPower
        )
        val estimatedEndTime = if (session.isRunning) {
            ChargingCalculator.estimateEndTime(session.startTime, totalMinutes)
        } else 0L
        val currentPercent = if (session.isRunning) calculation.estimatedPercent else settings.startPercent
        mutableStatus.value = ChargingStatus(
            isRunning = session.isRunning,
            startTime = session.startTime,
            currentPercent = currentPercent,
            estimatedEndTime = estimatedEndTime,
            calculation = calculation
        )
    }

    override suspend fun start() {
        val session = lastSession
        if (session.isRunning) return
        val now = Clock.System.now().toEpochMilliseconds()
        repository.saveSession(ChargingSession(isRunning = true, startTime = now))
    }

    override suspend fun stop() {
        val session = lastSession
        if (!session.isRunning) return
        repository.saveSession(session.copy(isRunning = false))
    }

    override fun refresh() {
        recompute()
    }
}
