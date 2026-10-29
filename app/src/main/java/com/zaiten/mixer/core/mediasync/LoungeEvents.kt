package com.zaiten.mixer.core.mediasync

import org.json.JSONArray
import org.json.JSONObject

/** État de lecture tel que l'écran (YouTube sur la PS5, la TV…) le rapporte. */
enum class RemoteState {
    STOPPED, PLAYING, PAUSED, BUFFERING;

    companion object {
        // Codes du protocole Lounge (repris de pyytlounge, models.State) : 1 lecture, 2 pause,
        // 0 fin, 3 chargement, 5 en file, 1081 publicité. Une pub est traitée comme un chargement :
        // la vidéo principale n'avance pas.
        fun fromCode(code: String?): RemoteState = when (code?.toIntOrNull()) {
            1 -> PLAYING
            2 -> PAUSED
            3, 5, 1081, 1082 -> BUFFERING
            else -> STOPPED
        }
    }
}

/** Instantané reçu de l'écran : position [positionSec] valable à l'instant [receivedAtMs] (elapsedRealtime). */
data class RemotePlayback(
    val videoId: String?,
    val positionSec: Double,
    val state: RemoteState,
    val receivedAtMs: Long,
)

/** Événements utiles du flux Lounge ; les autres (volume, sous-titres…) sont ignorés. */
sealed interface LoungeEvent {
    data class SessionId(val sid: String) : LoungeEvent
    data class GSession(val gsessionId: String) : LoungeEvent
    data class NowPlaying(val videoId: String?, val positionSec: Double?, val state: RemoteState) : LoungeEvent
    data class StateChange(val positionSec: Double, val state: RemoteState) : LoungeEvent
    data class ScreenName(val name: String) : LoungeEvent
    data object ScreenDisconnected : LoungeEvent
}

/**
 * Découpe le flux du canal Lounge : une ligne avec la taille du bloc, puis le bloc JSON (sur une ou
 * plusieurs lignes), qui contient une liste [[id, [type, args…]], …]. Même logique que
 * pyytlounge (wrapper._parse_event_chunks, _process_events).
 */
class LoungeChunkParser {
    private var remaining = 0
    private val chunk = StringBuilder()

    /** Dernier identifiant d'événement reçu, à renvoyer au serveur (paramètre AID). */
    var lastEventId: Int? = null
        private set

    /** Donne une ligne du flux ; renvoie les événements d'un bloc complet, sinon une liste vide. */
    fun feed(line: String): List<LoungeEvent> {
        if (remaining <= 0) {
            remaining = line.trim().toIntOrNull() ?: return emptyList()
            chunk.clear()
            return emptyList()
        }
        chunk.append(line)
        remaining -= line.length + 1 // +1 : le saut de ligne retiré par la lecture ligne à ligne.
        if (remaining > 0) return emptyList()
        remaining = 0
        return parseChunk(chunk.toString())
    }

    private fun parseChunk(text: String): List<LoungeEvent> {
        val events = JSONArray(text)
        val out = mutableListOf<LoungeEvent>()
        for (i in 0 until events.length()) {
            val entry = events.getJSONArray(i)
            lastEventId = entry.getInt(0)
            val payload = entry.getJSONArray(1)
            parseEvent(payload.getString(0), payload)?.let(out::add)
        }
        return out
    }

    private fun parseEvent(type: String, payload: JSONArray): LoungeEvent? {
        val arg = payload.optJSONObject(1)
        return when (type) {
            "c" -> LoungeEvent.SessionId(payload.getString(1))
            "S" -> LoungeEvent.GSession(payload.getString(1))
            "nowPlaying" -> arg?.let {
                LoungeEvent.NowPlaying(
                    videoId = it.optString("videoId").ifEmpty { null },
                    positionSec = it.optString("currentTime").toDoubleOrNull(),
                    state = if (it.has("state")) RemoteState.fromCode(it.optString("state")) else RemoteState.STOPPED,
                )
            }
            "onStateChange" -> arg?.let {
                LoungeEvent.StateChange(it.optString("currentTime").toDoubleOrNull() ?: 0.0, RemoteState.fromCode(it.optString("state")))
            }
            "loungeStatus" -> arg?.let(::screenName)?.let(LoungeEvent::ScreenName)
            "loungeScreenDisconnected" -> LoungeEvent.ScreenDisconnected
            else -> null
        }
    }

    private fun screenName(status: JSONObject): String? {
        val devices = JSONArray(status.optString("devices", "[]"))
        for (i in 0 until devices.length()) {
            val d = devices.getJSONObject(i)
            if (d.optString("type") == "LOUNGE_SCREEN") return d.optString("name")
        }
        return null
    }
}
