package info.anodsplace.evtimer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import info.anodsplace.evtimer.data.BackupError
import info.anodsplace.evtimer.data.BackupMessage
import info.anodsplace.evtimer.data.BackupState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BackupSettingsTest {
    @Test
    fun groupedBackupRowsRespectRunningAndBusyStatesWithoutHidingOtherRoutes() = runDesktopComposeUiTest {
        val state = mutableStateOf(BackupState())
        val running = mutableStateOf<Boolean?>(null)
        var exports = 0
        var restores = 0
        setContent {
            AppTheme {
                SettingsScreen(
                    true, {}, {}, {}, {}, onOpenHistory = {},
                    backupContent = {
                        BackupSettingsContent(state.value, running.value, { exports++ }, { restores++ }, {}, {})
                    }
                )
            }
        }
        onNodeWithText("Presets").assertExists()
        onNodeWithText("Charging history").assertExists()
        onNodeWithText("Export backup").performScrollTo().assertIsEnabled().performClick()
        onNodeWithText("Restore from backup").performScrollTo().assertIsNotEnabled()
        runOnIdle { running.value = true }
        onNodeWithText("Stop the current session first").assertExists()
        onNodeWithText("Restore from backup").assertIsNotEnabled()
        runOnIdle { running.value = false }
        onNodeWithText("Restore from backup").assertIsEnabled().performClick()
        runOnIdle {
            assertEquals(1, exports)
            assertEquals(1, restores)
            state.value = BackupState(busy = true)
        }
        onNodeWithText("Export backup").assertIsNotEnabled()
        onNodeWithText("Restore from backup").assertIsNotEnabled()
        onNodeWithText("Working…").performScrollTo().assertExists()
        onNodeWithTag("charging-waves").assertDoesNotExist()
        runOnIdle { state.value = BackupState(message = BackupMessage.RESTORED) }
        onNodeWithText("Backup restored").performScrollTo().assertExists()
        runOnIdle { state.value = BackupState(error = BackupError.INVALID_JSON) }
        onNodeWithText("Cannot read backup: invalid or incomplete JSON").performScrollTo().assertExists()
        onNodeWithText("Source code").performScrollTo().assertExists()
        onNodeWithTag("charging-waves").assertDoesNotExist()
        onAllNodesWithText("Backup & restore").fetchSemanticsNodes().let { assertEquals(1, it.size) }
    }
}
