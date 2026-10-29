package com.zaiten.mixer.core.mixer

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Applique un volume de piste de 0 à 200 % (cahier des charges §2.5) à des échantillons PCM 16 bits.
 *
 * Au-delà de 100 %, un limiteur doux (tanh) au-dessus de [LIMITER_THRESHOLD] évite l'écrêtage dur.
 * C'est un limiteur instantané sans attaque/relâchement : suffisant pour le prototype, à remplacer
 * par le limiteur du moteur Oboe (§5) quand la chaîne de traitement §7.2 sera implémentée.
 */
object GainProcessor {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 200
    const val WARNING_PERCENT = 150

    private const val LIMITER_THRESHOLD = 0.8f

    fun clampPercent(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun process(samples: ShortArray, count: Int, percent: Int) {
        val gain = clampPercent(percent) / 100f
        val limit = gain > 1f
        for (i in 0 until count) {
            var x = samples[i] / 32768f * gain
            if (limit) x = softLimit(x)
            samples[i] = (x * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    internal fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= LIMITER_THRESHOLD) return x
        val headroom = 1f - LIMITER_THRESHOLD
        val shaped = LIMITER_THRESHOLD + headroom * tanh((a - LIMITER_THRESHOLD) / headroom)
        return if (x < 0) -shaped else shaped
    }
}
