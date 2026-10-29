package com.zaiten.mixer.core.mediasync

import kotlin.math.abs

/**
 * Calcule où la lecture locale doit en être pour coller à l'écran, et quand se recaler.
 *
 * [offsetMs] compense le retard d'affichage de la TV et du casque (§2.3, réglage au pas de 10 ms) :
 * positif = le son du téléphone est retardé pour attendre la TV.
 */
object SyncClock {
    /** En dessous, l'écart est inaudible ou dû à l'imprécision du lecteur : on ne touche à rien. */
    const val DRIFT_TOLERANCE_SEC = 0.25

    fun expectedPositionSec(remote: RemotePlayback, nowMs: Long, offsetMs: Int): Double {
        val elapsed = if (remote.state == RemoteState.PLAYING) (nowMs - remote.receivedAtMs) / 1000.0 else 0.0
        return (remote.positionSec + elapsed - offsetMs / 1000.0).coerceAtLeast(0.0)
    }

    fun needsSeek(localSec: Double, expectedSec: Double): Boolean = abs(localSec - expectedSec) > DRIFT_TOLERANCE_SEC
}
