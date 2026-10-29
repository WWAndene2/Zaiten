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

class ChannelGainsTest {
    private val track = AppTrack(1, "a", "A", volumePercent = 100)

    @Test fun bothChannelsByDefault() {
        assertEquals(ChannelGains(100, 100), track.channelGains(false, 100, 0))
    }

    @Test fun channelSelectsOneEar() {
        assertEquals(ChannelGains(100, 0), track.copy(channel = Channel.LEFT).channelGains(false, 100, 0))
        assertEquals(ChannelGains(0, 100), track.copy(channel = Channel.RIGHT).channelGains(false, 100, 0))
    }

    @Test fun balanceAttenuatesOppositeSide() {
        assertEquals(ChannelGains(50, 100), track.copy(balance = 50).channelGains(false, 100, 0))
        assertEquals(ChannelGains(100, 0), track.channelGains(false, 100, -100))
    }

    @Test fun trackAndGlobalBalanceCombine() {
        assertEquals(ChannelGains(25, 100), track.copy(balance = 50).channelGains(false, 100, 50))
    }

    @Test fun ruleRoundTripsWithoutSolo() {
        val t = track.copy(volumePercent = 70, muted = true, solo = true, channel = Channel.LEFT, balance = -20)
        val restored = AppTrack(1, "a", "A").withRule(t.rule())
        assertEquals(t.copy(solo = false), restored)
    }
}
