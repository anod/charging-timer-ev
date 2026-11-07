package info.anodsplace.evtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OpaqueBackgroundNavEntryDecoratorTest {
    @Test
    fun entryCoversUnderlyingSceneIncludingLoadingAndThemeChanges() = runDesktopComposeUiTest {
        val dark = mutableStateOf(false)
        val loading = mutableStateOf(true)
        var surface = Color.Unspecified
        setContent {
            AppTheme(darkTheme = dark.value, dynamicColor = false) {
                surface = MaterialTheme.colorScheme.surface
                Box(Modifier.size(200.dp).background(Color.Red).testTag("scene")) {
                    NavDisplay(
                        backStack = listOf(Destination.Config),
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            remember { OpaqueBackgroundNavEntryDecorator() }
                        ),
                        entryProvider = { destination ->
                            NavEntry(destination) {
                                if (loading.value) Box(Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize()) {
                                    Box(Modifier.size(40.dp).background(Color.Blue))
                                }
                            }
                        }
                    )
                }
            }
        }
        for (isDark in listOf(false, true)) {
            for (isLoading in listOf(true, false)) {
                runOnIdle {
                    dark.value = isDark
                    loading.value = isLoading
                }
                val pixels = onNodeWithTag("scene").captureToImage().toPixelMap()
                assertEquals(surface.toArgb(), pixels[pixels.width - 1, pixels.height - 1].toArgb())
                assertEquals(
                    (if (isLoading) surface else Color.Blue).toArgb(),
                    pixels[1, 1].toArgb()
                )
            }
        }
    }
}
