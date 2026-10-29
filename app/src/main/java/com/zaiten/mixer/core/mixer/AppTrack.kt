package com.zaiten.mixer.core.mixer

/** Oreillette(s) sur lesquelles une appli est jouée (§2.2). */
enum class Channel { LEFT, BOTH, RIGHT }

/** Réglages d'une appli enregistrés et réappliqués à chaque détection (§7.1). */
data class TrackRule(
    val volumePercent: Int = 100,
    val muted: Boolean = false,
    val channel: Channel = Channel.BOTH,
    /** -100 (tout à gauche) à +100 (tout à droite). */
    val balance: Int = 0,
)

/** État d'une piste du mixeur : une appli identifiée par son UID Android. */
data class AppTrack(
    val uid: Int,
    val packageName: String,
    val label: String,
    val volumePercent: Int = 100,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val channel: Channel = Channel.BOTH,
    val balance: Int = 0,
) {
    // Solo n'est pas enregistré : c'est une écoute ponctuelle, pas une règle de l'appli.
    fun rule() = TrackRule(volumePercent, muted, channel, balance)

    fun withRule(rule: TrackRule) =
        copy(volumePercent = rule.volumePercent, muted = rule.muted, channel = rule.channel, balance = rule.balance)
}

/** Volume réellement appliqué en tenant compte de muet, solo (§2.1) et du volume maître. */
fun AppTrack.effectivePercent(anySolo: Boolean, masterPercent: Int): Int {
    if (muted || (anySolo && !solo)) return 0
    return VolumeRange.clamp(volumePercent * masterPercent / 100)
}

/** Gains gauche/droite de la piste : volume effectif, canal (§2.2), puis balance de la piste et globale. */
data class ChannelGains(val left: Int, val right: Int)

fun AppTrack.channelGains(anySolo: Boolean, masterPercent: Int, globalBalance: Int): ChannelGains {
    val base = effectivePercent(anySolo, masterPercent)
    var left = if (channel == Channel.RIGHT) 0 else base
    var right = if (channel == Channel.LEFT) 0 else base
    // Balance « atténuante » : le côté opposé baisse, le côté choisi garde son niveau.
    for (b in listOf(balance, globalBalance)) {
        val bal = b.coerceIn(-100, 100)
        if (bal > 0) left = left * (100 - bal) / 100
        if (bal < 0) right = right * (100 + bal) / 100
    }
    return ChannelGains(left, right)
}
