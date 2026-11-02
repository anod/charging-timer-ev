package info.anodsplace.evtimer.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant
import kotlin.text.CharacterCodingException

enum class BackupError { INVALID_JSON, INVALID_DATA, NEWER_VERSION, TOO_LARGE, SESSION_RUNNING, PREFERENCES, IO }

class BackupException(val error: BackupError, message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

@Serializable
data class ManualBackup(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val preferences: BackupPreferences,
    val presets: List<BackupPreset>,
    val sessions: List<BackupSession>
)

@Serializable
data class BackupPreferences(
    val selectedPresetId: String,
    val startPercent: Float,
    val notifyAt80: Boolean = true,
    val draft: BackupDraft? = null
)

@Serializable
data class BackupDraft(
    val presetId: String,
    val batteryCapacity: Float,
    val chargingPower: Float,
    val targetPercent: Float,
    val powerOptions: List<Float>
) {
    fun settings(startPercent: Float) = ChargingSettings(
        batteryCapacity, chargingPower, startPercent, targetPercent, powerOptions
    )
}

@Serializable
data class BackupPreset(
    val id: String,
    val name: String,
    val batteryCapacity: Double,
    val chargingPower: Double,
    val targetPercent: Double,
    val powerOptions: List<Double>,
    val position: Long = 0
)

@Serializable
data class BackupSession(
    val id: Long,
    val batteryCapacity: Double,
    val startPercent: Double,
    val targetPercent: Double,
    val efficiency: Double,
    val segments: List<BackupSegment>,
    val presetId: String? = null,
    val presetName: String? = null,
    val endedAt: Long? = null,
    val endPercent: Double? = null,
    val actualPercent: Double? = null,
    val reviewed: Boolean = false,
    val interrupted: Boolean = false,
    val presetEdited: Boolean = false
)

@Serializable
data class BackupSegment(val startedAt: Long, val powerKw: Double)

object BackupFormat {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val FORMAT = "ev-charging-timer-backup"
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(backup: ManualBackup): ByteArray {
        validate(backup)
        val bytes = json.encodeToString(ManualBackup.serializer(), backup).encodeToByteArray()
        checkSize(bytes)
        return bytes
    }

    fun decode(bytes: ByteArray): ManualBackup {
        checkSize(bytes)
        val backup = try {
            val document = json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
            val version = document["formatVersion"]?.jsonPrimitive?.intOrNull
            if (version != null && version > VERSION) {
                throw BackupException(BackupError.NEWER_VERSION, "This backup needs a newer app version")
            }
            validateNumberTypes(document)
            // Version 1 is the first released format. Future readers upgrade older DTOs here.
            json.decodeFromJsonElement(ManualBackup.serializer(), document)
        } catch (error: SerializationException) {
            throw BackupException(BackupError.INVALID_JSON, "Malformed or incomplete backup JSON", error)
        } catch (error: CharacterCodingException) {
            throw BackupException(BackupError.INVALID_JSON, "Backup is not valid UTF-8", error)
        } catch (error: IllegalArgumentException) {
            if (error is BackupException) throw error
            throw BackupException(BackupError.INVALID_JSON, "Invalid UTF-8 or JSON document", error)
        }
        validate(backup)
        return backup
    }

    private fun validateNumberTypes(document: JsonObject) {
        fun number(value: JsonElement) {
            if (value != JsonNull && (value !is JsonPrimitive || value.isString)) {
                throw BackupException(BackupError.INVALID_JSON, "A backup number has the wrong JSON type")
            }
        }
        fun fields(element: JsonElement?, names: Set<String>) {
            val obj = element as? JsonObject ?: return
            names.forEach { key -> obj[key]?.let(::number) }
            (obj["powerOptions"] as? JsonArray)?.forEach(::number)
        }
        fields(document, setOf("formatVersion"))
        val preferences = document["preferences"] as? JsonObject
        fields(preferences, setOf("startPercent"))
        fields(preferences?.get("draft"), setOf("batteryCapacity", "chargingPower", "targetPercent"))
        (document["presets"] as? JsonArray)?.forEach {
            fields(it, setOf("batteryCapacity", "chargingPower", "targetPercent", "position"))
        }
        (document["sessions"] as? JsonArray)?.forEach {
            fields(it, setOf("id", "batteryCapacity", "startPercent", "targetPercent", "efficiency",
                "endedAt", "endPercent", "actualPercent"))
            ((it as? JsonObject)?.get("segments") as? JsonArray)?.forEach { segment ->
                fields(segment, setOf("startedAt", "powerKw"))
            }
        }
    }

    private fun checkSize(bytes: ByteArray) {
        if (bytes.size > MAX_BYTES) throw BackupException(BackupError.TOO_LARGE, "Backup exceeds 16 MiB")
    }

    fun validate(backup: ManualBackup) {
        fun valid(value: Boolean, field: String) {
            if (!value) throw BackupException(BackupError.INVALID_DATA, "Invalid backup: $field")
        }
        fun positive(value: Double) = value.isFinite() && value > 0 && value.toFloat().isFinite() && value.toFloat() > 0
        fun percent(value: Double) = value.isFinite() && value in 0.0..100.0
        valid(backup.format == FORMAT, "format")
        if (backup.formatVersion > VERSION) throw BackupException(BackupError.NEWER_VERSION, "This backup needs a newer app version")
        valid(backup.formatVersion == VERSION, "formatVersion")
        valid(runCatching { Instant.parse(backup.exportedAt) }.isSuccess, "exportedAt")
        valid(backup.appVersion.isNotBlank(), "appVersion")
        valid(backup.presets.isNotEmpty(), "presets must not be empty")
        valid(backup.presets.map { it.id }.distinct().size == backup.presets.size, "duplicate preset ids")
        backup.presets.forEach {
            valid(it.id.isNotBlank(), "preset id")
            // Backup restores existing names, including those grandfathered by putPreset.
            valid(it.name.isNotBlank(), "preset name")
            valid(positive(it.batteryCapacity) && positive(it.chargingPower), "preset capacity/power")
            valid(percent(it.targetPercent), "preset target")
            valid(it.powerOptions.isNotEmpty() && it.powerOptions.all(::positive), "preset power options")
        }
        valid(percent(backup.preferences.startPercent.toDouble()), "start percentage")
        backup.preferences.draft?.let { draft ->
            valid(draft.presetId == backup.preferences.selectedPresetId &&
                backup.presets.any { it.id == draft.presetId }, "draft preset")
            valid(positive(draft.batteryCapacity.toDouble()) && positive(draft.chargingPower.toDouble()) &&
                percent(draft.targetPercent.toDouble()) && draft.powerOptions.isNotEmpty() &&
                draft.powerOptions.all { positive(it.toDouble()) }, "draft values")
        }
        valid(backup.sessions.map { it.id }.distinct().size == backup.sessions.size, "duplicate session ids")
        backup.sessions.forEach {
            valid(positive(it.batteryCapacity), "session capacity")
            valid(percent(it.startPercent) && percent(it.targetPercent), "session percentages")
            valid(it.efficiency.isFinite() && it.efficiency > 0 && it.efficiency <= 1 &&
                it.efficiency.toFloat() > 0, "session efficiency")
            valid(it.endPercent == null || percent(it.endPercent), "end percentage")
            valid(it.actualPercent == null || percent(it.actualPercent), "actual percentage")
            valid(it.segments.isNotEmpty() && it.segments.all { segment -> positive(segment.powerKw) } &&
                it.segments.zipWithNext().all { (a, b) -> a.startedAt < b.startedAt }, "session segments")
            valid(it.endedAt == null || it.endedAt >= it.segments.last().startedAt, "session end time")
        }
    }
}

interface ManualBackupRepository {
    suspend fun exportBackup(appVersion: String): ManualBackup
    suspend fun restoreBackup(backup: ManualBackup)
}
