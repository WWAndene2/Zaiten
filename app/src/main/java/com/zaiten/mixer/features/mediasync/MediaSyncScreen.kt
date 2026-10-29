package com.zaiten.mixer.features.mediasync

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.zaiten.mixer.core.mediasync.MediaSyncRepository
import com.zaiten.mixer.core.mediasync.MediaSyncRepository.Status
import com.zaiten.mixer.core.mediasync.RemotePlayback
import com.zaiten.mixer.core.mediasync.RemoteState
import com.zaiten.mixer.core.mediasync.SyncClock
import com.zaiten.mixer.shared.Dimens
import com.zaiten.mixer.shared.LabeledSlider
import kotlinx.coroutines.delay

/** Vérification de la dérive : assez fréquente pour suivre une pause ou un saut sans à-coups. */
private const val SYNC_TICK_MS = 500L

/**
 * Synchro média (étape 9) : couplage avec YouTube sur l'écran, lecteur YouTube officiel (intégrable)
 * qui suit l'écran, décalage réglable. Le lecteur reste visible : les règles de YouTube interdisent
 * de le masquer pour n'en garder que le son.
 */
@Composable
fun MediaSyncScreen() {
    val status by MediaSyncRepository.status.collectAsState()
    val screenName by MediaSyncRepository.screenName.collectAsState()
    val error by MediaSyncRepository.error.collectAsState()
    val offset by MediaSyncRepository.offsetMs.collectAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gutter),
    ) {
        Text("Synchro", style = MaterialTheme.typography.headlineMedium)
        when (status) {
            Status.UNPAIRED, Status.ERROR -> PairingForm(error)
            Status.CONNECTING -> Text("Connexion à ${screenName ?: "l'écran"}…")
            Status.CONNECTED -> Row {
                Text("Connecté à ${screenName ?: "l'écran"}", modifier = Modifier.weight(1f))
                TextButton(onClick = MediaSyncRepository::unpair) { Text("Dissocier") }
            }
        }
        if (status == Status.CONNECTED || status == Status.CONNECTING) {
            SyncedPlayer()
            LabeledSlider(
                label = "Décalage du son",
                value = offset,
                range = -MediaSyncRepository.MAX_OFFSET_MS..MediaSyncRepository.MAX_OFFSET_MS,
                valueText = { "${if (it > 0) "+" else ""}$it ms" },
                onChange = MediaSyncRepository::setOffset,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                OutlinedButton(onClick = { MediaSyncRepository.setOffset(offset - MediaSyncRepository.OFFSET_STEP_MS) }) { Text("−10 ms") }
                OutlinedButton(onClick = { MediaSyncRepository.setOffset(offset + MediaSyncRepository.OFFSET_STEP_MS) }) { Text("+10 ms") }
            }
            Text(
                "Le son du casque est en avance sur la TV ? Augmentez le décalage. En retard ? Diminuez-le.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PairingForm(error: String?) {
    var code by remember { mutableStateOf("") }
    Text(
        "Sur la PS5 ou la TV, ouvrez YouTube → Réglages → « Associer un téléphone » (code TV), " +
            "puis saisissez le code affiché.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(PAIRING_CODE_MAX) },
        label = { Text("Code TV") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { MediaSyncRepository.pair(code) }, enabled = code.length >= PAIRING_CODE_MIN) { Text("Associer") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

// Le code TV de YouTube compte 12 chiffres ; on laisse une marge si le format évolue.
private const val PAIRING_CODE_MIN = 8
private const val PAIRING_CODE_MAX = 16

@Composable
private fun SyncedPlayer() {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tracker = remember { YouTubePlayerTracker() }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    val remote by MediaSyncRepository.playback.collectAsState()

    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { context ->
            YouTubePlayerView(context).apply {
                lifecycle.addObserver(this)
                addYouTubePlayerListener(tracker)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) { player = youTubePlayer }
                })
            }
        },
        onRelease = { view -> lifecycle.removeObserver(view); view.release() },
    )
    DisposableEffect(Unit) { onDispose { player = null } }

    // Suit l'écran : changement de vidéo, lecture/pause, et recalage si la dérive dépasse la tolérance.
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            // Lus à chaque tick plutôt que capturés : la boucle suit le dernier état et le dernier décalage.
            MediaSyncRepository.playback.value?.let { follow(p, tracker, it, MediaSyncRepository.offsetMs.value) }
            delay(SYNC_TICK_MS)
        }
    }
    remote?.takeIf { it.videoId == null }?.let {
        Text("Rien en lecture sur l'écran.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun follow(player: YouTubePlayer, tracker: YouTubePlayerTracker, remote: RemotePlayback, offsetMs: Int) {
    val videoId = remote.videoId
    if (videoId == null) {
        player.pause()
        return
    }
    val expected = SyncClock.expectedPositionSec(remote, SystemClock.elapsedRealtime(), offsetMs)
    if (tracker.videoId != videoId) {
        if (remote.state == RemoteState.PLAYING) player.loadVideo(videoId, expected.toFloat())
        else player.cueVideo(videoId, expected.toFloat())
        return
    }
    val playing = tracker.state == PlayerConstants.PlayerState.PLAYING
    when (remote.state) {
        RemoteState.PLAYING -> {
            if (!playing && tracker.state != PlayerConstants.PlayerState.BUFFERING) player.play()
            if (SyncClock.needsSeek(tracker.currentSecond.toDouble(), expected)) player.seekTo(expected.toFloat())
        }
        RemoteState.PAUSED, RemoteState.STOPPED -> {
            if (playing) player.pause()
            if (SyncClock.needsSeek(tracker.currentSecond.toDouble(), expected)) player.seekTo(expected.toFloat())
        }
        RemoteState.BUFFERING -> if (playing) player.pause() // L'écran attend (chargement, pub) : on attend aussi.
    }
}
