package de.habits.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.habits.core.Direction
import de.habits.core.Habit
import de.habits.core.HabitHistory
import de.habits.core.Measure
import de.habits.core.Schedule
import de.habits.core.Streak
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val GERMAN: Locale = Locale.GERMANY
val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", GERMAN)
val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", GERMAN)
val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, GERMAN).removeSuffix(".")

fun percent(rate: Float?) = rate?.let { "${(it * 100).toInt()} %" } ?: "–"

fun scheduleText(h: Habit): String {
    val plan = h.currentPlan
    return when (val s = plan.schedule) {
        is Schedule.OnDays -> when {
            s.days.size == 7 -> "Täglich"
            else -> DayOfWeek.values().filter { it in s.days }.joinToString(", ") { it.short() }
        }
        is Schedule.PerWeek -> if (h.direction == Direction.BUILD) "${s.times}× pro Woche" else "höchstens ${s.times} Tage/Woche"
    }
}

/** "30 Wdh." / "höchstens 2 Kaffee" / "" for check habits. */
fun targetText(h: Habit, target: Int = h.currentPlan.target): String = when {
    h.measure == Measure.CHECK -> if (h.direction == Direction.BUILD && target > 1) "$target× am Tag" else ""
    h.direction == Direction.BUILD -> "$target ${h.unit}".trim()
    else -> "höchstens $target ${h.unit}".trim()
}

fun streakText(s: Streak): String {
    val unit = if (s.weekly) (if (s.current == 1) "Woche" else "Wochen") else (if (s.current == 1) "Tag" else "Tage")
    return "${s.current} $unit"
}

@Composable
fun HabitAvatar(h: Habit, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(habitColor(h.color).copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(h.emoji, style = if (size > 48.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge) }
}

@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun LinearProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A small labelled figure, used in rows of key numbers. */
@Composable
fun RowScope.Stat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Vertical bars for rates 0..1 (null = no data), with labels below. */
@Composable
fun RateBars(values: List<Float?>, labels: List<String>, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v?.let { "${(it * 100).toInt()}" } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(maxOf(v ?: 0f, 0.03f))
                            .background(
                                if (v == null) MaterialTheme.colorScheme.surfaceVariant else color,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            )
                    )
                }
                Text(labels[i], style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Tiny bars for table rows. */
@Composable
fun MiniBars(values: List<Float?>, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            Box(
                Modifier
                    .size(width = 5.dp, height = (20 * maxOf(v ?: 0f, 0.1f)).dp)
                    .background(if (v == null) MaterialTheme.colorScheme.surfaceVariant else color, RoundedCornerShape(1.dp))
            )
        }
    }
}

/** Enter or correct the amount of a habit on one day. */
@Composable
fun AmountDialog(history: HabitHistory, date: LocalDate, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val habit = history.habit
    val target = habit.planAt(date).target
    var text by remember { mutableStateOf(history.amount(date).toString()) }
    val value = text.toIntOrNull() ?: 0
    fun add(n: Int) { text = maxOf(0, value + n).toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${habit.emoji} ${habit.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(date.format(dayFormat) + (targetText(habit, target).takeIf { it.isNotEmpty() }?.let { " · Ziel: $it" } ?: ""))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter(Char::isDigit).take(5) },
                    label = { Text(habit.unit.ifBlank { "Anzahl" }) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1, 1, 5, 10).forEach { n ->
                        FilledTonalButton(onClick = { add(n) }, modifier = Modifier.weight(1f)) { Text(if (n > 0) "+$n" else "$n") }
                    }
                }
                if (habit.direction == Direction.BUILD && value < target) {
                    TextButton(onClick = { text = target.toString() }) { Text("✓ Ziel erreicht ($target)") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Check habits that are ticked off once a day (or marked as slipped) – a tap toggles them. */
fun isSingleCheck(h: Habit, date: LocalDate) =
    h.measure == Measure.CHECK && (h.direction == Direction.QUIT || h.planAt(date).target <= 1)

/** Toggle for single check habits: done / slipped or not. */
fun checkToggleValue(history: HabitHistory, date: LocalDate): Int = if (history.amount(date) > 0) 0 else 1

/**
 * Labels for the tick boxes of a multi-check habit: the reminder times if there is one per box
 * (e.g. 07:30 / 21:30 for brushing teeth), otherwise none.
 */
fun checkLabels(h: Habit, count: Int): List<String>? =
    h.reminders.sorted().takeIf { it.size == count }?.map { it.format(timeFormat) }

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
}
