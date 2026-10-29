package com.zaiten.mixer.core.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.SystemClock
import com.zaiten.mixer.core.engine.NativeMixerEngine
import com.zaiten.mixer.core.mixer.LevelMeter
import com.zaiten.mixer.core.mixer.MixerRepository
import kotlin.math.abs

/**
 * Capture le son d'une seule appli (filtre par UID) et le pousse dans le moteur natif, qui le
 * mixe et le rejoue. La capture reste en Kotlin : AudioPlaybackCapture n'existe pas dans Oboe.
 *
 * AudioPlaybackCapture copie le son sans le retirer de la sortie d'origine : OriginalMuter coupe
 * le volume média pour faire taire les originaux, et le moteur rejoue sur un autre flux.
 */
class AppCaptureTrack(private val projection: MediaProjection, private val uid: Int) {
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Instant (elapsedRealtime) du dernier tampon capturé non silencieux, lu par OriginalMuter. */
    @Volatile var lastSoundAtMs = 0L
        private set

    @SuppressLint("MissingPermission") // RECORD_AUDIO est demandée avant de démarrer le service.
    fun start() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUid(uid)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        )
        // Tampon système large (anti-perte), mais lectures par blocs réguliers de 10 ms : des blocs
        // plus longs que la file du moteur (30 ms) l'obligeaient à jeter du son, d'où des grésillements.
        val bufferBytes = maxOf(minBytes * 2, CHUNK_SAMPLES * 2 * 4)
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        running = true
        thread = Thread({
            val buffer = ShortArray(CHUNK_SAMPLES)
            record.startRecording()
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val peak = peakOf(buffer, read)
                LevelMeter.report(uid, peak)
                if (peak * 32768 > SILENCE_THRESHOLD) lastSoundAtMs = SystemClock.elapsedRealtime()
                NativeMixerEngine.setGains(uid, MixerRepository.currentGains(uid))
                NativeMixerEngine.write(uid, buffer, read)
            }
            record.stop()
            record.release()
            NativeMixerEngine.removeTrack(uid)
            LevelMeter.forget(uid)
        }, "capture-$uid").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun peakOf(buffer: ShortArray, count: Int): Float {
        var max = 0
        for (i in 0 until count) max = maxOf(max, abs(buffer[i].toInt()))
        return max / 32768f
    }

    private companion object {
        /** Doit correspondre à MixerEngine::kSampleRate côté natif. */
        const val SAMPLE_RATE = 48_000
        /** 10 ms de stéréo 16 bits : 480 images × 2 canaux. */
        const val CHUNK_SAMPLES = SAMPLE_RATE / 100 * 2
        /** Environ -50 dBFS : en dessous, le tampon est considéré comme silencieux. */
        const val SILENCE_THRESHOLD = 100
    }
}
