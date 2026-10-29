package com.zaiten.mixer.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EffectsRepositoryTest {
    @Before fun reset() = EffectsRepository.restore(EffectSettings(), null, emptyList())

    @Test fun applyingPresetSetsSettingsAndName() {
        val film = BuiltInPresets.all.first { it.name == "Film" }
        EffectsRepository.apply(film)
        assertEquals(film.settings, EffectsRepository.settings.value)
        assertEquals("Film", EffectsRepository.activePreset.value)
    }

    @Test fun editingClearsActivePreset() {
        EffectsRepository.apply(BuiltInPresets.all.first())
        EffectsRepository.setEqBand(0, 20f)
        assertNull(EffectsRepository.activePreset.value)
        assertEquals(EffectSettings.EQ_MAX_DB, EffectsRepository.settings.value.eqDb[0])
    }

    @Test fun cannotOverwriteBuiltInPreset() {
        assertFalse(EffectsRepository.saveCurrentAs("Nuit"))
        assertTrue(EffectsRepository.saveCurrentAs("Mon réglage"))
        assertEquals(listOf("Mon réglage"), EffectsRepository.customPresets.value.map { it.name })
    }

    @Test fun renameFollowsActivePreset() {
        EffectsRepository.saveCurrentAs("A")
        assertTrue(EffectsRepository.rename("A", "B"))
        assertEquals("B", EffectsRepository.activePreset.value)
        assertFalse(EffectsRepository.rename("B", "Film"))
    }

    @Test fun importRenamesCollisions() {
        EffectsRepository.saveCurrentAs("Perso")
        EffectsRepository.import(listOf(Preset("Perso", EffectSettings()), Preset("Film", EffectSettings())))
        assertEquals(listOf("Perso", "Perso (2)", "Film (2)"), EffectsRepository.customPresets.value.map { it.name })
    }

    @Test fun nextPresetCycles() {
        EffectsRepository.apply(BuiltInPresets.all.last())
        EffectsRepository.applyNext()
        assertEquals(BuiltInPresets.all.first().name, EffectsRepository.activePreset.value)
    }
}
