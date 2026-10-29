package com.zaiten.mixer.core.multidevice

/**
 * Estime l'écart entre l'horloge de la source et celle de cet appareil (algorithme de Cristian) :
 * l'enceinte envoie t0, la source répond son heure t1, la réponse arrive à t2.
 * écart ≈ t1 − (t0 + t2) / 2, d'autant plus fiable que l'aller-retour (t2 − t0) est court ;
 * on garde donc l'échange le plus rapide parmi les derniers.
 */
class ClockSync(private val window: Int = 16) {
    private data class Sample(val offsetNs: Long, val rttNs: Long)

    private val samples = ArrayDeque<Sample>()

    fun add(t0: Long, t1: Long, t2: Long) {
        samples.addLast(Sample(t1 - (t0 + t2) / 2, t2 - t0))
        if (samples.size > window) samples.removeFirst()
    }

    /** Heure source − heure locale, en ns ; null avant le premier échange. */
    val offsetNs: Long? get() = samples.minByOrNull { it.rttNs }?.offsetNs

    /** Aller-retour du meilleur échange : borne de l'incertitude (± rtt / 2). */
    val bestRttNs: Long? get() = samples.minOfOrNull { it.rttNs }

    fun sourceToLocal(sourceNs: Long): Long? = offsetNs?.let { sourceNs - it }
}
