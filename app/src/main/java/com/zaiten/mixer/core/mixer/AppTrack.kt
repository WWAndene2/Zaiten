package com.zaiten.mixer.core.mixer

/** État d'une piste du mixeur : une appli identifiée par son UID Android. */
data class AppTrack(
    val uid: Int,
    val packageName: String,
    val label: String,
    val volumePercent: Int = 100,
    val muted: Boolean = false,
    val solo: Boolean = false,
)

/** Volume réellement appliqué en tenant compte de muet, solo (§2.1) et du volume maître. */
fun AppTrack.effectivePercent(anySolo: Boolean, masterPercent: Int): Int {
    if (muted || (anySolo && !solo)) return 0
    return VolumeRange.clamp(volumePercent * masterPercent / 100)
}
