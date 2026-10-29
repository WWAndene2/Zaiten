package com.zaiten.mixer.features.spatial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.effects.Room
import com.zaiten.mixer.core.engine.HeadphoneMonitor
import com.zaiten.mixer.shared.Dimens
import com.zaiten.mixer.shared.LabeledSlider
import kotlin.math.roundToInt

/**
 * Spatialisation (§3) : intensité de « stéréo élargi » à « 3D complet » et taille de pièce.
 * Non encore proposés : profils HRTF mesurés et test d'écoute, suivi de la tête, 3D par appli,
 * désactivation auto sur contenu déjà spatialisé (voir cpp/engine/Spatializer.h et EffectsChain.h).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpatialScreen() {
    val settings by EffectsRepository.settings.collectAsState()
    val headphones by HeadphoneMonitor.connected.collectAsState()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gutter),
    ) {
        Text("3D", style = MaterialTheme.typography.headlineMedium)
        if (!headphones) {
            Text(
                "Branchez un casque : la 3D ne fonctionne qu'au casque et reste coupée sur le haut-parleur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LabeledSlider(
            label = "Intensité",
            value = (settings.spatial * 100).roundToInt(),
            range = 0..100,
            valueText = { spatialText(it) },
            onChange = { EffectsRepository.setSpatial(it / 100f) },
        )
        Text("Pièce virtuelle", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            Room.entries.forEach { room ->
                FilterChip(
                    selected = settings.room == room,
                    onClick = { EffectsRepository.setRoom(room) },
                    label = { Text(room.label) },
                )
            }
        }
        Text(
            "La 3D ajoute une légère latence : évitez-la pour les jeux compétitifs.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun spatialText(percent: Int) = when {
    percent == 0 -> "Arrêt"
    percent < 50 -> "Stéréo élargi $percent %"
    else -> "3D $percent %"
}
