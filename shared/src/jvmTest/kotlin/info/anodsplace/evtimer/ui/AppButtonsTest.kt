package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppButtonsTest {
    @Test
    fun actionRolesShareMinimumHeightAndKeepTheirClickSemantics() = runDesktopComposeUiTest {
        val clicks = mutableListOf<String>()
        setContent {
            MaterialTheme {
                Column {
                    AppPrimaryButton(onClick = { clicks += "primary" }) { Text("Primary") }
                    AppSecondaryButton(onClick = { clicks += "secondary" }) { Text("Secondary") }
                    AppDestructiveButton(onClick = { clicks += "destructive" }) { Text("Destructive") }
                    AppTextButton(onClick = { clicks += "text" }) { Text("Text") }
                }
            }
        }

        onNodeWithText("Primary").assertHeightIsAtLeast(56.dp).performClick()
        onNodeWithText("Secondary").assertHeightIsAtLeast(56.dp).performClick()
        onNodeWithText("Destructive").assertHeightIsAtLeast(56.dp).performClick()
        onNodeWithText("Text").assertHeightIsAtLeast(48.dp).performClick()
        runOnIdle {
            assertEquals(listOf("primary", "secondary", "destructive", "text"), clicks)
        }
    }

    @Test
    fun iconRoleRetainsItsAccessibilityLabelAndTouchTarget() = runDesktopComposeUiTest {
        setContent {
            MaterialTheme {
                AppIconButton(onClick = {}) {
                    Text("Edit", Modifier.semantics { contentDescription = "Edit charging power" })
                }
            }
        }

        val action = onNodeWithContentDescription("Edit charging power")
        action.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        onAllNodes(hasClickAction() and hasContentDescription("Edit charging power")).assertCountEquals(1)
    }
}
