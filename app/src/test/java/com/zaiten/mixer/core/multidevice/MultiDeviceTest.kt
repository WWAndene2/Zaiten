package com.zaiten.mixer.core.multidevice

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClockSyncTest {
    @Test fun noSampleNoOffset() = assertNull(ClockSync().offsetNs)

    @Test fun symmetricExchangeGivesExactOffset() {
        val c = ClockSync()
        // Source en avance de 1 s ; 10 ms à l'aller, 10 ms au retour.
        c.add(t0 = 0, t1 = 1_010_000_000, t2 = 20_000_000)
        assertEquals(1_000_000_000L, c.offsetNs)
    }

    @Test fun fastestExchangeWins() {
        val c = ClockSync()
        c.add(0, 1_090_000_000, 100_000_000) // Aller lent : estimation faussée de 40 ms.
        c.add(0, 1_005_000_000, 10_000_000)
        assertEquals(1_000_000_000L, c.offsetNs)
        assertEquals(10_000_000L, c.bestRttNs)
    }
}

class DriftCorrectorTest {
    @Test fun smallErrorPlays() = assertEquals(DriftCorrector.Action.Play, DriftCorrector.decide(1_000_000))
    @Test fun lateDropsBounded() = assertEquals(DriftCorrector.Action.Drop(48), DriftCorrector.decide(10_000_000))
    @Test fun earlyInserts() = assertEquals(DriftCorrector.Action.Insert(144 / 3), DriftCorrector.decide(-3_000_000))
    @Test fun hugeErrorResyncs() = assertEquals(DriftCorrector.Action.Resync, DriftCorrector.decide(-80_000_000))
}

class RoleMixerTest {
    private val stereo = setOf(SpeakerRole.LEFT, SpeakerRole.RIGHT)

    @Test fun stereoPairSplitsChannels() {
        assertEquals(1f to 1f, RoleMixer(SpeakerRole.LEFT).mix(1f, 0f, stereo))
        assertEquals(0f to 0f, RoleMixer(SpeakerRole.RIGHT).mix(1f, 0f, stereo))
    }

    @Test fun centerTakesTheMiddleFromFronts() {
        val roster = stereo + SpeakerRole.CENTER
        assertEquals(0f to 0f, RoleMixer(SpeakerRole.LEFT).mix(0.5f, 0.5f, roster))
        assertEquals(0.5f to 0.5f, RoleMixer(SpeakerRole.CENTER).mix(0.5f, 0.5f, roster))
    }

    @Test fun missingCenterFoldsIntoFronts() {
        assertEquals(0.5f to 0.5f, RoleMixer(SpeakerRole.LEFT).mix(0.5f, 0.5f, stereo))
    }

    @Test fun subGetsOnlyBass() {
        val sub = RoleMixer(SpeakerRole.SUB)
        var out = 0f
        repeat(48_000) { i -> out = sub.mix(if (i % 2 == 0) 0.5f else -0.5f, if (i % 2 == 0) 0.5f else -0.5f, stereo + SpeakerRole.SUB).first }
        assertEquals(0f, out, 0.02f) // 24 kHz : coupé par le passe-bas.
    }
}

class WireFormatTest {
    private fun roundTrip(m: Message): Message {
        val bytes = ByteArrayOutputStream()
        Message.write(DataOutputStream(bytes), m)
        return Message.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }

    @Test fun controlMessagesRoundTrip() {
        assertEquals(Message.Hello("Pixel", SpeakerRole.REAR_LEFT), roundTrip(Message.Hello("Pixel", SpeakerRole.REAR_LEFT)))
        assertEquals(Message.Pong(1, 2), roundTrip(Message.Pong(1, 2)))
        assertEquals(Message.Roster(setOf(SpeakerRole.LEFT, SpeakerRole.SUB)), roundTrip(Message.Roster(setOf(SpeakerRole.LEFT, SpeakerRole.SUB))))
    }

    @Test fun audioRoundTrips() {
        val back = roundTrip(Message.Audio(123L, shortArrayOf(1, -2, 3, 9), 3)) as Message.Audio
        assertEquals(123L, back.playAtNs)
        assertArrayEquals(shortArrayOf(1, -2, 3), back.samples)
    }
}
