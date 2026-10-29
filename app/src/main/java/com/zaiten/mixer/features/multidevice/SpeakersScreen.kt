package com.zaiten.mixer.features.multidevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zaiten.mixer.core.multidevice.MultiDeviceRepository
import com.zaiten.mixer.core.multidevice.MultiDeviceRepository.Mode
import com.zaiten.mixer.core.multidevice.SpeakerRole
import com.zaiten.mixer.shared.Dimens
import com.zaiten.mixer.shared.LabeledSlider
import kotlinx.coroutines.delay

/**
 * Système multi-appareils (§2.7) : ce téléphone diffuse son mix (source) ou joue un canal (enceinte).
 * Configurations obtenues par les rôles : stéréo (G + D), barre de son (+ centre), 2.1 (+ caisson),
 * 5.1 (+ arrières). Non encore fait : calibrage automatique au micro, placement dans la pièce,
 * enceintes Wi-Fi et Cast tierces (seuls des appareils avec Zaiten peuvent servir d'enceinte).
 */
@Composable
fun SpeakersScreen() {
    val mode by MultiDeviceRepository.mode.collectAsState()
    val context = LocalContext.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gutter),
    ) {
        Text("Enceintes", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plusieurs téléphones sur le même Wi-Fi forment un seul système : l'un diffuse, les autres jouent chacun un canal.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            FilterChip(selected = mode == Mode.SOURCE, onClick = { MultiDeviceRepository.startSource(context) }, label = { Text("Source") })
            FilterChip(selected = mode == Mode.SPEAKER, onClick = { MultiDeviceRepository.startDiscovery(context) }, label = { Text("Enceinte") })
            if (mode != Mode.OFF) TextButton(onClick = { MultiDeviceRepository.stopAll(context) }) { Text("Arrêter") }
        }
        when (mode) {
            Mode.SOURCE -> SourcePanel()
            Mode.SPEAKER -> SpeakerPanel()
            Mode.OFF -> {}
        }
    }
}

@Composable
private fun SourcePanel() {
    val speakers by MultiDeviceRepository.speakers.collectAsState()
    Text(
        "Ce téléphone diffuse le mix de la capture : démarrez-la dans l'onglet Mixeur. " +
            "Son propre son est retardé pour rester aligné avec les enceintes.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (speakers.isEmpty()) Text("En attente d'enceintes…")
    speakers.forEach { speaker ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(Dimens.Gutter), verticalAlignment = Alignment.CenterVertically) {
                Text(speaker.name, modifier = Modifier.weight(1f))
                Text(speaker.role.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeakerPanel() {
    val context = LocalContext.current
    val sources by MultiDeviceRepository.sources.collectAsState()
    val connectedTo by MultiDeviceRepository.connectedTo.collectAsState()
    val volume by MultiDeviceRepository.speakerVolume.collectAsState()
    val offset by MultiDeviceRepository.speakerOffsetMs.collectAsState()
    var role by rememberSaveable { mutableStateOf(SpeakerRole.LEFT) }

    Text("Rôle de ce téléphone", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        SpeakerRole.entries.forEach {
            FilterChip(selected = role == it, onClick = { role = it }, enabled = connectedTo == null, label = { Text(it.label) })
        }
    }
    if (connectedTo == null) {
        Text("Sources trouvées", style = MaterialTheme.typography.titleMedium)
        if (sources.isEmpty()) Text("Recherche sur le Wi-Fi…", style = MaterialTheme.typography.bodySmall)
        sources.forEach { source ->
            OutlinedButton(onClick = { MultiDeviceRepository.connect(context, source, role) }, modifier = Modifier.fillMaxWidth()) {
                Text(source.name)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Connecté à $connectedTo", modifier = Modifier.weight(1f))
            Button(onClick = MultiDeviceRepository::disconnect) { Text("Quitter") }
        }
        ClockStatus()
        LabeledSlider("Volume de cette enceinte", volume, 0..200, { "$it %" }, MultiDeviceRepository::setSpeakerVolume)
        LabeledSlider("Décalage", offset, -500..500, { "${if (it > 0) "+" else ""}$it ms" }, MultiDeviceRepository::setSpeakerOffset)
        Text(
            "Écho entre les enceintes ? Une enceinte plus éloignée de vous doit jouer un peu plus tôt (décalage négatif), " +
                "environ 3 ms par mètre.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Précision de l'horloge, pour juger si l'objectif de 5 ms entre enceintes est tenable sur ce Wi-Fi. */
@Composable
private fun ClockStatus() {
    var uncertainty by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            uncertainty = MultiDeviceRepository.clockUncertaintyMs()
            delay(1_000)
        }
    }
    val text = uncertainty?.let { "Horloge synchronisée à ± ${"%.1f".format(it)} ms" } ?: "Synchronisation de l'horloge…"
    Text(text, style = MaterialTheme.typography.bodySmall)
}
