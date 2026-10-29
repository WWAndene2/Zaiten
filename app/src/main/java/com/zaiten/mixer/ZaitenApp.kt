package com.zaiten.mixer

import android.app.Application
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.engine.HeadphoneMonitor
import com.zaiten.mixer.core.mixer.MixerRepository
import com.zaiten.mixer.core.storage.SettingsStore
import com.zaiten.mixer.features.controls.MixerWidgetProvider
import com.zaiten.mixer.features.controls.PresetTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ZaitenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        HeadphoneMonitor.start(this)
        // Widget et tuile vivent hors de l'appli : on les redessine à chaque changement d'état.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            combine(MixerRepository.masterPercent, EffectsRepository.activePreset) { _, _ -> }.collect {
                MixerWidgetProvider.refresh(this@ZaitenApp)
                PresetTileService.refresh(this@ZaitenApp)
            }
        }
    }
}
