package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class AdaptiveWidth { Compact, Medium, Expanded }
enum class SettingsItem { ChargingPower, Presets, History, Licenses }

internal val LocalSettingsTwoPane = compositionLocalOf { false }

internal data class AdaptiveLayout(val width: AdaptiveWidth, val shortHeight: Boolean) {
    val twoColumns: Boolean get() = width == AdaptiveWidth.Expanded && !shortHeight
}

internal fun adaptiveLayout(width: Dp, height: Dp) = AdaptiveLayout(
    width = when {
        width < 600.dp -> AdaptiveWidth.Compact
        width < 840.dp -> AdaptiveWidth.Medium
        else -> AdaptiveWidth.Expanded
    },
    shortHeight = height < 480.dp
)

internal val readablePageWidth = 720.dp
internal val wideTimerWidth = 1120.dp

@Composable
fun AdaptiveSettingsNavigation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSettingsTwoPane provides adaptiveLayout(maxWidth, maxHeight).twoColumns,
            content = content
        )
    }
}
