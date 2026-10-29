package com.zaiten.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.zaiten.mixer.features.effects.EffectsScreen
import com.zaiten.mixer.features.mediasync.MediaSyncScreen
import com.zaiten.mixer.features.multidevice.SpeakersScreen
import com.zaiten.mixer.shared.Dimens
import com.zaiten.mixer.features.mixer.MixerScreen
import com.zaiten.mixer.features.presets.PresetsScreen
import com.zaiten.mixer.features.routing.RoutingScreen
import com.zaiten.mixer.features.spatial.SpatialScreen

/** Onglets de la maquette. */
private enum class Tab(val label: String, val icon: Int) {
    MIXER("Mixeur", android.R.drawable.ic_lock_silent_mode_off),
    ROUTING("Routage", android.R.drawable.ic_menu_share),
    EFFECTS("Effets", android.R.drawable.ic_menu_manage),
    SPATIAL("3D", android.R.drawable.ic_menu_compass),
    PRESETS("Presets", android.R.drawable.ic_menu_agenda),
    SYNC("Synchro", android.R.drawable.ic_menu_rotate),
}

@Composable
fun AppShell(capturing: Boolean, onStartCapture: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.MIXER) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(painterResource(it.icon), contentDescription = it.label) },
                        // Six onglets ne tiennent pas avec leurs libellés sur 360 dp : seul l'onglet actif l'affiche.
                        label = { Text(it.label, maxLines = 1, softWrap = false) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.MIXER -> MixerScreen(capturing, onStartCapture)
                Tab.ROUTING -> RoutingScreen()
                Tab.EFFECTS -> EffectsScreen()
                Tab.SPATIAL -> SpatialScreen()
                Tab.PRESETS -> PresetsScreen()
                Tab.SYNC -> SyncTab()
            }
        }
    }
}

/** Onglet Synchro : média vers une TV ou console (étape 9) et groupe d'enceintes (étape 10). */
@Composable
private fun SyncTab() {
    var speakers by rememberSaveable { mutableStateOf(false) }
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = Dimens.Gap)) {
            SegmentedButton(selected = !speakers, onClick = { speakers = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Média") }
            SegmentedButton(selected = speakers, onClick = { speakers = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Enceintes") }
        }
        if (speakers) SpeakersScreen() else MediaSyncScreen()
    }
}
