package com.zaiten.mixer.features.mixer

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zaiten.mixer.core.capture.ActiveSessionWatcher
import com.zaiten.mixer.core.mixer.AppTrack
import com.zaiten.mixer.core.mixer.GainProcessor
import com.zaiten.mixer.core.mixer.MixerRepository
import com.zaiten.mixer.core.mixer.OriginalSoundMode

// Dimensions alignées sur l'échelle du design system (CLAUDE.md §7).
private val Gutter = 16.dp
private val Gap = 8.dp
private val StripWidth = 96.dp

@Composable
fun MixerScreen(capturing: Boolean, onStartCapture: () -> Unit) {
    val tracks by MixerRepository.tracks.collectAsState()
    val master by MixerRepository.masterPercent.collectAsState()
    var picking by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(Gutter), verticalArrangement = Arrangement.spacedBy(Gutter)) {
            Text("Mixeur", style = MaterialTheme.typography.headlineMedium)
            if (!capturing) {
                Button(onClick = onStartCapture) { Text("Démarrer la capture") }
            }
            SessionAccessPrompt()
            if (capturing) OriginalSoundNotice()
            PercentSlider(label = "Volume maître", percent = master, onChange = MixerRepository::setMaster)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Applis actives", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }) { Text("Ajouter") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                items(tracks, key = { it.uid }) { track -> TrackStrip(track) }
            }
        }
    }
    if (picking) AppPickerDialog(onDismiss = { picking = false })
}

@Composable
private fun PercentSlider(label: String, percent: Int, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text("$percent %", color = if (percent > GainProcessor.WARNING_PERCENT) Color(0xFFFFB4AB) else Color.Unspecified)
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = GainProcessor.MIN_PERCENT.toFloat()..GainProcessor.MAX_PERCENT.toFloat(),
        )
        if (percent > GainProcessor.WARNING_PERCENT) {
            Text("Au-delà de 150 % : risque pour l'audition", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrackStrip(track: AppTrack) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.width(StripWidth).padding(Gap),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(track.label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            Text("${track.volumePercent} %")
            Slider(
                value = track.volumePercent.toFloat(),
                onValueChange = { MixerRepository.setVolume(track.uid, it.toInt()) },
                valueRange = GainProcessor.MIN_PERCENT.toFloat()..GainProcessor.MAX_PERCENT.toFloat(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Gap / 2)) {
                FilterChip(selected = track.muted, onClick = { MixerRepository.toggleMute(track.uid) }, label = { Text("M") })
                FilterChip(selected = track.solo, onClick = { MixerRepository.toggleSolo(track.uid) }, label = { Text("S") })
            }
            TextButton(onClick = { MixerRepository.removeTrack(track.uid) }) { Text("Retirer") }
        }
    }
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { loadLaunchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Ajouter une appli") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(384.dp)) {
                items(apps, key = { it.uid }) { app ->
                    TextButton(onClick = { MixerRepository.addTrack(app); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(app.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

/** Propose l'accès aux notifications, sans lequel les applis qui jouent ne sont pas détectées. */
@Composable
private fun SessionAccessPrompt() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ActiveSessionWatcher.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        granted = ActiveSessionWatcher.hasAccess(context)
        onPauseOrDispose { }
    }
    if (granted) return
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text(
            "Autorisez l'accès aux notifications pour ajouter automatiquement les applis qui jouent du son. " +
                "Relancez ensuite la capture.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("Autoriser") }
    }
}

@Composable
private fun OriginalSoundNotice() {
    val mode by MixerRepository.originalSoundMode.collectAsState()
    val text = when (mode) {
        OriginalSoundMode.PENDING -> "Lancez un son dans une appli pour activer le mixage."
        OriginalSoundMode.MUTED_BY_APP -> "Le son passe par le mixeur. Réglez le volume ici plutôt qu'avec les touches."
        OriginalSoundMode.ORIGINAL_STILL_PLAYS ->
            "Cet appareil ne permet pas de couper le son d'origine : le mixeur peut augmenter " +
                "le volume d'une appli, mais pas le baisser."
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
}
