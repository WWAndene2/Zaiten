package com.zaiten.mixer.core.multidevice

/**
 * Décide comment rattraper l'écart entre l'instant où un bloc va réellement sortir et l'instant visé.
 * Petites corrections (quelques images par bloc, inaudibles) pour la dérive lente des horloges ;
 * remise à zéro si l'écart devient grand (réseau coupé, appareil en veille).
 */
object DriftCorrector {
    sealed interface Action {
        data object Play : Action
        /** Retirer [frames] images de ce bloc (en retard). */
        data class Drop(val frames: Int) : Action
        /** Répéter [frames] images (en avance). */
        data class Insert(val frames: Int) : Action
        data object Resync : Action
    }

    /** En dessous de 2 ms, l'écart est sous l'objectif de 5 ms entre enceintes (§2.7) : on laisse. */
    const val TOLERANCE_NS = 2_000_000L
    const val RESYNC_NS = 50_000_000L
    /** Au plus 1 ms rattrapée par bloc de 10 ms : 10 % de vitesse, sans clic notable. */
    const val MAX_STEP_FRAMES = 48
    private const val NS_PER_FRAME = 1_000_000_000L / 48_000

    /** [errorNs] > 0 : le bloc sortira en retard sur l'instant visé. */
    fun decide(errorNs: Long): Action = when {
        errorNs > RESYNC_NS || errorNs < -RESYNC_NS -> Action.Resync
        errorNs > TOLERANCE_NS -> Action.Drop(minOf(MAX_STEP_FRAMES.toLong(), errorNs / NS_PER_FRAME).toInt())
        errorNs < -TOLERANCE_NS -> Action.Insert(minOf(MAX_STEP_FRAMES.toLong(), -errorNs / NS_PER_FRAME).toInt())
        else -> Action.Play
    }
}
