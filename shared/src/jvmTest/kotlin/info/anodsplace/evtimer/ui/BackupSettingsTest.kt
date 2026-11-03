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
import info.anodsplace.evtimer.data.BackupFormat
import info.anodsplace.evtimer.data.BackupPreferences
import info.anodsplace.evtimer.data.BackupPreset
import info.anodsplace.evtimer.data.BackupSegment
import info.anodsplace.evtimer.data.BackupSession
import info.anodsplace.evtimer.data.BackupState
import info.anodsplace.evtimer.data.ManualBackup
import info.anodsplace.evtimer.data.formatSessionDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class BackupSettingsTest {
    @Test
    fun restoreDialogFormatsExportTimeLikeHistoryAndPluralizesCounts() = runDesktopComposeUiTest {
        val exportedAt = "2026-11-02T21:45:18.009978Z"
        val backup = ManualBackup(
            BackupFormat.FORMAT, BackupFormat.VERSION, exportedAt, "1.0",
            BackupPreferences("preset-0", 20f),
            listOf(BackupPreset("preset-0", "Home", 60.0, 7.0, 80.0, listOf(7.0))),
            listOf(BackupSession(1, 60.0, 20.0, 80.0, 1.0, listOf(BackupSegment(1, 7.0))))
        )
        val state = mutableStateOf(BackupState(pending = backup))
        setContent {
            AppTheme {
                BackupSettingsContent(state.value, false, {}, {}, {}, {})
            }
        }
        val date = formatSessionDate(Instant.parse(exportedAt).toEpochMilliseconds())
        val cases = listOf(
            Triple(1, 1, "1 preset · 1 session"),
            Triple(3, 1, "3 presets · 1 session"),
            Triple(1, 3, "1 preset · 3 sessions"),
            Triple(3, 3, "3 presets · 3 sessions"),
            Triple(1, 0, "1 preset · 0 sessions")
        )
        for ((presets, sessions, counts) in cases) {
            runOnIdle {
                val pending = backup.copy(
                    presets = List(presets) { backup.presets.single().copy(id = "preset-$it") },
                    sessions = List(sessions) { backup.sessions.single().copy(id = it + 1L) }
                )
                BackupFormat.validate(pending)
                state.value = BackupState(pending = pending)
            }
            onNodeWithText("Exported $date\nApp version 1.0\n$counts").assertExists()
            onNodeWithText(exportedAt, substring = true).assertDoesNotExist()
            onNodeWithText("Restore backup?").assertExists()
            onNodeWithText("Replaces all presets and history on this device. No timer will resume.").assertExists()
            onAllNodesWithText("Restore from backup")[1].assertIsEnabled()
        }
    }

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
