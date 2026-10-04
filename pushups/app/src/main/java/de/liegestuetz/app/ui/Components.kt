package de.liegestuetz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.liegestuetz.app.data.Repository
import de.liegestuetz.core.AchievementProgress
import de.liegestuetz.core.DayRecord
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

/** A number with −/+ buttons below it, used wherever reps or the target are adjusted. */
@Composable
fun Stepper(
    value: String,
    onChange: (delta: Int) -> Unit,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    bigStep: Int = 5,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-bigStep, -1, 1, bigStep).forEach { delta ->
                FilledTonalButton(
                    onClick = { onChange(delta) },
                    enabled = if (delta < 0) canDecrease else canIncrease,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(if (delta < 0) "−${-delta}" else "+$delta") }
            }
        }
    }
}

/** Lets the user correct any past day (e.g. forgot to log it). */
@Composable
fun EditDayDialog(
    day: DayRecord,
    onDismiss: () -> Unit,
    onSave: (reps: Int) -> Unit,
) {
    var reps by remember(day) { mutableIntStateOf(day.reps) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.format(dayFormat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("💪 Liegestütze (Ziel ${day.target})")
                Stepper(
                    value = "$reps",
                    onChange = { reps = (reps + it).coerceIn(0, Repository.MAX_REPS) },
                    canDecrease = reps > 0,
                    canIncrease = reps < Repository.MAX_REPS,
                )
                TextButton(onClick = { reps = day.target }) { Text("Auf Ziel setzen (${day.target})") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(reps) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
