package com.zaiten.mixer.core.mixer

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTrackTest {
    @Test fun percentIsClamped() {
        assertEquals(200, VolumeRange.clamp(500))
        assertEquals(0, VolumeRange.clamp(-3))
    }

    @Test fun soloSilencesOtherTracks() {
        val a = AppTrack(1, "a", "A", solo = true)
        val b = AppTrack(2, "b", "B")
        assertEquals(100, a.effectivePercent(anySolo = true, masterPercent = 100))
        assertEquals(0, b.effectivePercent(anySolo = true, masterPercent = 100))
    }

    @Test fun muteSilencesTrack() {
        assertEquals(0, AppTrack(1, "a", "A", muted = true).effectivePercent(anySolo = false, masterPercent = 100))
    }

    @Test fun masterScalesAndIsClamped() {
        assertEquals(50, AppTrack(1, "a", "A", volumePercent = 100).effectivePercent(false, 50))
        assertEquals(200, AppTrack(1, "a", "A", volumePercent = 150).effectivePercent(false, 150))
    }
}
