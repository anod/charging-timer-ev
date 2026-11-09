package info.anodsplace.evtimer.ui

import kotlin.test.*

class AppNavigationStateTest {
    private fun idle() = AppNavigationState().reconcile(false, null, emptySet())

    @Test
    fun settingsHierarchyAndHistorySummaryReturnToTheirParent() {
        val settings = idle().navigate(AppDestination.Settings)
        val history = settings.navigate(AppDestination.History)
        val summary = history.openSummary(42)
        assertEquals(AppRoute(AppDestination.EndSummary, 42), summary.current)
        assertEquals(history, summary.back())
        assertEquals(settings, history.back())
        assertEquals(idle(), settings.back())
        for (destination in listOf(AppDestination.Presets, AppDestination.ChargingPowers, AppDestination.Licenses)) {
            assertEquals(settings, settings.navigate(destination).back())
        }
    }

    @Test
    fun twoPaneBackExitsSettingsAndTopLevelDetailsButPreservesNestedHistorySummary() {
        val root = idle()
        val settings = root.navigate(AppDestination.Settings)
        assertEquals(root, settings.back(twoPane = true))

        for (destination in listOf(
            AppDestination.ChargingPowers,
            AppDestination.Presets,
            AppDestination.History,
            AppDestination.Licenses
        )) {
            assertEquals(root, settings.navigate(destination).back(twoPane = true))
        }

        val history = settings.navigate(AppDestination.History)
        val summary = history.openSummary(42)
        assertEquals(history, summary.back(twoPane = true))
        assertEquals(root, history.back(twoPane = true))
    }

    @Test
    fun sessionTransitionsReplaceSecondaryScreensButTicksPreserveThem() {
        val settings = idle().navigate(AppDestination.Settings)
        assertEquals(settings, settings.reconcile(false, null, emptySet()))
        val running = settings.reconcile(true, null, emptySet())
        assertEquals(AppDestination.Running, running.current?.destination)
        val completed = running.reconcile(false, 42, setOf(42))
        assertEquals(AppRoute(AppDestination.EndSummary, 42, true), completed.current)
        assertEquals(completed, completed.reconcile(false, 42, setOf(42)))
        assertEquals(AppDestination.Config, completed.reconcile(false, null, setOf(42)).current?.destination)
    }

    @Test
    fun historyBrowsingIsNotInterruptedByPendingChangesAndDeletedSummaryIsRemoved() {
        val history = idle().navigate(AppDestination.Settings).navigate(AppDestination.History)
        val updated = history.reconcile(false, 42, setOf(42))
        assertEquals(history.backStack, updated.backStack)
        val summary = updated.openSummary(42).reconcile(false, 42, emptySet())
        assertEquals(AppDestination.History, summary.current?.destination)
        assertEquals(idle(), idle().openSummary(42))
    }

    @Test
    fun restoringLinksRebuildsHierarchyAndRejectsStaleSessionRoutes() {
        val state = idle()
        val history = state.restore(AppRoute(AppDestination.EndSummary, 42), setOf(42))
        assertEquals(listOf(AppDestination.Config, AppDestination.Settings, AppDestination.History, AppDestination.EndSummary),
            history.backStack.map { it.destination })
        assertEquals(state, state.restore(AppRoute(AppDestination.Running), emptySet()))
        assertEquals(state, state.restore(AppRoute(AppDestination.EndSummary, 42), emptySet()))
        assertEquals(state, state.restore(AppRoute(AppDestination.EndSummary, 42, true), setOf(42)))
        val running = state.reconcile(true, null, emptySet())
        assertEquals(AppDestination.Config, running.restore(AppRoute(AppDestination.Config), emptySet()).current?.destination)
    }

    @Test
    fun fragmentsPreserveLongIdsWithoutJavascriptNumberConversion() {
        val route = AppRoute(AppDestination.EndSummary, 9_007_199_254_740_993L)
        assertEquals(route, parseAppRoute(route.fragment()))
        for (destination in AppDestination.entries.filter { it != AppDestination.EndSummary }) {
            val screen = AppRoute(destination)
            assertEquals(screen, parseAppRoute(screen.fragment()))
        }
        assertNull(parseAppRoute("#/summary/nope"))
        assertNull(parseAppRoute("#/summary/42/unexpected"))
        assertNull(parseAppRoute("#/unknown"))
    }
}
