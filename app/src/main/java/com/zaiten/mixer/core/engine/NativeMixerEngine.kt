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

    /**
     * Réglages du mix final : égaliseur, spatialisation, compresseur, limiteur, mono.
     * [spatialAllowed] faux (pas de casque) coupe la 3D, qui ne fonctionne qu'au casque (§3.3).
     */
    fun setEffects(settings: EffectSettings, spatialAllowed: Boolean) = nativeSetEffects(
        settings.eqDb.toFloatArray(),
        settings.compressor,
        settings.limiter,
        settings.mono,
        if (spatialAllowed) settings.spatial else 0f,
        settings.room.ordinal,
        settings.preampDb,
        settings.crossfeed,
    )

    fun removeTrack(uid: Int) = nativeRemoveTrack(uid)

    /**
     * Copie du mix final pour le groupe d'enceintes (§2.7). Renvoie l'index de la première image
     * copiée ; à appeler depuis le thread qui lira ensuite la copie ([readTap]).
     */
    fun setTapEnabled(enabled: Boolean): Long = nativeSetTapEnabled(enabled)

    /** Lit le mix copié (stéréo entrelacé 16 bits) ; renvoie le nombre d'échantillons lus. */
    fun readTap(out: ShortArray): Int = nativeReadTap(out)

    /**
     * Horloge de la copie : [TapClock.timeNs] (System.nanoTime) est l'instant où l'image numéro
     * [TapClock.frames] sort du téléphone ; les images suivantes s'en déduisent à 48 kHz.
     */
    fun tapClock(): TapClock = LongArray(2).also(::nativeTapClock).let { TapClock(it[0], it[1]) }

    /** Retarde le son local pour qu'il sorte en même temps que celui des enceintes. */
    fun setOutputDelayMs(ms: Int) = nativeSetOutputDelayMs(ms)

    data class TapClock(val timeNs: Long, val frames: Long)

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeWrite(uid: Int, samples: ShortArray, count: Int)
    private external fun nativeSetGains(uid: Int, left: Int, right: Int)
    private external fun nativeSetEffects(
        eqDb: FloatArray,
        compressor: Float,
        limiter: Boolean,
        mono: Boolean,
        spatial: Float,
        room: Int,
        preampDb: Float,
        crossfeed: Boolean,
    )
    private external fun nativeRemoveTrack(uid: Int)
    private external fun nativeSetTapEnabled(enabled: Boolean): Long
    private external fun nativeReadTap(out: ShortArray): Int
    private external fun nativeTapClock(out: LongArray)
    private external fun nativeSetOutputDelayMs(ms: Int)
}
