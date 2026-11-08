package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.scene.SceneStrategy
import info.anodsplace.evtimer.generated.resources.Res
import info.anodsplace.evtimer.generated.resources.settings_select_detail
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> rememberSettingsListDetailSceneStrategy(): SceneStrategy<T> =
    rememberSettingsListDetailSceneStrategy(LocalSettingsTwoPane.current)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun <T : Any> rememberSettingsListDetailSceneStrategy(twoPane: Boolean): SceneStrategy<T> {
    val windowInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowInfo, twoPane) {
        calculatePaneScaffoldDirective(windowInfo).copy(
            maxHorizontalPartitions = if (twoPane) 2 else 1
        )
    }
    return rememberListDetailSceneStrategy(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun settingsListPaneMetadata(): Map<String, Any> =
    ListDetailSceneStrategy.listPane(detailPlaceholder = { SettingsDetailPlaceholder() })

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun settingsDetailPaneMetadata(): Map<String, Any> =
    ListDetailSceneStrategy.detailPane()

@Composable
internal fun SettingsDetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.settings_select_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
