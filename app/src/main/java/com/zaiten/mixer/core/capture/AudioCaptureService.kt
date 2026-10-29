package com.zaiten.mixer.core.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.zaiten.mixer.R
import com.zaiten.mixer.core.mixer.MixerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        val resultData = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (projection == null && resultData != null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(Activity.RESULT_OK, resultData)
            observeTracks()
            sessionWatcher = ActiveSessionWatcher(this).also { it.start() }
            val muter = OriginalMuter(getSystemService(AudioManager::class.java), ::lastSoundAt)
            originalMuter = muter
            scope.launch { muter.run() }
        }
        return START_NOT_STICKY
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
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_running))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        sessionWatcher?.stop()
        originalMuter?.restore()
        synchronized(this) { captures.values.forEach { it.stop() }; captures.clear() }
        projection?.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "mixer"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_DATA = "projection_data"

        fun start(context: Context, projectionData: Intent) {
            context.startForegroundService(
                Intent(context, AudioCaptureService::class.java).putExtra(EXTRA_DATA, projectionData),
            )
        }
    }
}
