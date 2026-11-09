package info.anodsplace.evtimer.ui

enum class AppDestination { Config, Running, EndSummary, History, Settings, Presets, ChargingPowers, Licenses }

data class AppRoute(
    val destination: AppDestination,
    val summaryId: Long? = null,
    val completion: Boolean = false
)

/** Navigation policy is independent of the platform's history, ViewModels and renderer. */
data class AppNavigationState(
    val backStack: List<AppRoute> = emptyList(),
    val running: Boolean? = null,
    val pendingId: Long? = null
) {
    val current: AppRoute? get() = backStack.lastOrNull()

    fun reconcile(isRunning: Boolean, pendingSummaryId: Long?, historyIds: Set<Long>): AppNavigationState {
        val pending = pendingSummaryId.takeUnless { isRunning }
        val reset = running != isRunning || backStack.isEmpty() ||
            (pending != pendingId && backStack.none { it.destination == AppDestination.History })
        val stack = if (reset) {
            if (pending != null) listOf(AppRoute(AppDestination.Config), AppRoute(AppDestination.EndSummary, pending, true))
            else sessionStack(isRunning)
        } else backStack.filterNot {
            it.destination == AppDestination.EndSummary && it.summaryId !in historyIds
        }
        return copy(backStack = stack, running = isRunning, pendingId = pending)
    }

    fun navigate(destination: AppDestination): AppNavigationState =
        if (backStack.isEmpty() || current?.destination == destination) this
        else copy(backStack = backStack + AppRoute(destination))

    fun openSummary(id: Long): AppNavigationState =
        if (current?.destination != AppDestination.History) this
        else copy(backStack = backStack + AppRoute(AppDestination.EndSummary, id))

    /** A completion must be acknowledged durably before calling back(). */
    fun back(twoPane: Boolean = false): AppNavigationState {
        if (backStack.size <= 1) return this
        val exitsSettings = twoPane &&
            current?.destination.isSettingsTopLevelDetail() == true &&
            backStack.getOrNull(backStack.lastIndex - 1)?.destination == AppDestination.Settings
        return copy(backStack = backStack.dropLast(if (exitsSettings) 2 else 1))
    }

    fun restore(route: AppRoute, historyIds: Set<Long>): AppNavigationState {
        if (route.destination == AppDestination.EndSummary && route.summaryId !in historyIds) return this
        if (route.destination == AppDestination.Running && running != true) return this
        if (route.completion && route.summaryId != pendingId) return this
        val config = AppRoute(AppDestination.Config)
        val root = sessionStack(running == true)
        val settings = root + AppRoute(AppDestination.Settings)
        val stack = when (route.destination) {
            AppDestination.Config -> listOf(config)
            AppDestination.Running -> root
            AppDestination.Settings -> settings
            AppDestination.Presets, AppDestination.ChargingPowers, AppDestination.Licenses -> settings + route
            AppDestination.History -> settings + route
            AppDestination.EndSummary -> if (route.completion) listOf(config, route)
                else settings + AppRoute(AppDestination.History) + route
        }
        return copy(backStack = stack)
    }

    companion object {
        fun sessionStack(running: Boolean): List<AppRoute> =
            listOf(AppRoute(AppDestination.Config)) + if (running) listOf(AppRoute(AppDestination.Running)) else emptyList()
    }
}

private fun AppDestination?.isSettingsTopLevelDetail(): Boolean = when (this) {
    AppDestination.ChargingPowers, AppDestination.Presets, AppDestination.History, AppDestination.Licenses -> true
    else -> false
}

fun AppRoute.fragment(): String = when (destination) {
    AppDestination.Config -> "#/timer"
    AppDestination.Running -> "#/running"
    AppDestination.EndSummary -> "#/summary/$summaryId" + if (completion) "/completion" else ""
    else -> "#/${destination.name.lowercase()}"
}

fun parseAppRoute(fragment: String): AppRoute? {
    val parts = fragment.removePrefix("#/").split('/')
    if (parts.firstOrNull() == "summary") {
        val id = parts.getOrNull(1)?.toLongOrNull() ?: return null
        if (parts.size !in 2..3 || (parts.size == 3 && parts[2] != "completion")) return null
        return AppRoute(AppDestination.EndSummary, id, parts.size == 3)
    }
    if (parts.size != 1) return null
    val destination = when (parts[0]) {
        "timer" -> AppDestination.Config
        "running" -> AppDestination.Running
        else -> AppDestination.entries.firstOrNull { it.name.lowercase() == parts[0] && it != AppDestination.EndSummary }
    } ?: return null
    return AppRoute(destination)
}
