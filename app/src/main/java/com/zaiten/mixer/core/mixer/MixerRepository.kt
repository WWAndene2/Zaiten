package com.zaiten.mixer.core.mixer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Source unique de l'état du mixeur, partagée entre l'interface et le service de capture. */
object MixerRepository {
    private val _tracks = MutableStateFlow<List<AppTrack>>(emptyList())
    val tracks: StateFlow<List<AppTrack>> = _tracks

    private val _masterPercent = MutableStateFlow(100)
    val masterPercent: StateFlow<Int> = _masterPercent

    /** -100 à +100, appliquée à toutes les pistes (§2.2). */
    private val _globalBalance = MutableStateFlow(0)
    val globalBalance: StateFlow<Int> = _globalBalance

    /** Règles par nom de paquet, conservées après retrait de la piste et après redémarrage (§7.1). */
    private val _rules = MutableStateFlow<Map<String, TrackRule>>(emptyMap())
    val rules: StateFlow<Map<String, TrackRule>> = _rules

    /** Plafond du volume maître imposé par le preset actif (Nuit, §7.4). */
    private val _masterCapPercent = MutableStateFlow(VolumeRange.MAX_PERCENT)

    private val _originalSoundMode = MutableStateFlow(OriginalSoundMode.PENDING)
    val originalSoundMode: StateFlow<OriginalSoundMode> = _originalSoundMode

    fun setOriginalSoundMode(mode: OriginalSoundMode) { _originalSoundMode.value = mode }

    /** Rechargement depuis le stockage, au démarrage de l'appli. */
    fun restore(masterPercent: Int, globalBalance: Int, rules: Map<String, TrackRule>) {
        _masterPercent.value = VolumeRange.clamp(masterPercent)
        _globalBalance.value = globalBalance.coerceIn(-100, 100)
        _rules.value = rules
    }

    fun addTrack(track: AppTrack) = _tracks.update { list ->
        if (list.any { it.uid == track.uid }) list
        else list + (_rules.value[track.packageName]?.let(track::withRule) ?: track)
    }

    fun removeTrack(uid: Int) = _tracks.update { list -> list.filterNot { it.uid == uid } }

    fun setVolume(uid: Int, percent: Int) = edit(uid) { it.copy(volumePercent = VolumeRange.clamp(percent)) }

    fun toggleMute(uid: Int) = edit(uid) { it.copy(muted = !it.muted) }

    fun toggleSolo(uid: Int) = edit(uid) { it.copy(solo = !it.solo) }

    fun setChannel(uid: Int, channel: Channel) = edit(uid) { it.copy(channel = channel) }

    fun setBalance(uid: Int, balance: Int) = edit(uid) { it.copy(balance = balance.coerceIn(-100, 100)) }

    fun setMaster(percent: Int) { _masterPercent.value = VolumeRange.clamp(percent) }

    fun setGlobalBalance(balance: Int) { _globalBalance.value = balance.coerceIn(-100, 100) }

    fun setMasterCap(percent: Int) { _masterCapPercent.value = VolumeRange.clamp(percent) }

    /** Gains à appliquer à la piste [uid] à cet instant (lu par le thread de capture). */
    fun currentGains(uid: Int): ChannelGains {
        val list = _tracks.value
        val track = list.firstOrNull { it.uid == uid } ?: return ChannelGains(0, 0)
        val master = minOf(_masterPercent.value, _masterCapPercent.value)
        return track.channelGains(list.any { it.solo }, master, _globalBalance.value)
    }

    private fun edit(uid: Int, change: (AppTrack) -> AppTrack) {
        _tracks.update { list -> list.map { if (it.uid == uid) change(it) else it } }
        _tracks.value.firstOrNull { it.uid == uid }?.let { track ->
            _rules.update { it + (track.packageName to track.rule()) }
        }
    }
}
