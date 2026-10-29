package com.zaiten.mixer.core.mediasync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeChunkParserTest {
    private fun feed(parser: LoungeChunkParser, chunk: String): List<LoungeEvent> {
        // Le serveur annonce la taille en caractères, saut de ligne final compris.
        parser.feed("${chunk.length + 1}")
        return parser.feed(chunk)
    }

    @Test fun sessionIdsAreRead() {
        val p = LoungeChunkParser()
        val events = feed(p, """[[0,["c","SID123","",8]],[1,["S","GS456"]]]""")
        assertEquals(listOf(LoungeEvent.SessionId("SID123"), LoungeEvent.GSession("GS456")), events)
        assertEquals(1, p.lastEventId)
    }

    @Test fun nowPlayingCarriesVideoPositionAndState() {
        val events = feed(LoungeChunkParser(), """[[5,["nowPlaying",{"videoId":"dQw4w9WgXcQ","currentTime":"42.5","state":"1"}]]]""")
        assertEquals(listOf(LoungeEvent.NowPlaying("dQw4w9WgXcQ", 42.5, RemoteState.PLAYING)), events)
    }

    @Test fun emptyNowPlayingMeansStopped() {
        // Cas réel couvert par pyytlounge (test_empty_now_playing_event).
        val events = feed(LoungeChunkParser(), """[[6,["nowPlaying",{"listId":"RQhvB4xAAAAA-hYxOY"}]]]""")
        assertEquals(listOf(LoungeEvent.NowPlaying(null, null, RemoteState.STOPPED)), events)
    }

    @Test fun stateChangeAndAdsAreMapped() {
        val events = feed(LoungeChunkParser(), """[[7,["onStateChange",{"currentTime":"10","state":"2","duration":"100"}]],[8,["onStateChange",{"currentTime":"10","state":"1081","duration":"100"}]]]""")
        assertEquals(listOf(LoungeEvent.StateChange(10.0, RemoteState.PAUSED), LoungeEvent.StateChange(10.0, RemoteState.BUFFERING)), events)
    }

    @Test fun chunkSplitOverLinesIsReassembled() {
        val p = LoungeChunkParser()
        val part1 = """[[9,["noop"]],"""
        val part2 = """[10,["loungeScreenDisconnected"]]]"""
        p.feed("${part1.length + 1 + part2.length + 1}")
        assertTrue(p.feed(part1).isEmpty())
        assertEquals(listOf(LoungeEvent.ScreenDisconnected), p.feed(part2))
    }
}

class SyncClockTest {
    @Test fun playingPositionAdvancesWithTime() {
        val remote = RemotePlayback("v", 10.0, RemoteState.PLAYING, receivedAtMs = 1_000)
        assertEquals(12.0, SyncClock.expectedPositionSec(remote, nowMs = 3_000, offsetMs = 0), 1e-9)
    }

    @Test fun pausedPositionStays() {
        val remote = RemotePlayback("v", 10.0, RemoteState.PAUSED, receivedAtMs = 1_000)
        assertEquals(10.0, SyncClock.expectedPositionSec(remote, nowMs = 9_000, offsetMs = 0), 1e-9)
    }

    @Test fun offsetDelaysLocalPlayback() {
        val remote = RemotePlayback("v", 10.0, RemoteState.PAUSED, receivedAtMs = 0)
        assertEquals(9.75, SyncClock.expectedPositionSec(remote, nowMs = 0, offsetMs = 250), 1e-9)
    }

    @Test fun smallDriftIsIgnored() {
        assertEquals(false, SyncClock.needsSeek(10.1, 10.0))
        assertEquals(true, SyncClock.needsSeek(10.5, 10.0))
    }
}
