package info.anodsplace.evtimer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PresetConfigurationTest {
    private val custom = ChargingPreset("custom", "Default", ChargingSettings.DEFAULT)
    private val home = ChargingPreset("home", "Home", ChargingSettings.DEFAULT.copy(
        batteryCapacity = 60f, chargingPower = 7f, maxPercent = 80f
    ))
    private val initial = PresetConfiguration(listOf(custom, home), custom.id, custom.settings.copy(startPercent = 25f))
    private val selected get() = initial.reduce(PresetEvent.Select(home.id))
    private val edited get() = selected.reduce(PresetEvent.Edit(selected.settings.withAddedPower(50f)))

    @Test fun applyingPresetPreservesSessionStart() {
        assertEquals(home.id, selected.selectedId)
        assertEquals(home.settings.copy(startPercent = 25f), selected.settings)
        assertFalse(selected.isEdited)
    }

    @Test fun editingNamedPresetKeepsSelectionAndOriginalAndShowsBanner() {
        assertEquals(home.id, edited.selectedId)
        assertEquals(home, edited.selected)
        assertTrue(edited.isEdited)
        assertEquals(50f, edited.settings.chargingPower)
    }

    @Test fun updateSavesWorkingValuesAndClearsBanner() {
        val updated = edited.reduce(PresetEvent.Update)
        assertEquals(edited.settings, updated.selected.settings)
        assertFalse(updated.isEdited)
    }

    @Test fun saveNewSelectsNewPresetAndKeepsOriginal() {
        val saved = edited.reduce(PresetEvent.SaveNew("new", "\u26A1 Highway"))
        assertEquals("new", saved.selectedId)
        assertEquals(edited.settings, saved.selected.settings)
        assertEquals(home, saved.presets.first { it.id == home.id })
        assertFalse(saved.isEdited)
        assertFailsWith<IllegalArgumentException> { edited.reduce(PresetEvent.SaveNew("new", "   ")) }
    }

    @Test
    fun presetNamesTrimAndRejectDuplicatesRegardlessOfCase() {
        assertEquals("Weekend", normalizeAndValidatePresetName("  Weekend  ", emptyList()).name)
        assertEquals(PresetNameError.ALREADY_EXISTS,
            normalizeAndValidatePresetName("hOME", listOf("Home")).error)
        assertEquals(PresetNameError.ALREADY_EXISTS,
            normalizeAndValidatePresetName("home", listOf("  Home  ")).error)
        assertEquals(PresetNameError.ALREADY_EXISTS,
            normalizeAndValidatePresetName("default", emptyList()).error)
        assertFailsWith<IllegalArgumentException> { edited.reduce(PresetEvent.SaveNew("new", "default")) }
    }

    @Test
    fun emojiNamesCountUnicodeCodePointsWithoutSplittingSequences() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val flag = "\uD83C\uDDEE\uD83C\uDDF1"
        val skinTone = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(family, normalizeAndValidatePresetName(family, emptyList()).name)
        assertEquals(flag, normalizeAndValidatePresetName(flag, emptyList()).name)
        assertEquals(skinTone, normalizeAndValidatePresetName(skinTone, emptyList()).name)
        assertEquals("a".repeat(27) + family,
            normalizeAndValidatePresetName("a".repeat(27) + family, emptyList()).name)
        assertEquals("a".repeat(30) + flag,
            normalizeAndValidatePresetName("a".repeat(30) + flag, emptyList()).name)
        assertEquals("a".repeat(30) + skinTone,
            normalizeAndValidatePresetName("a".repeat(30) + skinTone, emptyList()).name)
        assertEquals(PresetNameError.TOO_LONG,
            normalizeAndValidatePresetName("a".repeat(28) + family, emptyList()).error)
    }

    @Test fun resetRestoresPresetButNotSessionStart() {
        val reset = edited.reduce(PresetEvent.Reset)
        assertEquals(selected.settings, reset.settings)
        assertFalse(reset.isEdited)
    }

    @Test fun editingBackClearsBannerAndStartIsNeverAPresetEdit() {
        assertFalse(edited.reduce(PresetEvent.Edit(selected.settings)).isEdited)
        assertFalse(selected.reduce(PresetEvent.Edit(selected.settings.copy(startPercent = 30f))).isEdited)
    }

    @Test fun deletingSelectedPresetAppliesCustom() {
        val deleted = edited.reduce(PresetEvent.Delete(home.id))
        assertEquals(custom.id, deleted.selectedId)
        assertEquals(custom.settings.copy(startPercent = 25f), deleted.settings)
        assertFalse(deleted.isEdited)
        assertFailsWith<IllegalArgumentException> { selected.reduce(PresetEvent.Delete(custom.id)) }
        assertFailsWith<IllegalArgumentException> { selected.reduce(PresetEvent.Delete("missing")) }
    }

    @Test fun customEditsAreSavedDirectlyAndCanBeSavedAsNamedPreset() {
        val changed = initial.reduce(PresetEvent.Edit(initial.settings.withAddedPower(50f)))
        assertEquals(changed.settings, changed.selected.settings)
        assertFalse(changed.isEdited)
        val saved = changed.reduce(PresetEvent.SaveNew("new", "Weekend"))
        assertEquals(changed.selected, saved.presets.first { it.id == custom.id })
        assertEquals("new", saved.selectedId)
    }
}
