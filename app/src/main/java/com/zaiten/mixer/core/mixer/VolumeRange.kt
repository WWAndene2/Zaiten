package com.zaiten.mixer.core.mixer

/**
 * Bornes du volume de piste et du volume maître (§2.5). Le gain et le limiteur eux-mêmes sont
 * appliqués par le moteur natif (cpp/engine/Dsp.h), qui borne aussi les valeurs reçues.
 */
object VolumeRange {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 200
    const val WARNING_PERCENT = 150

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
}
