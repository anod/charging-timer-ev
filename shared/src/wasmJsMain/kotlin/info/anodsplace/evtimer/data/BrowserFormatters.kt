@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package info.anodsplace.evtimer.data

@JsFun("(value, min, max) => value.toLocaleString(undefined, {minimumFractionDigits:min, maximumFractionDigits:max})")
private external fun localeNumber(value: Double, min: Int, max: Int): String

@JsFun("(epoch) => new Date(epoch).toLocaleString()")
private external fun localDate(epoch: Double): String

actual fun formatLocaleNumber(value: Float, minimumFractionDigits: Int, maximumFractionDigits: Int): String =
    localeNumber(value.toDouble(), minimumFractionDigits, maximumFractionDigits)

actual fun formatSessionDate(epochMilliseconds: Long): String = localDate(epochMilliseconds.toDouble())
