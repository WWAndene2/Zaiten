package com.zaiten.mixer.core.effects

/** Réglages du mix final (§7.2, point 7), appliqués par le moteur natif (cpp/engine/EffectsChain.h). */
data class EffectSettings(
    /** Gain de chaque bande en dB, -12 à +12, aux fréquences [EQ_FREQUENCIES]. */
    val eqDb: List<Float> = List(EQ_FREQUENCIES.size) { 0f },
    /** 0 = compresseur contourné, 1 = compression maximale. */
    val compressor: Float = 0f,
    val limiter: Boolean = true,
    val mono: Boolean = false,
    /** Plafond du volume maître (preset Nuit) ; 200 = pas de plafond. */
    val masterCapPercent: Int = 200,
    /** Intensité de la spatialisation (§3.2) : 0 = arrêt, faible = stéréo élargi, 1 = 3D complet. */
    val spatial: Float = 0f,
    val room: Room = Room.LIVING_ROOM,
) {
    companion object {
        /** Doit correspondre à dsp::kEqFrequencies côté natif. */
        val EQ_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val EQ_MIN_DB = -12f
        const val EQ_MAX_DB = 12f
    }
}

/** Pièce virtuelle (§3.2) ; l'ordre doit correspondre à dsp::Room côté natif. */
enum class Room(val label: String) {
    NONE("Sans pièce"), SMALL("Petite salle"), LIVING_ROOM("Salon"), CINEMA("Cinéma"), OUTDOOR("Plein air"),
}

/** Un preset : un nom et des réglages, fourni (non supprimable) ou créé par l'utilisateur. */
data class Preset(val name: String, val settings: EffectSettings, val builtIn: Boolean = false)
