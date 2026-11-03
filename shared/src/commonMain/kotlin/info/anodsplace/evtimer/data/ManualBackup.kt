@file:kotlinx.serialization.UseSerializers(
    BackupDoubleSerializer::class, BackupFloatSerializer::class,
    BackupLongSerializer::class, BackupIntSerializer::class, BackupBooleanSerializer::class
)

package info.anodsplace.evtimer.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonDecoder
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
    val presetEdited: Boolean = false,
    val calibrationEnabled: Boolean = false
)

@Serializable
data class BackupSegment(val startedAt: Long, val powerKw: Double, val efficiency: Double? = null)

@Serializable
private data class BackupHeader(val formatVersion: Int)

@Serializable
private data class BackupSegmentV1(val startedAt: Long, val powerKw: Double) {
    fun upgrade() = BackupSegment(startedAt, powerKw)
}

// Keep the released v1 session contract separate: new fields must not grant consent.
@Serializable
private data class BackupSessionV1(
    val id: Long,
    val batteryCapacity: Double,
    val startPercent: Double,
    val targetPercent: Double,
    val efficiency: Double,
    val segments: List<BackupSegmentV1>,
    val presetId: String? = null,
    val presetName: String? = null,
    val endedAt: Long? = null,
    val endPercent: Double? = null,
    val actualPercent: Double? = null,
    val reviewed: Boolean = false,
    val interrupted: Boolean = false,
    val presetEdited: Boolean = false
) {
    fun upgrade() = BackupSession(id, batteryCapacity, startPercent, targetPercent, efficiency,
        segments.map { it.upgrade() }, presetId, presetName, endedAt, endPercent, actualPercent, reviewed,
        interrupted, presetEdited, calibrationEnabled = false)
}

@Serializable
private data class ManualBackupV1(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val preferences: BackupPreferences,
    val presets: List<BackupPreset>,
    val sessions: List<BackupSessionV1>
) {
    fun upgrade() = ManualBackup(format, formatVersion, exportedAt, appVersion,
        preferences, presets, sessions.map { it.upgrade() })
}

object BackupFormat {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val FORMAT = "ev-charging-timer-backup"
    const val VERSION = 2
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
            checkDepth(bytes)
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            val version = json.decodeFromString(BackupHeader.serializer(), text).formatVersion
            if (version > VERSION) {
                throw BackupException(BackupError.NEWER_VERSION, "This backup needs a newer app version")
            }
            if (version == 1) json.decodeFromString(ManualBackupV1.serializer(), text).upgrade()
            else json.decodeFromString(ManualBackup.serializer(), text)
        } catch (error: SerializationException) {
            throw BackupException(BackupError.INVALID_JSON, "Malformed or incomplete backup JSON", error)
        } catch (error: CharacterCodingException) {
            throw BackupException(BackupError.INVALID_JSON, "Backup is not valid UTF-8", error)
        } catch (error: IllegalArgumentException) {
            if (error is BackupException) throw error
            throw BackupException(BackupError.INVALID_JSON, "Invalid UTF-8 or JSON document", error)
        }
        validate(backup)
        return if (backup.formatVersion == 1) backup.copy(
            formatVersion = VERSION,
            sessions = backup.sessions.map { it.copy(calibrationEnabled = false) }
        ) else backup
    }

    private fun checkDepth(bytes: ByteArray) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (byte in bytes) {
            val char = byte.toInt().toChar()
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> {
                    depth++
                    if (depth > 32) throw BackupException(BackupError.INVALID_JSON, "Backup JSON is nested too deeply")
                }
                '}', ']' -> depth--
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
        valid(backup.formatVersion in 1..VERSION, "formatVersion")
        valid(runCatching { Instant.parse(backup.exportedAt) }.isSuccess, "exportedAt")
        valid(backup.appVersion.isNotBlank(), "appVersion")
        valid(backup.presets.isNotEmpty(), "presets must not be empty")
        val presetIds = HashSet<String>()
        backup.presets.forEach {
            valid(presetIds.add(it.id), "duplicate preset ids")
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
                draft.presetId in presetIds, "draft preset")
            valid(positive(draft.batteryCapacity.toDouble()) && positive(draft.chargingPower.toDouble()) &&
                percent(draft.targetPercent.toDouble()) && draft.powerOptions.isNotEmpty() &&
                draft.powerOptions.all { positive(it.toDouble()) }, "draft values")
        }
        val sessionIds = HashSet<Long>()
        backup.sessions.forEach {
            valid(sessionIds.add(it.id), "duplicate session ids")
            valid(positive(it.batteryCapacity), "session capacity")
            valid(percent(it.startPercent) && percent(it.targetPercent), "session percentages")
            valid(it.efficiency.isFinite() && it.efficiency > 0 &&
                it.efficiency <= 1.0 &&
                it.efficiency.toFloat() > 0, "session efficiency")
            valid(it.endPercent == null || percent(it.endPercent), "end percentage")
            valid(it.actualPercent == null || percent(it.actualPercent), "actual percentage")
            valid(it.segments.isNotEmpty() && it.segments.all { segment -> positive(segment.powerKw) } &&
                it.segments.all { segment -> segment.efficiency == null ||
                    (segment.efficiency.isFinite() && segment.efficiency > 0.0 && segment.efficiency <= 1.0) } &&
                (backup.formatVersion != 1 || (it.segments.all { segment -> segment.efficiency == null } &&
                    !it.calibrationEnabled)) &&
                (1 until it.segments.size).all { index ->
                    it.segments[index - 1].startedAt < it.segments[index].startedAt
                }, "session segments")
            valid(it.endedAt == null || it.endedAt >= it.segments.last().startedAt, "session end time")
        }
    }
}

private fun Decoder.backupPrimitive(): JsonPrimitive {
    val value = (this as JsonDecoder).decodeJsonElement()
    if (value !is JsonPrimitive || value.isString) throw SerializationException("Expected an unquoted JSON primitive")
    return value
}

internal object BackupDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("BackupDouble", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder) = decoder.backupPrimitive().doubleOrNull
        ?: throw SerializationException("Expected a JSON number")
}

internal object BackupFloatSerializer : KSerializer<Float> {
    override val descriptor = PrimitiveSerialDescriptor("BackupFloat", PrimitiveKind.FLOAT)
    override fun serialize(encoder: Encoder, value: Float) = encoder.encodeFloat(value)
    override fun deserialize(decoder: Decoder) = decoder.backupPrimitive().floatOrNull
        ?: throw SerializationException("Expected a JSON number")
}

internal object BackupLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("BackupLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder) = decoder.backupPrimitive().longOrNull
        ?: throw SerializationException("Expected a JSON integer")
}

internal object BackupIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("BackupInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder) = decoder.backupPrimitive().intOrNull
        ?: throw SerializationException("Expected a JSON integer")
}

internal object BackupBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("BackupBoolean", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder) = decoder.backupPrimitive().booleanOrNull
        ?: throw SerializationException("Expected a JSON boolean")
}

interface ManualBackupRepository {
    suspend fun exportBackup(appVersion: String): ManualBackup
    suspend fun restoreBackup(backup: ManualBackup)
}
