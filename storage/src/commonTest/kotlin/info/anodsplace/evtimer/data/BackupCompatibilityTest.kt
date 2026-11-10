package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupCompatibilityTest {
    // Keep this first-version fixture unchanged when a newer format is introduced.
    private val versionOne = """
        {
          "format": "ev-charging-timer-backup", "formatVersion": 1,
          "exportedAt": "2026-11-01T09:30:00Z", "appVersion": "first-version",
          "preferences": { "selectedPresetId": "custom", "startPercent": 20.0 },
          "presets": [{
            "id": "custom", "name": "Default", "batteryCapacity": 60.0,
            "chargingPower": 7.0, "targetPercent": 80.0, "powerOptions": [7.0, 11.0]
          }],
          "sessions": [{
            "id": 12, "batteryCapacity": 60.0, "startPercent": 20.0,
            "targetPercent": 80.0, "efficiency": 0.9,
            "endedAt": 300, "endPercent": 80.0,
            "segments": [{"startedAt": 100, "powerKw": 7.0}]
          }]
        }
    """.trimIndent()

    @Test
    fun firstVersionRemainsReadableWithDefaultsForOptionalFields() {
        val backup = BackupFormat.decode(versionOne.encodeToByteArray())
        assertEquals(BackupFormat.VERSION, backup.formatVersion)
        assertTrue(backup.preferences.notifyAt80)
        assertNull(backup.preferences.draft)
        assertEquals(0L, backup.presets.single().position)
        assertEquals(80.0, backup.presets.single().taperStartPercent)
        assertEquals(100.0, backup.presets.single().taperEndPercent)
        assertEquals(22.0, backup.presets.single().taperPowerCapKw)
        val session = backup.sessions.single()
        assertFalse(session.presetEdited)
        assertFalse(session.reviewed)
        assertFalse(session.interrupted)
        assertFalse(session.calibrationEnabled)
        assertNull(session.presetId)
        assertNull(session.presetName)
        assertNull(session.actualPercent)
        assertEquals(80.0, session.taperStartPercent)
        assertEquals(100.0, session.taperEndPercent)
        assertEquals(22.0, session.taperPowerCapKw)
        assertEquals(backup, BackupFormat.decode(BackupFormat.encode(backup)))
    }

    @Test
    fun additiveUnknownFieldsAreIgnoredAtEveryNestingLevel() {
        val withUnknown = versionOne
            .replace("\"format\":", "\"futureRoot\": {\"nested\": [1,2]}, \"format\":")
            .replace("\"selectedPresetId\":", "\"futurePreference\": true, \"selectedPresetId\":")
            .replace("\"name\":", "\"futurePreset\": \"value\", \"name\":")
            .replace("\"efficiency\":", "\"futureSession\": null, \"efficiency\":")
            .replace("\"powerKw\":", "\"futureSegment\": [1,2], \"powerKw\":")
        assertEquals(BackupFormat.decode(versionOne.encodeToByteArray()),
            BackupFormat.decode(withUnknown.encodeToByteArray()))
    }

    @Test
    fun newerVersionHasClearErrorEvenWhenItsPayloadShapeChanged() {
        val error = assertFailsWith<BackupException> {
            BackupFormat.decode("""{"formatVersion":3,"newPayload":{}}""".encodeToByteArray())
        }
        assertEquals(BackupError.NEWER_VERSION, error.error)
    }

    @Test
    fun versionOneKeepsOldBoundsAndNeverGrantsConsentOrSegmentFactors() {
        val oldWithUnknowns = versionOne
            .replace("\"efficiency\": 0.9", "\"calibrationEnabled\": true, \"efficiency\": 0.9")
            .replace("\"powerKw\": 7.0", "\"powerKw\": 7.0, \"efficiency\": 0.8")
        val upgraded = BackupFormat.decode(oldWithUnknowns.encodeToByteArray()).sessions.single()
        assertFalse(upgraded.calibrationEnabled)
        assertNull(upgraded.segments.single().efficiency)
        assertEquals(BackupError.INVALID_DATA, assertFailsWith<BackupException> {
            BackupFormat.decode(versionOne.replace("\"efficiency\": 0.9", "\"efficiency\": 1.01").encodeToByteArray())
        }.error)

        val v2 = versionOne.replace("\"formatVersion\": 1", "\"formatVersion\": 2")
            .replace("\"powerKw\": 7.0", "\"powerKw\": 7.0, \"efficiency\": 0.8")
            .replace("\"efficiency\": 0.9", "\"efficiency\": 1.0, \"calibrationEnabled\": true")
        val backup = BackupFormat.decode(v2.encodeToByteArray())
        assertEquals(1.0, backup.sessions.single().efficiency)
        assertEquals(0.8, backup.sessions.single().segments.single().efficiency)
        assertTrue(backup.sessions.single().calibrationEnabled)
        assertEquals(80.0, backup.presets.single().taperStartPercent)
        assertEquals(100.0, backup.presets.single().taperEndPercent)
        assertEquals(22.0, backup.sessions.single().taperPowerCapKw)
        assertEquals(backup, BackupFormat.decode(BackupFormat.encode(backup)))
        val missingConsent = v2.replace(", \"calibrationEnabled\": true", "")
        assertFalse(BackupFormat.decode(missingConsent.encodeToByteArray()).sessions.single().calibrationEnabled)
        assertEquals(BackupError.INVALID_JSON, assertFailsWith<BackupException> {
            BackupFormat.decode(v2.replace("\"calibrationEnabled\": true", "\"calibrationEnabled\": \"true\"").encodeToByteArray())
        }.error)
    }

    @Test
    fun quotedNumbersBooleansAndWrongCollectionElementsAreNotCoerced() {
        val badReplacements = listOf(
            "\"formatVersion\": 1" to "\"formatVersion\": \"1\"",
            "\"startPercent\": 20.0" to "\"startPercent\": \"20.0\"",
            "\"powerOptions\": [7.0, 11.0]" to "\"powerOptions\": [\"7.0\", 11.0]",
            "\"id\": 12" to "\"id\": \"12\"",
            "\"efficiency\": 0.9" to "\"efficiency\": \"0.9\"",
            "\"powerKw\": 7.0" to "\"powerKw\": \"7.0\"",
            "\"selectedPresetId\": \"custom\"" to "\"notifyAt80\": \"true\", \"selectedPresetId\": \"custom\"",
            "\"endedAt\": 300" to "\"reviewed\": \"false\", \"endedAt\": 300"
        )
        badReplacements.forEach { (from, to) ->
            assertEquals(BackupError.INVALID_JSON, assertFailsWith<BackupException> {
                BackupFormat.decode(versionOne.replace(from, to).encodeToByteArray())
            }.error)
        }
    }

    @Test
    fun deeplyNestedUnknownDataIsRejectedBeforeRecursiveParsing() {
        val nested = """{"formatVersion":1,"unknown":""" + "[".repeat(10_000) + "0" + "]".repeat(10_000) + "}"
        assertEquals(BackupError.INVALID_JSON,
            assertFailsWith<BackupException> { BackupFormat.decode(nested.encodeToByteArray()) }.error)
        val bracketsInName = versionOne.replace("Default", "[]{}\\\"".repeat(4))
        assertTrue(BackupFormat.decode(bracketsInName.encodeToByteArray()).presets.single().name.contains('['))
    }

    @Test
    fun exactSizeLimitIsReadableButOneExtraByteIsRejected() {
        val base = versionOne.replaceFirst("{", "{\"padding\":\"\",")
        val paddingSize = BackupFormat.MAX_BYTES - base.encodeToByteArray().size
        val document = base.replace("\"padding\":\"\"", "\"padding\":\"" + "x".repeat(paddingSize) + "\"")
        val bytes = document.encodeToByteArray()
        assertEquals(BackupFormat.MAX_BYTES, bytes.size)
        assertEquals("first-version", BackupFormat.decode(bytes).appVersion)
        assertEquals(BackupError.TOO_LARGE,
            assertFailsWith<BackupException> { BackupFormat.decode(bytes + byteArrayOf(32)) }.error)
    }
}
