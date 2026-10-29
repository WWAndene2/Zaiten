package com.zaiten.mixer.core.mediasync

import android.content.Context
import android.os.SystemClock
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException

/**
 * Synchronisation média (étape 9, §2.3) : Zaiten se couple à YouTube sur l'écran (PS5, TV…) comme
 * une seconde télécommande, suit la vidéo et la position, et l'écran de synchro rejoue la même
 * vidéo sur le téléphone, son dans le casque. Le cast d'origine n'est pas touché.
 */
object MediaSyncRepository {
    enum class Status { UNPAIRED, CONNECTING, CONNECTED, ERROR }

    private val _status = MutableStateFlow(Status.UNPAIRED)
    val status: StateFlow<Status> = _status

    private val _screenName = MutableStateFlow<String?>(null)
    val screenName: StateFlow<String?> = _screenName

    private val _playback = MutableStateFlow<RemotePlayback?>(null)
    val playback: StateFlow<RemotePlayback?> = _playback

    private val _offsetMs = MutableStateFlow(0)
    val offsetMs: StateFlow<Int> = _offsetMs

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _offsetMs.value = prefs.getInt(KEY_OFFSET, 0)
        val screenId = prefs.getString(KEY_SCREEN_ID, null) ?: return
        _screenName.value = prefs.getString(KEY_SCREEN_NAME, null)
        start { client -> client.resume(screenId, _screenName.value) }
    }

    fun pair(code: String) = start { client ->
        client.pair(code).also { screen ->
            prefs.edit().putString(KEY_SCREEN_ID, screen.screenId).putString(KEY_SCREEN_NAME, screen.name).apply()
        }
    }

    fun unpair() {
        job?.cancel()
        prefs.edit().remove(KEY_SCREEN_ID).remove(KEY_SCREEN_NAME).apply()
        _status.value = Status.UNPAIRED
        _screenName.value = null
        _playback.value = null
    }

    /** Réglage au pas de 10 ms (§2.3), de −2 s à +2 s. */
    fun setOffset(ms: Int) {
        val v = (ms / OFFSET_STEP_MS * OFFSET_STEP_MS).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        _offsetMs.value = v
        prefs.edit().putInt(KEY_OFFSET, v).apply()
    }

    private fun start(authenticate: (LoungeClient) -> LoungeClient.Screen) {
        job?.cancel()
        _error.value = null
        _status.value = Status.CONNECTING
        job = scope.launch {
            val client = LoungeClient(DEVICE_NAME)
            try {
                val screen = authenticate(client)
                screen.name?.let { _screenName.value = it }
            } catch (e: Exception) {
                fail(if (e is IOException) "Code invalide ou écran introuvable" else "Réponse inattendue de YouTube")
                return@launch
            }
            // Boucle de session : l'écoute longue se termine régulièrement, on rouvre aussitôt ;
            // si la session est perdue, on la recrée ; on redemande la position pour corriger la dérive.
            var poller: Job? = null
            while (isActive) {
                try {
                    client.connect(::onEvent)
                    _status.value = Status.CONNECTED
                    poller?.cancel()
                    poller = launch {
                        while (isActive) {
                            runCatching { client.requestNowPlaying() }
                            delay(NOW_PLAYING_INTERVAL_MS)
                        }
                    }
                    while (isActive) client.listen(::onEvent)
                } catch (e: IOException) {
                    _status.value = Status.CONNECTING
                    delay(RECONNECT_DELAY_MS)
                } catch (e: JSONException) {
                    fail("Réponse inattendue de YouTube : le protocole a peut-être changé")
                    return@launch
                }
            }
        }
    }

    private fun onEvent(event: LoungeEvent) {
        val now = SystemClock.elapsedRealtime()
        when (event) {
            is LoungeEvent.NowPlaying ->
                _playback.value = RemotePlayback(event.videoId, event.positionSec ?: 0.0, event.state, now)
            is LoungeEvent.StateChange ->
                _playback.value = _playback.value?.copy(positionSec = event.positionSec, state = event.state, receivedAtMs = now)
            is LoungeEvent.ScreenName -> _screenName.value = event.name
            else -> {}
        }
    }

    private fun fail(message: String) {
        _error.value = message
        _status.value = Status.ERROR
    }

    private const val PREFS = "mediasync"
    private const val KEY_SCREEN_ID = "screenId"
    private const val KEY_SCREEN_NAME = "screenName"
    private const val KEY_OFFSET = "offsetMs"
    /** Nom affiché sur l'écran dans la liste des appareils connectés. */
    private const val DEVICE_NAME = "Zaiten"
    const val OFFSET_STEP_MS = 10
    const val MAX_OFFSET_MS = 2_000
    private const val NOW_PLAYING_INTERVAL_MS = 10_000L
    private const val RECONNECT_DELAY_MS = 3_000L
}
