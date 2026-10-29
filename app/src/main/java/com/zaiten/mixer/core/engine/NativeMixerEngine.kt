package com.zaiten.mixer.core.engine

/**
 * Pont vers le moteur C++ (Oboe) : un seul flux de sortie basse latence qui mixe toutes les pistes
 * et applique le limiteur de sortie (cpp/engine/MixerEngine).
 */
object NativeMixerEngine {
    init {
        System.loadLibrary("mixer_engine")
    }

    fun start(): Boolean = nativeStart()
    fun stop() = nativeStop()

    /** PCM 16 bits stéréo entrelacé, 48 kHz. */
    fun write(uid: Int, samples: ShortArray, count: Int) = nativeWrite(uid, samples, count)

    /** Volume effectif de la piste (muet, solo et maître déjà appliqués), 0 à 200 %. */
    fun setGain(uid: Int, percent: Int) = nativeSetGain(uid, percent)

    fun removeTrack(uid: Int) = nativeRemoveTrack(uid)

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeWrite(uid: Int, samples: ShortArray, count: Int)
    private external fun nativeSetGain(uid: Int, percent: Int)
    private external fun nativeRemoveTrack(uid: Int)
}
