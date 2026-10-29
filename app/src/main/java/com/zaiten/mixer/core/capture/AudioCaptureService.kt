package com.zaiten.mixer.core.capture

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.IntentCompat
import com.zaiten.mixer.core.control.MixerNotification
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.engine.HeadphoneMonitor
import com.zaiten.mixer.core.engine.NativeMixerEngine
import com.zaiten.mixer.core.mixer.MixerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Service de premier plan qui détient la MediaProjection (§5) et tient une capture par piste.
 * Les pistes ajoutées/retirées dans [MixerRepository] démarrent/arrêtent leur capture.
 */
class AudioCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null
    private val captures = mutableMapOf<Int, AppCaptureTrack>()
    private var sessionWatcher: ActiveSessionWatcher? = null
    private var originalMuter: OriginalMuter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (intent?.action == ACTION_REFRESH_SESSIONS) {
            sessionWatcher?.stop()
            sessionWatcher = ActiveSessionWatcher(this).also { it.start() }
            return START_NOT_STICKY
        }
        val resultData = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (projection == null && resultData != null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(Activity.RESULT_OK, resultData)
            NativeMixerEngine.start()
            observeEffects()
            observeTracks()
            observeNotification()
            sessionWatcher = ActiveSessionWatcher(this).also { it.start() }
            val muter = OriginalMuter(getSystemService(AudioManager::class.java), ::lastSoundAt)
            originalMuter = muter
            scope.launch { muter.run() }
        }
        return START_NOT_STICKY
    }

    private fun observeEffects() = scope.launch {
        combine(EffectsRepository.settings, HeadphoneMonitor.connected, ::Pair).collect { (settings, headphones) ->
            NativeMixerEngine.setEffects(settings, spatialAllowed = headphones)
            MixerRepository.setMasterCap(settings.masterCapPercent)
        }
    }

    private fun observeTracks() = scope.launch {
        MixerRepository.tracks.map { list -> list.map { it.uid }.toSet() }
            .distinctUntilChanged()
            .collect { uids -> syncCaptures(uids) }
    }

    @Synchronized
    private fun syncCaptures(uids: Set<Int>) {
        val proj = projection ?: return
        (captures.keys - uids).forEach { captures.remove(it)?.stop() }
        (uids - captures.keys).forEach { uid ->
            captures[uid] = AppCaptureTrack(proj, uid).also { it.start() }
        }
    }

    @Synchronized
    private fun lastSoundAt(): Long = captures.values.maxOfOrNull { it.lastSoundAtMs } ?: 0L

    private fun startInForeground() {
        val notification = MixerNotification.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(MixerNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(MixerNotification.ID, notification)
        }
    }

    /** Tient la notification à jour : volume maître, preset, applis. */
    private fun observeNotification() = scope.launch {
        combine(MixerRepository.masterPercent, MixerRepository.tracks, EffectsRepository.activePreset) { _, _, _ -> }
            .collect { MixerNotification.update(this@AudioCaptureService) }
    }

    override fun onDestroy() {
        sessionWatcher?.stop()
        originalMuter?.restore()
        synchronized(this) { captures.values.forEach { it.stop() }; captures.clear() }
        projection?.stop()
        NativeMixerEngine.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DATA = "projection_data"

        private const val ACTION_REFRESH_SESSIONS = "com.zaiten.mixer.REFRESH_SESSIONS"

        /** Relance la détection des applis, par exemple après l'octroi de l'accès aux notifications. */
        fun refreshSessions(context: Context) {
            context.startService(Intent(context, AudioCaptureService::class.java).setAction(ACTION_REFRESH_SESSIONS))
        }

        fun start(context: Context, projectionData: Intent) {
            context.startForegroundService(
                Intent(context, AudioCaptureService::class.java).putExtra(EXTRA_DATA, projectionData),
            )
        }
    }
}
