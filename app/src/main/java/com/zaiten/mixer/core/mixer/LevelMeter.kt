package com.zaiten.mixer.core.mixer

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Niveau crête récent de chaque appli capturée (vu-mètre §7.3, visualiseur §4.2) ; sert aussi
 * au ducking, qui doit savoir si l'appli prioritaire joue en ce moment.
 */
object LevelMeter {
    private class Reading(val peak: Float, val atMs: Long)

    private val readings = ConcurrentHashMap<Int, Reading>()

    /** Appelé par le thread de capture à chaque bloc ; [peak] de 0 à 1. */
    fun report(uid: Int, peak: Float) {
        readings[uid] = Reading(peak, SystemClock.elapsedRealtime())
    }

    /** Niveau affichable : retombe à zéro si l'appli n'envoie plus rien. */
    fun level(uid: Int): Float {
        val r = readings[uid] ?: return 0f
        return if (SystemClock.elapsedRealtime() - r.atMs > STALE_MS) 0f else r.peak
    }

    fun isAudible(uid: Int): Boolean = level(uid) > AUDIBLE_PEAK

    fun forget(uid: Int) {
        readings.remove(uid)
    }

    private const val STALE_MS = 300L
    /** Environ -50 dBFS, comme le seuil de silence de la capture. */
    private const val AUDIBLE_PEAK = 0.003f
}
