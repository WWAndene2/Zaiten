package com.zaiten.mixer.core.multidevice

/** Place d'un appareil dans le système (§2.7). */
enum class SpeakerRole(val label: String) {
    LEFT("Gauche"), RIGHT("Droite"), CENTER("Centre"), SUB("Caisson"), REAR_LEFT("Arrière gauche"), REAR_RIGHT("Arrière droite"), FULL("Stéréo complète"),
}

/**
 * Calcule ce qu'une enceinte joue à partir du stéréo reçu, selon son rôle et les rôles présents
 * dans le groupe (§2.7). Upmix mid/side comme pour la spatialisation (§3.1) : centre = milieu,
 * arrière = côtés (G − D). Un rôle absent est replié sur les autres plutôt que perdu (tolérance) :
 * sans centre, le milieu reste dans G et D ; sans arrière, l'ambiance reste à l'avant ;
 * sans caisson, les basses restent partout.
 */
class RoleMixer(private val role: SpeakerRole) {
    private var bass = 0f

    /** Renvoie la paire (gauche, droite) à jouer sur cet appareil. */
    fun mix(left: Float, right: Float, roster: Set<SpeakerRole>): Pair<Float, Float> {
        val mid = 0.5f * (left + right)
        val side = 0.5f * (left - right)
        bass += BASS_COEFF * (mid - bass) // Passe-bas ~120 Hz, à 48 kHz.
        val hasCenter = SpeakerRole.CENTER in roster
        val hasRears = SpeakerRole.REAR_LEFT in roster || SpeakerRole.REAR_RIGHT in roster
        val hasSub = SpeakerRole.SUB in roster
        val frontMid = if (hasCenter) 0f else mid
        val frontSide = if (hasRears) side * FRONT_SIDE_WITH_REARS else side
        // Avec un caisson, les petites enceintes lâchent les basses qu'elles rendent mal.
        val frontBass = if (hasSub) bass else 0f
        val out = when (role) {
            SpeakerRole.FULL -> left to right
            SpeakerRole.LEFT -> (frontMid + frontSide - frontBass).let { it to it }
            SpeakerRole.RIGHT -> (frontMid - frontSide - frontBass).let { it to it }
            SpeakerRole.CENTER -> (mid - frontBass).let { it to it }
            SpeakerRole.SUB -> bass to bass
            SpeakerRole.REAR_LEFT -> side.let { it to it }
            SpeakerRole.REAR_RIGHT -> (-side).let { it to it }
        }
        return out
    }

    private companion object {
        /** 1 − exp(−2π × 120 / 48 000). */
        const val BASS_COEFF = 0.0156f
        /** Avec des arrières, l'ambiance est partagée : l'avant en garde la moitié. */
        const val FRONT_SIDE_WITH_REARS = 0.5f
    }
}
