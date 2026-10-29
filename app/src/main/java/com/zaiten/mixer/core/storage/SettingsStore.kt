package com.zaiten.mixer.core.storage

import android.content.Context
import com.zaiten.mixer.core.effects.EffectSettings
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.effects.Preset
import com.zaiten.mixer.core.mixer.MixerRepository
import com.zaiten.mixer.core.mixer.TrackRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Conserve les réglages entre les sessions (§7.1 règles par appli, §2.4 presets, §7.7 volumes
 * conservés après redémarrage) : rechargés au lancement, réécrits après chaque modification.
 */
object SettingsStore {
    private const val FILE = "settings"
    private const val KEY = "state"
    /** Un curseur déplacé produit des dizaines de valeurs : on n'écrit qu'une fois le geste fini. */
    private const val SAVE_DEBOUNCE_MS = 500L

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { load(it) }

        val mixer = combine(MixerRepository.masterPercent, MixerRepository.globalBalance, MixerRepository.rules) { m, b, r ->
            Triple(m, b, r)
        }
        val effects = combine(EffectsRepository.settings, EffectsRepository.activePreset, EffectsRepository.customPresets) { s, a, c ->
            Triple(s, a, c)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(mixer, effects) { m, e -> serialize(m, e) }
                .drop(1) // État tout juste rechargé : rien à réécrire.
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { prefs.edit().putString(KEY, it).apply() }
        }
    }

    private fun load(text: String) = runCatching {
        val o = JSONObject(text)
        MixerRepository.restore(
            masterPercent = o.optInt("master", 100),
            globalBalance = o.optInt("globalBalance", 0),
            rules = o.optJSONObject("rules")?.let(SettingsJson::rulesFromJson).orEmpty(),
        )
        EffectsRepository.restore(
            settings = o.optJSONObject("effects")?.let(SettingsJson::settingsFromJson) ?: EffectSettings(),
            activePreset = o.optString("activePreset").ifEmpty { null },
            customPresets = SettingsJson.presetsFromJson(o.optString("customPresets")).orEmpty(),
        )
    }

    private fun serialize(
        mixer: Triple<Int, Int, Map<String, TrackRule>>,
        effects: Triple<EffectSettings, String?, List<Preset>>,
    ): String = JSONObject()
        .put("master", mixer.first)
        .put("globalBalance", mixer.second)
        .put("rules", SettingsJson.rulesToJson(mixer.third))
        .put("effects", SettingsJson.settingsToJson(effects.first))
        .put("activePreset", effects.second ?: "")
        .put("customPresets", SettingsJson.presetsToJson(effects.third))
        .toString()
}
