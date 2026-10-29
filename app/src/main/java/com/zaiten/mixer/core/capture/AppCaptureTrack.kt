package com.zaiten.mixer.core.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import com.zaiten.mixer.core.mixer.GainProcessor
import com.zaiten.mixer.core.mixer.MixerRepository

/**
 * Capture le son d'une seule appli (filtre par UID) et le rejoue avec le volume de sa piste.
 *
 * Limite connue (non contournable sans root) : AudioPlaybackCapture copie le son, il ne le
 * retire pas de la sortie d'origine. L'appli capturée doit donc être mise en sourdine de son
 * côté, sinon on entend l'original plus la copie. Le contournement est à valider sur appareil
 * réel au prototype (étape 1).
 */
class AppCaptureTrack(private val projection: MediaProjection, private val uid: Int) {
    @Volatile private var running = false
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
        // USAGE_ASSISTANT n'est pas capturable : la copie rejouée n'est pas recapturée en boucle.
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build(),
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

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
