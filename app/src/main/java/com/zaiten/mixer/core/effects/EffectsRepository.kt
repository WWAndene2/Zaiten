package com.zaiten.mixer.core.effects

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** État des effets du mix final et bibliothèque de presets (§2.4). */
object EffectsRepository {
    private val _settings = MutableStateFlow(EffectSettings())
    val settings: StateFlow<EffectSettings> = _settings

    /** Nom du preset appliqué ; null dès qu'un réglage s'en écarte. */
    private val _activePreset = MutableStateFlow<String?>(null)
    val activePreset: StateFlow<String?> = _activePreset

    private val _customPresets = MutableStateFlow<List<Preset>>(emptyList())
    val customPresets: StateFlow<List<Preset>> = _customPresets

    fun allPresets(): List<Preset> = BuiltInPresets.all + _customPresets.value

    fun restore(settings: EffectSettings, activePreset: String?, customPresets: List<Preset>) {
        _settings.value = settings
        _activePreset.value = activePreset
        _customPresets.value = customPresets
    }

    fun apply(preset: Preset) {
        _settings.value = preset.settings
        _activePreset.value = preset.name
    }

    /** Passe au preset suivant (notification, tuile) ; part du premier si aucun n'est actif. */
    fun applyNext() {
        val presets = allPresets()
        val index = presets.indexOfFirst { it.name == _activePreset.value }
        apply(presets[(index + 1) % presets.size])
    }

    fun setEqBand(band: Int, db: Float) = edit {
        it.copy(eqDb = it.eqDb.toMutableList().also { list ->
            list[band] = db.coerceIn(EffectSettings.EQ_MIN_DB, EffectSettings.EQ_MAX_DB)
        })
    }

    fun setCompressor(amount: Float) = edit { it.copy(compressor = amount.coerceIn(0f, 1f)) }
    fun setLimiter(enabled: Boolean) = edit { it.copy(limiter = enabled) }
    fun setMono(enabled: Boolean) = edit { it.copy(mono = enabled) }
    fun setSpatial(amount: Float) = edit { it.copy(spatial = amount.coerceIn(0f, 1f)) }
    fun setRoom(room: Room) = edit { it.copy(room = room) }

    /** Enregistre les réglages courants sous [name] ; remplace un preset personnel du même nom. */
    fun saveCurrentAs(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || BuiltInPresets.all.any { it.name == trimmed }) return false
        _customPresets.update { list -> list.filterNot { it.name == trimmed } + Preset(trimmed, _settings.value) }
        _activePreset.value = trimmed
        return true
    }

    fun rename(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || allPresets().any { it.name == trimmed }) return false
        _customPresets.update { list -> list.map { if (it.name == oldName) it.copy(name = trimmed) else it } }
        if (_activePreset.value == oldName) _activePreset.value = trimmed
        return true
    }

    fun delete(name: String) {
        _customPresets.update { list -> list.filterNot { it.name == name } }
        if (_activePreset.value == name) _activePreset.value = null
    }

    /** Ajoute des presets importés ; un nom déjà pris reçoit un suffixe plutôt que d'écraser. */
    fun import(presets: List<Preset>) = _customPresets.update { list ->
        val taken = (BuiltInPresets.all + list).map { it.name }.toMutableSet()
        list + presets.map { preset ->
            var name = preset.name
            var n = 2
            while (name in taken) name = "${preset.name} ($n)".also { n++ }
            taken += name
            preset.copy(name = name, builtIn = false)
        }
    }

    private fun edit(change: (EffectSettings) -> EffectSettings) {
        _settings.update(change)
        _activePreset.value = null
    }
}
