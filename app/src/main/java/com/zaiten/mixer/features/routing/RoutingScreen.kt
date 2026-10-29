package com.zaiten.mixer.features.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.mixer.AppTrack
import com.zaiten.mixer.core.mixer.Channel
import com.zaiten.mixer.core.mixer.MixerRepository
import com.zaiten.mixer.shared.Dimens
import com.zaiten.mixer.shared.LabeledSlider
import com.zaiten.mixer.shared.balanceText

/**
 * Routage gauche/droite (§2.2, §7.1) : canal et balance par appli, balance et mono globaux.
 * Le choix de la sortie (casque, haut-parleur, TV, enceinte Wi-Fi) viendra avec les étapes 9 et 10.
 */
@Composable
fun RoutingScreen() {
    val tracks by MixerRepository.tracks.collectAsState()
    val globalBalance by MixerRepository.globalBalance.collectAsState()
    val effects by EffectsRepository.settings.collectAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gutter),
    ) {
        Text("Routage", style = MaterialTheme.typography.headlineMedium)
        LabeledSlider("Balance globale", globalBalance, -100..100, ::balanceText, MixerRepository::setGlobalBalance)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mono", modifier = Modifier.weight(1f))
            Switch(checked = effects.mono, onCheckedChange = EffectsRepository::setMono)
        }
        if (tracks.isEmpty()) {
            Text("Aucune appli active.", style = MaterialTheme.typography.bodyMedium)
        }
        tracks.forEach { TrackRouting(it) }
    }
}

@Composable
private fun TrackRouting(track: AppTrack) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.Gutter), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            Text(track.label, style = MaterialTheme.typography.titleMedium)
            val options = listOf(Channel.LEFT to "G", Channel.BOTH to "G+D", Channel.RIGHT to "D")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (channel, label) ->
                    SegmentedButton(
                        selected = track.channel == channel,
                        onClick = { MixerRepository.setChannel(track.uid, channel) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
            LabeledSlider("Balance", track.balance, -100..100, ::balanceText) { MixerRepository.setBalance(track.uid, it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prioritaire")
                    Text("Les autres applis baissent quand elle joue", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = track.priority, onCheckedChange = { MixerRepository.togglePriority(track.uid) })
            }
        }
    }
}
