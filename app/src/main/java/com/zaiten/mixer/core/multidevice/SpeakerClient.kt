package com.zaiten.mixer.core.multidevice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Côté enceinte (§2.7) : se connecte à une source, synchronise son horloge, et joue son canal
 * à l'instant visé, en rattrapant la dérive (DriftCorrector).
 *
 * [volume] (0 à 2) et [offsetMs] (réglage manuel du §2.7) sont lus à chaque bloc.
 */
class SpeakerClient(
    private val role: SpeakerRole,
    private val volume: () -> Float,
    private val offsetMs: () -> Int,
    private val onDisconnected: () -> Unit,
) {
    @Volatile private var running = false
    private var socket: Socket? = null
    private val clock = ClockSync()
    @Volatile private var roster: Set<SpeakerRole> = setOf(role)
    private val queue = ArrayBlockingQueue<Message.Audio>(QUEUE_BLOCKS)

    /** Incertitude actuelle de l'horloge (aller-retour du meilleur échange), en ms. */
    val clockUncertaintyMs: Double? get() = clock.bestRttNs?.let { it / 2e6 }

    fun connect(host: String, port: Int) {
        running = true
        thread(name = "zaiten-speaker") {
            try {
                val s = Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
                socket = s
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val send = { m: Message -> synchronized(out) { Message.write(out, m) } }
                send(Message.Hello(Build.MODEL, role))
                thread(name = "zaiten-ping") {
                    var n = 0
                    while (running) {
                        runCatching { send(Message.Ping(System.nanoTime())) }
                        // Rafale au départ pour une horloge vite fiable, puis entretien.
                        Thread.sleep(if (n++ < BURST_PINGS) BURST_INTERVAL_MS else PING_INTERVAL_MS)
                    }
                }
                thread(name = "zaiten-play") { playLoop() }
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                while (running) {
                    when (val m = Message.read(input)) {
                        is Message.Pong -> clock.add(m.t0, m.t1, System.nanoTime())
                        is Message.Roster -> roster = m.roles + role
                        is Message.Audio -> if (!queue.offer(m)) { queue.poll(); queue.offer(m) }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Source perdue.
            } finally {
                running = false
                runCatching { socket?.close() }
                onDisconnected()
            }
        }
    }

    fun disconnect() {
        running = false
        runCatching { socket?.close() }
    }

    private fun playLoop() {
        val minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minBytes * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val mixer = RoleMixer(role)
        val timestamp = AudioTimestamp()
        var written = 0L
        val out = ShortArray(MAX_BLOCK_SAMPLES)
        track.play()
        try {
            while (running) {
                val block = queue.poll(QUEUE_WAIT_MS, TimeUnit.MILLISECONDS) ?: continue
                val target = clock.sourceToLocal(block.playAtNs)?.plus(offsetMs() * 1_000_000L) ?: continue
                // Instant où la prochaine image écrite sortira : horodatage matériel + images en attente.
                val nextOutNs = if (track.getTimestamp(timestamp)) {
                    timestamp.nanoTime + (written - timestamp.framePosition) * NS_PER_SECOND / SAMPLE_RATE
                } else {
                    System.nanoTime() + (written * NS_PER_SECOND / SAMPLE_RATE).coerceAtMost(STARTUP_LATENCY_NS)
                }
                val frames = block.count / 2
                var start = 0
                var repeat = 0
                when (val action = DriftCorrector.decide(nextOutNs - target)) {
                    DriftCorrector.Action.Play -> {}
                    is DriftCorrector.Action.Drop -> start = action.frames
                    is DriftCorrector.Action.Insert -> repeat = action.frames
                    DriftCorrector.Action.Resync -> {
                        if (nextOutNs > target) continue // En retard : on saute le bloc pour rattraper.
                        // En avance : silence jusqu'à l'instant visé (borné à un tampon).
                        val silence = ((target - nextOutNs) * SAMPLE_RATE / NS_PER_SECOND).toInt().coerceAtMost(SAMPLE_RATE / 2)
                        track.write(ShortArray(silence * 2), 0, silence * 2)
                        written += silence
                    }
                }
                val gain = volume()
                var n = 0
                fun emit(frame: Int) {
                    val (l, r) = mixer.mix(block.samples[2 * frame] / 32768f, block.samples[2 * frame + 1] / 32768f, roster)
                    out[n++] = toShort(l * gain)
                    out[n++] = toShort(r * gain)
                }
                repeat(repeat) { emit(start) }
                for (f in start until frames) emit(f)
                track.write(out, 0, n)
                written += n / 2
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    private fun toShort(x: Float) = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private companion object {
        const val SAMPLE_RATE = SourceServer.SAMPLE_RATE
        const val NS_PER_SECOND = 1_000_000_000L
        /** ~1 s de blocs de 10 ms. */
        const val QUEUE_BLOCKS = 100
        const val QUEUE_WAIT_MS = 100L
        const val MAX_BLOCK_SAMPLES = SourceServer.BLOCK_SAMPLES * 2 + DriftCorrector.MAX_STEP_FRAMES * 2
        const val CONNECT_TIMEOUT_MS = 5_000
        const val BURST_PINGS = 20
        const val BURST_INTERVAL_MS = 50L
        const val PING_INTERVAL_MS = 1_000L
        /** Avant le premier horodatage matériel : latence de sortie typique supposée. */
        const val STARTUP_LATENCY_NS = 40_000_000L
    }
}
