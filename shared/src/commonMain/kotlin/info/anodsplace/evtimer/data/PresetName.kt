package info.anodsplace.evtimer.data

const val MAX_PRESET_NAME_CODE_POINTS = 32

enum class PresetNameError {
    EMPTY,
    TOO_LONG,
    ALREADY_EXISTS
}

data class PresetNameValidation(
    val name: String,
    val error: PresetNameError?
) {
    val isValid: Boolean get() = error == null
}

/**
 * Trims and validates a name without changing its Unicode content. Names are limited to
 * [MAX_PRESET_NAME_CODE_POINTS] Unicode code points; overlong input is rejected rather than
 * truncated, so surrogate pairs and joined emoji sequences are never split.
 */
fun normalizeAndValidatePresetName(name: String, existingNames: Collection<String>): PresetNameValidation {
    val normalized = name.trim()
    val error = when {
        normalized.isEmpty() -> PresetNameError.EMPTY
        normalized.codePointCount() > MAX_PRESET_NAME_CODE_POINTS -> PresetNameError.TOO_LONG
        normalized.equals("Default", ignoreCase = true) ||
            existingNames.any { it.equals(normalized, ignoreCase = true) } -> PresetNameError.ALREADY_EXISTS
        else -> null
    }
    return PresetNameValidation(normalized, error)
}

private fun String.codePointCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val first = this[index]
        index += if (first.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}
