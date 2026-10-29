package com.zaiten.mixer.core.engine

import com.zaiten.mixer.core.effects.EffectSettings
import com.zaiten.mixer.core.mixer.ChannelGains

/**
 * Pont vers le moteur C++ (Oboe) : un seul flux de sortie basse latence qui mixe toutes les pistes
 * et applique la chaîne du mix final (cpp/engine/MixerEngine).
 */
object NativeMixerEngine {
    init {
        System.loadLibrary("mixer_engine")
    }

    fun start(): Boolean = nativeStart()
    fun stop() = nativeStop()

    /** PCM 16 bits stéréo entrelacé, 48 kHz. */
    fun write(uid: Int, samples: ShortArray, count: Int) = nativeWrite(uid, samples, count)

    /** Gains effectifs gauche/droite de la piste (muet, solo, maître, canal, balance), 0 à 200 %. */
    fun setGains(uid: Int, gains: ChannelGains) = nativeSetGains(uid, gains.left, gains.right)

    /** Réglages du mix final : égaliseur, compresseur, limiteur, mono. */
    fun setEffects(settings: EffectSettings) =
        nativeSetEffects(settings.eqDb.toFloatArray(), settings.compressor, settings.limiter, settings.mono)

    fun removeTrack(uid: Int) = nativeRemoveTrack(uid)

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeWrite(uid: Int, samples: ShortArray, count: Int)
    private external fun nativeSetGains(uid: Int, left: Int, right: Int)
    private external fun nativeSetEffects(eqDb: FloatArray, compressor: Float, limiter: Boolean, mono: Boolean)
    private external fun nativeRemoveTrack(uid: Int)
}
