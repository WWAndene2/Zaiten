package com.zaiten.mixer.core.storage

import com.zaiten.mixer.core.effects.EffectSettings
import com.zaiten.mixer.core.effects.Preset
import com.zaiten.mixer.core.effects.Room
import com.zaiten.mixer.core.mixer.Channel
import com.zaiten.mixer.core.mixer.TrackRule
import org.json.JSONArray
import org.json.JSONObject

/** Conversion JSON des réglages : stockage local et fichiers d'export de presets (§2.4). */
object SettingsJson {
    private const val PRESET_FORMAT = "zaiten-presets"
    private const val PRESET_VERSION = 1

    fun settingsToJson(s: EffectSettings) = JSONObject()
        .put("eqDb", JSONArray(s.eqDb.map { it.toDouble() }))
        .put("compressor", s.compressor.toDouble())
        .put("limiter", s.limiter)
        .put("mono", s.mono)
        .put("masterCapPercent", s.masterCapPercent)
        .put("spatial", s.spatial.toDouble())
        .put("room", s.room.name)
        .put("preampDb", s.preampDb.toDouble())
        .put("crossfeed", s.crossfeed)

    fun settingsFromJson(o: JSONObject): EffectSettings {
        val defaults = EffectSettings()
        val eq = o.optJSONArray("eqDb")
        return EffectSettings(
            eqDb = List(defaults.eqDb.size) { i -> eq?.optDouble(i, 0.0)?.toFloat() ?: 0f },
            compressor = o.optDouble("compressor", 0.0).toFloat(),
            limiter = o.optBoolean("limiter", true),
            mono = o.optBoolean("mono", false),
            masterCapPercent = o.optInt("masterCapPercent", defaults.masterCapPercent),
            spatial = o.optDouble("spatial", 0.0).toFloat(),
            room = runCatching { Room.valueOf(o.optString("room")) }.getOrDefault(defaults.room),
            preampDb = o.optDouble("preampDb", 0.0).toFloat(),
            crossfeed = o.optBoolean("crossfeed", false),
        )
    }

    fun presetsToJson(presets: List<Preset>): String = JSONObject()
        .put("format", PRESET_FORMAT)
        .put("version", PRESET_VERSION)
        .put("presets", JSONArray(presets.map { JSONObject().put("name", it.name).put("settings", settingsToJson(it.settings)) }))
        .toString(2)

    /** Lit un fichier d'export ; renvoie null si ce n'est pas un fichier de presets valide. */
    fun presetsFromJson(text: String): List<Preset>? = try {
        val root = JSONObject(text)
        if (root.optString("format") != PRESET_FORMAT) null
        else {
            val array = root.getJSONArray("presets")
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Preset(o.getString("name"), settingsFromJson(o.getJSONObject("settings")))
            }
        }
    } catch (e: org.json.JSONException) {
        null
    }

    fun rulesToJson(rules: Map<String, TrackRule>) = JSONObject().apply {
        rules.forEach { (pkg, r) ->
            put(pkg, JSONObject()
                .put("volumePercent", r.volumePercent)
                .put("muted", r.muted)
                .put("channel", r.channel.name)
                .put("balance", r.balance)
                .put("priority", r.priority))
        }
    }

    fun rulesFromJson(o: JSONObject): Map<String, TrackRule> = o.keys().asSequence().associateWith { pkg ->
        val r = o.getJSONObject(pkg)
        TrackRule(
            volumePercent = r.optInt("volumePercent", 100),
            muted = r.optBoolean("muted", false),
            channel = runCatching { Channel.valueOf(r.optString("channel")) }.getOrDefault(Channel.BOTH),
            balance = r.optInt("balance", 0),
            priority = r.optBoolean("priority", false),
        )
    }
}
