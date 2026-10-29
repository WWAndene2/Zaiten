package com.zaiten.mixer.features.controls

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.zaiten.mixer.R
import com.zaiten.mixer.core.control.MixerControl
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.mixer.MixerRepository

/** Widget d'écran d'accueil (§2.6) : volume maître, preset actif, boutons −/+ et preset suivant. */
class MixerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MixerWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context) = RemoteViews(context.packageName, R.layout.widget_mixer).apply {
            setTextViewText(R.id.widget_master, "${MixerRepository.masterPercent.value} %")
            setTextViewText(R.id.widget_preset, EffectsRepository.activePreset.value ?: "Réglage libre")
            setOnClickPendingIntent(R.id.widget_down, MixerControl.MASTER_DOWN.pendingIntent(context))
            setOnClickPendingIntent(R.id.widget_up, MixerControl.MASTER_UP.pendingIntent(context))
            setOnClickPendingIntent(R.id.widget_preset, MixerControl.NEXT_PRESET.pendingIntent(context))
        }
    }
}
