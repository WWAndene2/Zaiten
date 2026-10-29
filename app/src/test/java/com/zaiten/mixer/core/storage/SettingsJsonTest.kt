package com.zaiten.mixer.core.storage

import com.zaiten.mixer.core.effects.BuiltInPresets
import com.zaiten.mixer.core.mixer.Channel
import com.zaiten.mixer.core.mixer.TrackRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsJsonTest {
    @Test fun presetsRoundTrip() {
        val text = SettingsJson.presetsToJson(BuiltInPresets.all)
        val back = SettingsJson.presetsFromJson(text)!!
        assertEquals(BuiltInPresets.all.map { it.name to it.settings }, back.map { it.name to it.settings })
    }

    @Test fun foreignFileIsRejected() {
        assertNull(SettingsJson.presetsFromJson("""{"presets": []}"""))
        assertNull(SettingsJson.presetsFromJson("pas du json"))
    }

    @Test fun rulesRoundTrip() {
        val rules = mapOf("com.spotify.music" to TrackRule(70, true, Channel.LEFT, -30, priority = true))
        assertEquals(rules, SettingsJson.rulesFromJson(SettingsJson.rulesToJson(rules)))
    }
}
