package com.zaiten.mixer.core.mediasync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import org.json.JSONObject

/**
 * Client du protocole YouTube « Lounge » : celui qu'utilise l'appli YouTube du téléphone pour piloter
 * YouTube sur une TV ou une console (couplage par « code TV »).
 *
 * Protocole non documenté par Google : requêtes reprises de pyytlounge 3.5.0 (wrapper.py : pair,
 * refresh_auth, connect, subscribe, _command). Il peut changer sans préavis ; tout échec remonte en
 * [IOException] pour que l'appelant se reconnecte ou redemande un couplage.
 *
 * Appels bloquants : à utiliser hors du thread principal.
 */
class LoungeClient(private val deviceName: String, private val deviceId: String = UUID.randomUUID().toString()) {
    data class Screen(val screenId: String, val name: String?, val loungeToken: String)

    private var screen: Screen? = null
    private var sid: String? = null
    private var gsession: String? = null
    private var commandOffset = 1
    private val parser = LoungeChunkParser()

    /** Couplage avec le code affiché par YouTube sur l'écran (Réglages → Associer un téléphone). */
    fun pair(code: String): Screen {
        val json = post("$BASE/pairing/get_screen", mapOf("pairing_code" to code.filter(Char::isDigit)))
        val s = JSONObject(json).getJSONObject("screen")
        return Screen(s.getString("screenId"), s.optString("name"), s.getString("loungeToken")).also { screen = it }
    }

    /** Reprend un couplage enregistré : le jeton Lounge expire (environ deux semaines), l'écran non. */
    fun resume(screenId: String, name: String?): Screen {
        val json = post("$BASE/pairing/get_lounge_token_batch", mapOf("screen_ids" to screenId))
        val s = JSONObject(json).getJSONArray("screens").getJSONObject(0)
        return Screen(s.getString("screenId"), name, s.getString("loungeToken")).also { screen = it }
    }

    /** Ouvre la session (bind) ; les identifiants SID et gsessionid arrivent dans la réponse. */
    fun connect(onEvent: (LoungeEvent) -> Unit) {
        val token = requireNotNull(screen) { "Couplage requis" }.loungeToken
        val body = mapOf(
            "app" to "web",
            "mdx-version" to "3",
            "name" to deviceName,
            "id" to deviceId,
            "device" to "REMOTE_CONTROL",
            "capabilities" to "que,dsdtr,atp,vsp",
            "magnaKey" to "cloudPairedDevice",
            "ui" to "false",
            "deviceContext" to "user_agent=dunno&window_width_points=&window_height_points=&os_name=android&ms=",
            "theme" to "cl",
            "loungeIdToken" to token,
        )
        val text = post("$BASE/bc/bind?RID=1&VER=8&CVER=1&auth_failure_option=send_error", body)
        text.lineSequence().forEach { line -> parser.feed(line).forEach { handle(it, onEvent) } }
        commandOffset = 1
        if (sid == null || gsession == null) throw IOException("Session Lounge refusée")
    }

    /** Écoute longue : bloque tant que le serveur garde la connexion ouverte. */
    fun listen(onEvent: (LoungeEvent) -> Unit) {
        val params = session() + mapOf("RID" to "rpc", "CI" to "0", "TYPE" to "xmlhttp")
        val connection = open("$BASE/bc/bind?${encode(params)}", "GET")
        connection.readTimeout = 0 // Connexion volontairement longue (« long polling »).
        checkStatus(connection)
        connection.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                parser.feed(line).forEach { handle(it, onEvent) }
                if (sid == null) break
            }
        }
    }

    /** Demande à l'écran de renvoyer la vidéo et la position courantes (événement nowPlaying). */
    fun requestNowPlaying() {
        commandOffset++
        val params = session() + mapOf("RID" to commandOffset.toString())
        post("$BASE/bc/bind?${encode(params)}", mapOf("count" to "1", "ofs" to commandOffset.toString(), "req0__sc" to "getNowPlaying"))
    }

    private fun handle(event: LoungeEvent, onEvent: (LoungeEvent) -> Unit) {
        when (event) {
            is LoungeEvent.SessionId -> sid = event.sid
            is LoungeEvent.GSession -> gsession = event.gsessionId
            LoungeEvent.ScreenDisconnected -> { sid = null; gsession = null }
            else -> {}
        }
        onEvent(event)
    }

    private fun session(): Map<String, String> = mapOf(
        "name" to deviceName,
        "loungeIdToken" to (screen?.loungeToken ?: ""),
        "SID" to (sid ?: throw IOException("Pas de session")),
        "AID" to (parser.lastEventId?.toString() ?: ""),
        "gsessionid" to (gsession ?: ""),
        "device" to "REMOTE_CONTROL",
        "app" to "youtube-desktop",
        "VER" to "8",
        "v" to "2",
    )

    private fun post(url: String, form: Map<String, String>): String {
        val connection = open(url, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(encode(form).toByteArray()) }
        checkStatus(connection)
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun open(url: String, method: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }

    private fun checkStatus(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code == 400 || code == 410) { sid = null; gsession = null } // Session perdue : reconnexion.
        if (code != HttpURLConnection.HTTP_OK) throw IOException("Lounge HTTP $code")
    }

    private fun encode(params: Map<String, String>) =
        params.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }

    private companion object {
        const val BASE = "https://www.youtube.com/api/lounge"
        const val TIMEOUT_MS = 15_000
    }
}
