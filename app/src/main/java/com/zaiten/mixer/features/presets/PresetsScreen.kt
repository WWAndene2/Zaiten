package com.zaiten.mixer.features.presets

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zaiten.mixer.core.effects.BuiltInPresets
import com.zaiten.mixer.core.effects.EffectsRepository
import com.zaiten.mixer.core.effects.Preset
import com.zaiten.mixer.core.storage.SettingsJson
import com.zaiten.mixer.shared.Dimens

/** Presets fournis et personnels : appliquer, enregistrer, renommer, supprimer, exporter, importer (§2.4). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsScreen() {
    val context = LocalContext.current
    val active by EffectsRepository.activePreset.collectAsState()
    val custom by EffectsRepository.customPresets.collectAsState()
    var naming by remember { mutableStateOf<NameDialog?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(SettingsJson.presetsToJson(custom).toByteArray()) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        val presets = text?.let(SettingsJson::presetsFromJson)
        if (presets == null) Toast.makeText(context, "Fichier de presets invalide", Toast.LENGTH_SHORT).show()
        else EffectsRepository.import(presets)
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        Text("Presets", style = MaterialTheme.typography.headlineMedium)
        Text("Fournis", style = MaterialTheme.typography.titleMedium)
        BuiltInPresets.all.forEach { PresetRow(it, active == it.name, onRename = null, onDelete = null) }
        Text("Personnels", style = MaterialTheme.typography.titleMedium)
        if (custom.isEmpty()) Text("Réglez les effets puis enregistrez-les ici.", style = MaterialTheme.typography.bodySmall)
        custom.forEach { preset ->
            PresetRow(
                preset,
                active == preset.name,
                onRename = { naming = NameDialog.Rename(preset.name) },
                onDelete = { EffectsRepository.delete(preset.name) },
            )
        }
        Text("Comparaison A/B", style = MaterialTheme.typography.titleMedium)
        val ab by EffectsRepository.abSlots.collectAsState()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            OutlinedButton(onClick = EffectsRepository::storeA) { Text(if (ab.first == null) "Mémoriser A" else "Remplacer A") }
            OutlinedButton(onClick = EffectsRepository::storeB) { Text(if (ab.second == null) "Mémoriser B" else "Remplacer B") }
            OutlinedButton(onClick = EffectsRepository::toggleAb, enabled = ab.first != null && ab.second != null) { Text("Basculer A ↔ B") }
        }
        Text("Actions", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            OutlinedButton(onClick = { naming = NameDialog.Save }) { Text("Enregistrer") }
            OutlinedButton(onClick = { exporter.launch("presets-zaiten.json") }, enabled = custom.isNotEmpty()) { Text("Exporter") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain")) }) { Text("Importer") }
        }
    }

    naming?.let { dialog ->
        NamePrompt(
            title = if (dialog is NameDialog.Rename) "Renommer" else "Enregistrer les réglages",
            initial = (dialog as? NameDialog.Rename)?.oldName.orEmpty(),
            onDismiss = { naming = null },
            onConfirm = { name ->
                val ok = when (dialog) {
                    NameDialog.Save -> EffectsRepository.saveCurrentAs(name)
                    is NameDialog.Rename -> EffectsRepository.rename(dialog.oldName, name)
                }
                if (ok) naming = null else Toast.makeText(context, "Nom vide ou déjà pris", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

private sealed interface NameDialog {
    data object Save : NameDialog
    data class Rename(val oldName: String) : NameDialog
}

@Composable
private fun PresetRow(preset: Preset, active: Boolean, onRename: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (active) colors.primaryContainer else colors.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable { EffectsRepository.apply(preset) },
    ) {
        Row(modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = Dimens.Gap), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.name, modifier = Modifier.weight(1f))
            onRename?.let { TextButton(onClick = it) { Text("Renommer") } }
            onDelete?.let { TextButton(onClick = it) { Text("Supprimer") } }
        }
    }
}

@Composable
private fun NamePrompt(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
