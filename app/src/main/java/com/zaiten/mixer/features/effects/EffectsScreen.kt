package com.zaiten.mixer.features.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.zaiten.mixer.core.effects.EffectSettings
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.shared.Dimens
import com.zaiten.mixer.shared.LabeledSlider
import kotlin.math.roundToInt

private val BandFaderLength = 192.dp
private val BandFaderThickness = 30.dp

/** Égaliseur 10 bandes global, compresseur et limiteur du mix final (§2.4, §7.2). */
@Composable
fun EffectsScreen() {
    val settings by EffectsRepository.settings.collectAsState()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gutter),
    ) {
        Text("Effets", style = MaterialTheme.typography.headlineMedium)
        LabeledSlider(
            label = "Préampli",
            value = settings.preampDb.roundToInt(),
            range = -12..12,
            valueText = { "${formatDb(it.toFloat())} dB" },
            onChange = { EffectsRepository.setPreamp(it.toFloat()) },
        )
        Text("Égaliseur", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EffectSettings.EQ_FREQUENCIES.forEachIndexed { band, frequency ->
                BandFader(frequency, settings.eqDb[band]) { EffectsRepository.setEqBand(band, it) }
            }
        }
        LabeledSlider(
            label = "Compresseur",
            value = (settings.compressor * 100).roundToInt(),
            range = 0..100,
            valueText = { if (it == 0) "Arrêt" else "$it %" },
            onChange = { EffectsRepository.setCompressor(it / 100f) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Limiteur")
                Text("Évite la saturation au-delà de 100 %", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.limiter, onCheckedChange = EffectsRepository::setLimiter)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Crossfeed")
                Text("Moins de fatigue au casque sur une stéréo très séparée", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.crossfeed, onCheckedChange = EffectsRepository::setCrossfeed)
        }
    }
}

@Composable
private fun BandFader(frequency: Int, db: Float, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatDb(db), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = db,
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = EffectSettings.EQ_MIN_DB..EffectSettings.EQ_MAX_DB,
            modifier = Modifier
                .height(BandFaderLength)
                .width(BandFaderThickness)
                .layout { measurable, _ ->
                    val length = BandFaderLength.roundToPx()
                    val thickness = BandFaderThickness.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(length, thickness))
                    layout(thickness, length) { placeable.place((thickness - length) / 2, (length - thickness) / 2) }
                }
                .graphicsLayer { rotationZ = -90f },
        )
        Text(formatFrequency(frequency), style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatDb(db: Float) = db.roundToInt().let { if (it > 0) "+$it" else "$it" }

private fun formatFrequency(hz: Int) = if (hz >= 1000) "${hz / 1000}k" else "$hz"
