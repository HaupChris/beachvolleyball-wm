package de.zahnputz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.zahnputz.core.AchievementProgress
import de.zahnputz.core.DayRecord
import java.time.format.DateTimeFormatter
import java.util.Locale

val GERMAN: Locale = Locale.GERMANY
val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Progress(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = color,
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
    )
}

@Composable
fun AchievementRow(p: AchievementProgress) {
    val a = p.achievement
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (p.unlocked) a.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.title, fontWeight = FontWeight.SemiBold)
            Text(a.description, style = MaterialTheme.typography.bodySmall)
            if (!p.unlocked) {
                Progress(p.progress)
                Text("${p.value} / ${a.target}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Lets the user correct any past day (e.g. forgot to tick it off). */
@Composable
fun EditDayDialog(
    day: DayRecord,
    onDismiss: () -> Unit,
    onSave: (brushCount: Int, flossed: Boolean) -> Unit,
) {
    var brush by remember(day) { mutableIntStateOf(day.brushCount) }
    var floss by remember(day) { mutableStateOf(day.flossed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.format(dayFormat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🪥 Geputzt", Modifier.weight(1f))
                    FilledTonalButton(onClick = { if (brush > 0) brush-- }) { Text("−") }
                    Text("$brush / ${day.brushTarget}", fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { if (brush < 9) brush++ }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (day.flossRequired) "🧵 Zahnseide" else "🧵 Zahnseide (Bonus)",
                        Modifier.weight(1f),
                    )
                    Checkbox(checked = floss, onCheckedChange = { floss = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(brush, floss) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
