package com.zaiten.mixer.core.control

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaiten.mixer.core.capture.AudioCaptureService
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.mixer.MixerRepository

/** Commandes rapides partagées par la notification, le widget et la tuile (§2.6). */
enum class MixerControl {
    MASTER_DOWN, MASTER_UP, NEXT_PRESET, STOP;

    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ordinal,
        Intent(context, MixerControlReceiver::class.java).setAction(ACTION_PREFIX + name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun perform(context: Context) {
        when (this) {
            MASTER_DOWN -> MixerRepository.setMaster(MixerRepository.masterPercent.value - MASTER_STEP)
            MASTER_UP -> MixerRepository.setMaster(MixerRepository.masterPercent.value + MASTER_STEP)
            NEXT_PRESET -> EffectsRepository.applyNext()
            STOP -> context.stopService(Intent(context, AudioCaptureService::class.java))
        }
    }

    companion object {
        /** Pas des boutons rapides ; le réglage au pour cent près reste dans l'appli (§2.5). */
        const val MASTER_STEP = 10
        private const val ACTION_PREFIX = "com.zaiten.mixer.control."

        fun fromAction(action: String?): MixerControl? =
            action?.removePrefix(ACTION_PREFIX)?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

class MixerControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MixerControl.fromAction(intent.action)?.perform(context)
    }
}
