package com.zaiten.mixer.features.controls

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zaiten.mixer.core.control.MixerControl
import com.zaiten.mixer.core.effects.EffectsRepository

/** Tuile des réglages rapides (§2.6) : affiche le preset actif, un appui passe au suivant. */
class PresetTileService : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        MixerControl.NEXT_PRESET.perform(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val preset = EffectsRepository.activePreset.value
        tile.label = preset ?: "Preset"
        tile.state = if (preset != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        /** Demande au système de redessiner la tuile après un changement de preset ailleurs. */
        fun refresh(context: Context) =
            requestListeningState(context, ComponentName(context, PresetTileService::class.java))
    }
}
