package com.zaiten.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.zaiten.mixer.features.effects.EffectsScreen
import com.zaiten.mixer.features.mixer.MixerScreen
import com.zaiten.mixer.features.presets.PresetsScreen
import com.zaiten.mixer.features.routing.RoutingScreen

/** Onglets de la maquette ; « 3D » s'ajoutera avec la spatialisation (étape 8). */
private enum class Tab(val label: String, val icon: Int) {
    MIXER("Mixeur", android.R.drawable.ic_lock_silent_mode_off),
    ROUTING("Routage", android.R.drawable.ic_menu_share),
    EFFECTS("Effets", android.R.drawable.ic_menu_manage),
    PRESETS("Presets", android.R.drawable.ic_menu_agenda),
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
                        icon = { Icon(painterResource(it.icon), contentDescription = null) },
                        label = { Text(it.label) },
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
                Tab.PRESETS -> PresetsScreen()
            }
        }
    }
}
