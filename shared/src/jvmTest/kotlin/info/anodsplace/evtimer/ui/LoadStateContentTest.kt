package info.anodsplace.evtimer.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoadStateContentTest {
    @Test fun loadingLight() = capture(false, false)
    @Test fun loadingDark() = capture(false, true)
    @Test fun errorLight() = capture(true, false)
    @Test fun errorDark() = capture(true, true)

    private fun capture(failed: Boolean, dark: Boolean) = runDesktopComposeUiTest {
        setContent {
            AppTheme(darkTheme = dark, dynamicColor = false) {
                Surface(Modifier.size(360.dp, 640.dp).testTag("load-state")) {
                    LoadStateContent(failed, onRetry = {})
                }
            }
        }
        if (failed) onNodeWithText("Retry").assertExists()
        val directory = System.getenv("ISSUE105_EVIDENCE_DIR")
        if (directory != null) {
            val name = "${if (failed) "error" else "loading"}-${if (dark) "dark" else "light"}.png"
            check(ImageIO.write(onNodeWithTag("load-state").captureToImage().toAwtImage(),
                "png", File(directory, name)))
        }
    }

    @Test
    fun retryActionReturnsToLoading() = runDesktopComposeUiTest {
        var failed by mutableStateOf(true)
        var retries = 0
        setContent {
            AppTheme(dynamicColor = false) {
                Surface {
                    LoadStateContent(failed, onRetry = { retries++; failed = false })
                }
            }
        }
        onNodeWithText("Could not load data. Please try again.").assertExists()
        onNodeWithText("Retry").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        onNodeWithText("Retry").assertDoesNotExist()
        runOnIdle { assertEquals(1, retries) }
    }
}
