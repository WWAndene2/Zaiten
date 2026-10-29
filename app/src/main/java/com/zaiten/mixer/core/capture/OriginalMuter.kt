package com.zaiten.mixer.core.capture

import android.media.AudioManager
import android.os.SystemClock
import com.zaiten.mixer.core.mixer.MixerRepository
import com.zaiten.mixer.core.mixer.OriginalSoundMode
import kotlinx.coroutines.delay

/**
 * Fait taire le son d'origine des applis capturées, sans root ni Shizuku.
 *
 * Principe : la capture (sous-mix distant) est prise avant le volume du flux média ; on coupe donc
 * le volume média, ce qui tait les originaux tandis que la capture continue, et la copie réglée est
 * rejouée sur un flux indépendant (voir MixerEngine::start côté natif).
 *
 * Ce comportement n'est pas garanti par la documentation Android : il est vérifié à l'exécution.
 * Au premier son capturé, on coupe le média puis on regarde si la capture entend encore quelque
 * chose. Sinon, on rétablit le volume et on bascule en [OriginalSoundMode.ORIGINAL_STILL_PLAYS].
 * Faux négatif possible si la lecture est mise en pause pendant les 2 s du test.
 *
 * Limite : une touche de volume pendant la session remonte le volume média et refait entendre les
 * originaux ; l'interception des touches relève de l'étape 6 (panneau de volume).
 */
class OriginalMuter(private val audio: AudioManager, private val lastSoundAt: () -> Long) {
    private var muted = false

    suspend fun run() {
        while (lastSoundAt() == 0L) delay(POLL_MS)
        val mutedAt = SystemClock.elapsedRealtime()
        setMuted(true)
        delay(VERIFY_MS)
        if (lastSoundAt() > mutedAt + SETTLE_MS) {
            MixerRepository.setOriginalSoundMode(OriginalSoundMode.MUTED_BY_APP)
        } else {
            setMuted(false)
            MixerRepository.setOriginalSoundMode(OriginalSoundMode.ORIGINAL_STILL_PLAYS)
        }
    }

    fun restore() = setMuted(false)

    private fun setMuted(mute: Boolean) {
        if (muted == mute) return
        val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        muted = mute
    }

    private companion object {
        const val POLL_MS = 250L
        const val VERIFY_MS = 2_000L
        /** Laisse passer les tampons déjà en vol au moment de la coupure. */
        const val SETTLE_MS = 300L
    }
}
