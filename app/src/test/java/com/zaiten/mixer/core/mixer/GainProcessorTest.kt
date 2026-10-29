package com.zaiten.mixer.core.mixer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GainProcessorTest {
    @Test fun unityGainKeepsSamples() {
        val s = shortArrayOf(0, 1000, -1000, 32767)
        GainProcessor.process(s, s.size, 100)
        assertEquals(listOf<Short>(0, 1000, -1000, 32767), s.toList())
    }

    @Test fun zeroPercentSilences() {
        val s = shortArrayOf(1000, -2000)
        GainProcessor.process(s, s.size, 0)
        assertEquals(listOf<Short>(0, 0), s.toList())
    }

    @Test fun boostIsLimitedWithoutHardClip() {
        // 20000 × 200 % = 40000 dépasserait la pleine échelle : le limiteur doit rester sous l'écrêtage.
        val s = shortArrayOf(20000, -20000)
        GainProcessor.process(s, s.size, 200)
        assertTrue(s[0] in 26215..32766)
        assertTrue(s[1] in -32766..-26215)
    }

    @Test fun percentIsClamped() {
        assertEquals(200, GainProcessor.clampPercent(500))
        assertEquals(0, GainProcessor.clampPercent(-3))
    }

    @Test fun soloSilencesOtherTracks() {
        val a = AppTrack(1, "a", "A", solo = true)
        val b = AppTrack(2, "b", "B")
        assertEquals(100, a.effectivePercent(anySolo = true, masterPercent = 100))
        assertEquals(0, b.effectivePercent(anySolo = true, masterPercent = 100))
        assertEquals(0, b.copy(muted = true).effectivePercent(anySolo = false, masterPercent = 100))
        assertEquals(200, AppTrack(3, "c", "C", volumePercent = 150).effectivePercent(false, 150))
    }
}
