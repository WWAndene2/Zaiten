package com.zaiten.mixer.core.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.SystemClock
import com.zaiten.mixer.core.mixer.GainProcessor
import com.zaiten.mixer.core.mixer.MixerRepository
import kotlin.math.abs

/**
 * Capture le son d'une seule appli (filtre par UID) et le rejoue avec le volume de sa piste.
 *
 * AudioPlaybackCapture copie le son sans le retirer de la sortie d'origine : OriginalMuter coupe
 * le volume média pour faire taire les originaux, et la copie est rejouée sur un autre flux.
 */
class AppCaptureTrack(private val projection: MediaProjection, private val uid: Int) {
    @Volatile private var running = false

    /** Instant (elapsedRealtime) du dernier tampon capturé non silencieux, lu par OriginalMuter. */
    @Volatile var lastSoundAtMs = 0L
        private set
    private var thread: Thread? = null

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
        val bufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        // REPLAY_USAGE n'est pas capturable (pas de boucle) et ne dépend pas du volume média,
        // que OriginalMuter met à zéro.
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(REPLAY_USAGE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        running = true
        thread = Thread({
            val buffer = ShortArray(bufferBytes / 2)
            record.startRecording()
            track.play()
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                if (isAudible(buffer, read)) lastSoundAtMs = SystemClock.elapsedRealtime()
                GainProcessor.process(buffer, read, MixerRepository.currentPercent(uid))
                track.write(buffer, 0, read)
            }
            record.stop(); record.release()
            track.stop(); track.release()
        }, "capture-$uid").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun isAudible(buffer: ShortArray, count: Int): Boolean {
        for (i in 0 until count) if (abs(buffer[i].toInt()) > SILENCE_THRESHOLD) return true
        return false
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        /** Environ -50 dBFS : en dessous, le tampon est considéré comme silencieux. */
        const val SILENCE_THRESHOLD = 100

        /**
         * Flux de la copie rejouée. Hypothèse à valider sur appareil : en mode normal (hors appel),
         * ce flux suit la sortie casque/Bluetooth A2DP comme le média. Sans casque, certains
         * appareils pourraient le router vers l'écouteur d'appel au lieu du haut-parleur.
         */
        const val REPLAY_USAGE = AudioAttributes.USAGE_VOICE_COMMUNICATION
    }
}
