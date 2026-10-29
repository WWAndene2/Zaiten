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

    private val _originalSoundMode = MutableStateFlow(OriginalSoundMode.PENDING)
    val originalSoundMode: StateFlow<OriginalSoundMode> = _originalSoundMode

    fun setOriginalSoundMode(mode: OriginalSoundMode) { _originalSoundMode.value = mode }

    fun addTrack(track: AppTrack) = _tracks.update { list ->
        if (list.any { it.uid == track.uid }) list else list + track
    }

    fun removeTrack(uid: Int) = _tracks.update { list -> list.filterNot { it.uid == uid } }

    fun setVolume(uid: Int, percent: Int) =
        edit(uid) { it.copy(volumePercent = VolumeRange.clamp(percent)) }

    fun toggleMute(uid: Int) = edit(uid) { it.copy(muted = !it.muted) }

    fun toggleSolo(uid: Int) = edit(uid) { it.copy(solo = !it.solo) }

    fun setMaster(percent: Int) { _masterPercent.value = VolumeRange.clamp(percent) }

    /** Volume à appliquer à la piste [uid] à cet instant (lu par le thread audio). */
    fun currentPercent(uid: Int): Int {
        val list = _tracks.value
        val track = list.firstOrNull { it.uid == uid } ?: return 0
        return track.effectivePercent(list.any { it.solo }, _masterPercent.value)
    }

    private fun edit(uid: Int, change: (AppTrack) -> AppTrack) = _tracks.update { list ->
        list.map { if (it.uid == uid) change(it) else it }
    }
}
