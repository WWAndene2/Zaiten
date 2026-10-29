package com.zaiten.mixer.core.capture

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.zaiten.mixer.core.mixer.AppTrack
import com.zaiten.mixer.core.mixer.MixerRepository

/**
 * Ajoute automatiquement au mixeur les applis qui se mettent à jouer (§2.1).
 *
 * L'UID des lecteurs audio n'est pas public (AudioPlaybackConfiguration le masque) ; on passe par
 * les sessions média, qui donnent le nom de paquet, puis on en déduit l'UID. Ne voit que les applis
 * qui publient une session média (lecteurs musique/vidéo, la plupart des navigateurs) : les jeux
 * en sont souvent dépourvus et restent à ajouter à la main depuis la liste des applis installées.
 */
class ActiveSessionWatcher(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaSessionAccessService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        sync(controllers.orEmpty())
    }

    fun start() {
        if (!hasAccess(context)) return
        manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
        sync(manager.getActiveSessions(component))
    }

    fun stop() {
        if (!hasAccess(context)) return
        manager.removeOnActiveSessionsChangedListener(sessionsListener)
        watched.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        watched.clear()
    }

    private fun sync(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        (watched.keys - tokens).forEach { token ->
            watched.remove(token)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
        }
        controllers.filter { it.sessionToken !in watched }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = onState(controller.packageName, state)
            }
            controller.registerCallback(callback, handler)
            watched[controller.sessionToken] = controller to callback
            onState(controller.packageName, controller.playbackState)
        }
    }

    private fun onState(packageName: String, state: PlaybackState?) {
        if (state?.state != PlaybackState.STATE_PLAYING || packageName == context.packageName) return
        trackFor(packageName)?.let(MixerRepository::addTrack)
    }

    private fun trackFor(packageName: String): AppTrack? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        AppTrack(uid = info.uid, packageName = packageName, label = pm.getApplicationLabel(info).toString())
    } catch (e: PackageManager.NameNotFoundException) {
        null // Paquet invisible pour nous (filtrage de visibilité Android 11+).
    }

    companion object {
        private var instance: ActiveSessionWatcher? = null

        /**
         * Démarre (ou relance) la détection, indépendamment de la capture : les applis apparaissent
         * dans le mixeur dès l'ouverture. Appelé au lancement et à chaque retour dans l'appli, car
         * l'accès aux notifications s'accorde dans les réglages d'Android.
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (instance != null || !hasAccess(app)) return
            instance = ActiveSessionWatcher(app).also { it.start() }
        }

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
