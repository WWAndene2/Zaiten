package com.zaiten.mixer.features.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.log10
import kotlinx.coroutines.delay
import com.zaiten.mixer.core.mixer.AppTrack
import com.zaiten.mixer.core.mixer.LevelMeter
import com.zaiten.mixer.core.mixer.VolumeRange

// Dimensions sur l'échelle du design system (CLAUDE.md §7).
// Largeur fixe 80 = 64 + 16 (§7.2) au-delà de quatre tranches (défilement) ; jusqu'à quatre, elles
// se partagent la largeur comme dans la maquette (TrackStripRow). Marge latérale 6 : sur un écran
// de 360 dp, une tranche fait ~79 dp et garde 64 dp utiles pour les boutons M/S (30 + 4 + 30).
// Rayon = 0,24 × hauteur (§7.3) : boutons de 30 → 7,2 → 8 ; tranche de ~384 → 92 → plafonné à 16
// (une tranche n'est pas une pilule, on garde le rayon des cartes de la maquette).
private val StripWidth = 80.dp
private val StripPadding = 8.dp
private val StripSidePadding = 6.dp
private val StripGap = 4.dp
private const val MaxSharedStrips = 4
private val StripRadius = 16.dp
private val IconSize = 32.dp
private val ToggleSize = 30.dp
private val ToggleGap = 4.dp
private val ToggleRadius = 8.dp
private val FaderLength = 192.dp
private val FaderThickness = 48.dp
private val MeterHeight = 4.dp

/** Tranche verticale de la table de mixage (§7.3, maquette « Table de mixage »). */
@Composable
fun TrackStrip(
    track: AppTrack,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    level: Float = 0f,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(StripRadius),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = StripSidePadding, vertical = StripPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StripPadding),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize)
                    .clip(RoundedCornerShape(ToggleRadius))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(track.label.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
            Text(
                track.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            LevelBar(level)
            VerticalFader(
                percent = track.volumePercent,
                onChange = onVolumeChange,
                length = FaderLength,
                thickness = FaderThickness,
            )
            Text("${track.volumePercent} %", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(ToggleGap)) {
                Toggle("M", selected = track.muted, onClick = onToggleMute)
                Toggle("S", selected = track.solo, onClick = onToggleSolo)
            }
            Text(
                "Retirer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(ToggleRadius)).clickable(onClick = onRemove).padding(StripPadding),
            )
        }
    }
}

/** Rangée de tranches : partage de la largeur jusqu'à [MaxSharedStrips], défilement au-delà. */
@Composable
fun TrackStripRow(
    tracks: List<AppTrack>,
    onVolumeChange: (uid: Int, percent: Int) -> Unit,
    onToggleMute: (uid: Int) -> Unit,
    onToggleSolo: (uid: Int) -> Unit,
    onRemove: (uid: Int) -> Unit,
) {
    // Vu-mètres rafraîchis à ~15 images/s : assez fluide, sans recomposer à chaque bloc audio.
    var levels by remember { mutableStateOf(emptyMap<Int, Float>()) }
    LaunchedEffect(tracks) {
        while (true) {
            levels = tracks.associate { it.uid to LevelMeter.level(it.uid) }
            delay(METER_REFRESH_MS)
        }
    }

    @Composable
    fun strip(track: AppTrack, modifier: Modifier) = TrackStrip(
        track = track,
        onVolumeChange = { onVolumeChange(track.uid, it) },
        onToggleMute = { onToggleMute(track.uid) },
        onToggleSolo = { onToggleSolo(track.uid) },
        onRemove = { onRemove(track.uid) },
        modifier = modifier,
        level = levels[track.uid] ?: 0f,
    )
    if (tracks.size <= MaxSharedStrips) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StripGap)) {
            tracks.forEach { strip(it, Modifier.weight(1f)) }
            // Places vides : une tranche garde la même largeur qu'il y en ait une ou quatre.
            repeat(MaxSharedStrips - tracks.size) { Spacer(Modifier.weight(1f)) }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(StripGap)) {
            items(tracks, key = { it.uid }) { strip(it, Modifier.width(StripWidth)) }
        }
    }
}

/** Vu-mètre horizontal (§7.3) : niveau crête de l'appli, en dB pour suivre l'oreille. */
@Composable
private fun LevelBar(level: Float) {
    val db = if (level > 0f) 20f * log10(level) else METER_FLOOR_DB
    val fraction = ((db - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clip(RoundedCornerShape(MeterHeight))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(MeterHeight)
                .background(if (db > METER_HOT_DB) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        )
    }
}

private const val METER_REFRESH_MS = 66L
private const val METER_FLOOR_DB = -60f
private const val METER_HOT_DB = -3f

@Composable
private fun Toggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(ToggleSize)
            .clip(RoundedCornerShape(ToggleRadius))
            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.onPrimary else colors.onSurface)
    }
}

/** Slider Material tourné de 90° : l'encombrement est échangé avant la rotation pour ne pas déborder. */
@Composable
private fun VerticalFader(percent: Int, onChange: (Int) -> Unit, length: Dp, thickness: Dp) {
    Slider(
        value = percent.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = VolumeRange.MIN_PERCENT.toFloat()..VolumeRange.MAX_PERCENT.toFloat(),
        modifier = Modifier
            .height(length)
            .width(thickness)
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(length.roundToPx(), thickness.roundToPx()))
                layout(thickness.roundToPx(), length.roundToPx()) {
                    placeable.place(
                        (thickness.roundToPx() - length.roundToPx()) / 2,
                        (length.roundToPx() - thickness.roundToPx()) / 2,
                    )
                }
            }
            .graphicsLayer { rotationZ = -90f },
    )
}
