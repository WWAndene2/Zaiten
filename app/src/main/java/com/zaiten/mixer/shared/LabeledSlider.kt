package com.zaiten.mixer.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/** Curseur horizontal avec libellé et valeur affichée (entiers). */
@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    range: IntRange,
    valueText: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

/** Texte d'une balance : « Centre », « G 40 », « D 25 ». */
fun balanceText(balance: Int): String = when {
    balance < 0 -> "G ${-balance}"
    balance > 0 -> "D $balance"
    else -> "Centre"
}
