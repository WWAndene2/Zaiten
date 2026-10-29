package com.zaiten.mixer.core.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import com.zaiten.mixer.R
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.mixer.MixerRepository

/**
 * Notification persistante du service (§2.6, §7.3) : volume maître, preset actif, les trois applis
 * les plus fortes, et les commandes rapides.
 */
object MixerNotification {
    const val ID = 1
    private const val CHANNEL_ID = "mixer"
    private const val TOP_APPS = 3

    fun build(context: Context): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val master = MixerRepository.masterPercent.value
        val preset = EffectsRepository.activePreset.value ?: "Réglage libre"
        val apps = MixerRepository.tracks.value
            .sortedByDescending { if (it.muted) -1 else it.volumePercent }
            .take(TOP_APPS)
            .joinToString(" · ") { "${it.label} ${if (it.muted) "muet" else "${it.volumePercent} %"}" }
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Maître $master % · $preset")
            .setContentText(apps.ifEmpty { context.getString(R.string.notification_running) })
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // En vue compacte, seules les icônes s'affichent : chacune doit être distincte.
            .addAction(action(context, MixerControl.MASTER_DOWN, android.R.drawable.ic_media_rew, "−${MixerControl.MASTER_STEP} %"))
            .addAction(action(context, MixerControl.MASTER_UP, android.R.drawable.ic_media_ff, "+${MixerControl.MASTER_STEP} %"))
            .addAction(action(context, MixerControl.NEXT_PRESET, android.R.drawable.ic_menu_rotate, "Preset"))
            .addAction(action(context, MixerControl.STOP, android.R.drawable.ic_menu_close_clear_cancel, "Arrêter"))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    fun update(context: Context) {
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context))
    }

    private fun action(context: Context, control: MixerControl, icon: Int, label: String) =
        Notification.Action.Builder(Icon.createWithResource(context, icon), label, control.pendingIntent(context)).build()
}
