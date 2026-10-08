package de.habits.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.habits.app.reminder.ReminderScheduler
import de.habits.core.Challenge
import de.habits.core.Direction
import de.habits.core.Habit
import de.habits.core.Measure
import de.habits.core.Plan
import de.habits.core.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private data class Template(
    val emoji: String,
    val name: String,
    val direction: Direction = Direction.BUILD,
    val measure: Measure = Measure.CHECK,
    val target: Int = 1,
    val unit: String = "",
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val perWeek: Int? = null,
    val reminders: List<LocalTime> = emptyList(),
)

private val templates = listOf(
    Template("🪥", "Zähneputzen", target = 2, reminders = listOf(LocalTime.of(7, 30), LocalTime.of(21, 30))),
    Template("🧵", "Zahnseide", days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
    Template("💪", "Liegestütze", measure = Measure.COUNT, target = 20, unit = "Wdh."),
    Template("🏃", "Sport", perWeek = 3),
    Template("📖", "Lesen", measure = Measure.COUNT, target = 15, unit = "min"),
    Template("💧", "Wasser trinken", measure = Measure.COUNT, target = 8, unit = "Gläser"),
    Template("🍬", "Kein Zucker", direction = Direction.QUIT, target = 0),
    Template("📵", "Handy max. 1 h", direction = Direction.QUIT, measure = Measure.COUNT, target = 60, unit = "min"),
)

private val durationPresets = listOf(7, 21, 30, 66, 90)
private const val MAX_TIMES_PER_DAY = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditor(initial: Habit?, today: LocalDate, onSave: (Habit) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val plan = initial?.currentPlan
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "✅") }
    var color by remember { mutableIntStateOf(initial?.color ?: 0) }
    var direction by remember { mutableStateOf(initial?.direction ?: Direction.BUILD) }
    var measure by remember { mutableStateOf(initial?.measure ?: Measure.CHECK) }
    var timesPerDay by remember { mutableIntStateOf(plan?.target?.takeIf { initial?.direction == Direction.BUILD && it in 1..MAX_TIMES_PER_DAY } ?: 1) }
    var targetText by remember { mutableStateOf(plan?.target?.takeIf { initial?.measure == Measure.COUNT }?.toString() ?: "10") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var weekly by remember { mutableStateOf(plan?.schedule is Schedule.PerWeek) }
    var days by remember { mutableStateOf((plan?.schedule as? Schedule.OnDays)?.days ?: DayOfWeek.values().toSet()) }
    var perWeek by remember { mutableIntStateOf((plan?.schedule as? Schedule.PerWeek)?.times ?: 3) }
    var startDate by remember { mutableStateOf(initial?.startDate ?: today) }
    var isChallenge by remember { mutableStateOf(initial?.challenge != null) }
    var durationDays by remember {
        mutableIntStateOf(initial?.challenge?.let { ChronoUnit.DAYS.between(initial.startDate, it.endDate).toInt() + 1 } ?: 30)
    }
    var threshold by remember { mutableIntStateOf(initial?.challenge?.thresholdPercent ?: 80) }
    var reminders by remember { mutableStateOf(initial?.reminders ?: emptyList()) }

    fun applyTemplate(t: Template) {
        emoji = t.emoji; name = t.name; direction = t.direction; measure = t.measure
        targetText = t.target.toString(); unit = t.unit
        timesPerDay = if (t.measure == Measure.CHECK) t.target.coerceIn(1, MAX_TIMES_PER_DAY) else 1
        weekly = t.perWeek != null; t.perWeek?.let { perWeek = it }; days = t.days
        reminders = t.reminders
    }

    val target = when {
        measure == Measure.COUNT -> targetText.toIntOrNull() ?: 0
        direction == Direction.BUILD -> timesPerDay
        else -> 0
    }
    val perWeekRange = if (direction == Direction.BUILD) 1..7 else 0..6
    val valid = name.isNotBlank() && (weekly || days.isNotEmpty()) && (measure == Measure.CHECK || direction == Direction.QUIT || target > 0)

    fun build(): Habit {
        val schedule = if (weekly) Schedule.PerWeek(perWeek.coerceIn(perWeekRange)) else Schedule.OnDays(days)
        val challenge = if (isChallenge) Challenge(startDate.plusDays(durationDays - 1L), threshold) else null
        val base = initial?.withStartDate(startDate)?.withPlan(maxOf(today, startDate), target, schedule)
            ?: Habit(0, name, emoji, color, direction, measure, unit, listOf(Plan(startDate, target, schedule)))
        return base.copy(
            name = name.trim(), emoji = emoji.ifBlank { "✅" }, color = color, direction = direction, measure = measure,
            unit = unit.trim(), challenge = challenge, reminders = reminders.sorted(),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Neuer Habit" else "Bearbeiten") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Abbrechen") } },
                actions = { TextButton(onClick = { onSave(build()) }, enabled = valid) { Text("Speichern") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (initial == null) {
                SectionTitle("Vorlage")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { t -> InputChip(selected = name == t.name, onClick = { applyTemplate(t) }, label = { Text("${t.emoji} ${t.name}") }) }
                }
            }

            SectionTitle("Name")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    modifier = Modifier.width(72.dp),
                    singleLine = true,
                    label = { Text("Icon") },
                )
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Name") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                habitColors.forEachIndexed { i, c -> ColorDot(c, selected = color == i, onClick = { color = i }) }
            }

            SectionTitle("Ziel")
            ChoiceRow(listOf("Angewöhnen" to Direction.BUILD, "Abgewöhnen" to Direction.QUIT), direction) {
                direction = it
                perWeek = perWeek.coerceIn(if (it == Direction.BUILD) 1..7 else 0..6)
            }
            ChoiceRow(listOf("Abhaken" to Measure.CHECK, "Menge" to Measure.COUNT), measure) { measure = it }
            if (measure == Measure.CHECK && direction == Direction.BUILD) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Wie oft am Tag", Modifier.weight(1f))
                    FilledTonalButton(onClick = { timesPerDay-- }, enabled = timesPerDay > 1) { Text("−") }
                    Text("$timesPerDay×", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    FilledTonalButton(onClick = { timesPerDay++ }, enabled = timesPerDay < MAX_TIMES_PER_DAY) { Text("+") }
                }
            }
            if (measure == Measure.COUNT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        label = { Text(if (direction == Direction.BUILD) "pro Tag" else "höchstens") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(value = unit, onValueChange = { unit = it.take(12) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Einheit (z. B. min)") })
                }
            }
            Text(
                when {
                    direction == Direction.BUILD && measure == Measure.CHECK && timesPerDay == 1 -> "Erfüllt, wenn abgehakt."
                    direction == Direction.BUILD && measure == Measure.CHECK ->
                        "$timesPerDay Kreise zum Abhaken. Mit $timesPerDay Erinnerungen stehen deren Uhrzeiten unter den Kreisen."
                    direction == Direction.BUILD -> "Erfüllt ab $target ${unit.trim()} am Tag."
                    measure == Measure.CHECK -> "Erfüllt, solange du keinen Ausrutscher einträgst."
                    else -> "Erfüllt, solange du höchstens $target ${unit.trim()} am Tag einträgst."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Rhythmus")
            ChoiceRow(listOf("Feste Tage" to false, "x-mal pro Woche" to true), weekly) { weekly = it }
            if (weekly) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { perWeek-- }, enabled = perWeek > perWeekRange.first) { Text("−") }
                    Text("$perWeek", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    FilledTonalButton(onClick = { perWeek++ }, enabled = perWeek < perWeekRange.last) { Text("+") }
                }
                Text(
                    if (direction == Direction.BUILD) "$perWeek Tage pro Woche, frei verteilt (Mo–So)." else "Höchstens $perWeek Ausnahme-Tage pro Woche (Mo–So).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.values().forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(d.short()) })
                    }
                }
            }

            SectionTitle("Laufzeit")
            ChoiceRow(listOf("Unbegrenzt" to false, "Challenge" to true), isChallenge) { isChallenge = it }
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth)
                    .apply { datePicker.maxDate = System.currentTimeMillis() }
                    .show()
            }) { Text("Start: ${startDate.format(shortDate)}") }
            if (isChallenge) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    durationPresets.forEach { n -> FilterChip(selected = durationDays == n, onClick = { durationDays = n }, label = { Text("$n Tage") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { durationDays = maxOf(1, durationDays - 1) }) { Text("−") }
                    Text("$durationDays Tage · bis ${startDate.plusDays(durationDays - 1L).format(shortDate)}", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { durationDays = minOf(999, durationDays + 1) }) { Text("+") }
                }
                Text("Geschafft ab $threshold % erfüllten ${if (weekly) "Wochen" else "Tagen"}", style = MaterialTheme.typography.bodyMedium)
                Slider(value = threshold.toFloat(), onValueChange = { threshold = (it / 5).toInt() * 5 }, valueRange = 50f..100f, steps = 9)
            }

            SectionTitle("Erinnerungen")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminders.forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { reminders = reminders - t },
                        label = { Text(t.format(timeFormat)) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Entfernen", Modifier.width(16.dp)) },
                    )
                }
                if (reminders.size < ReminderScheduler.MAX_REMINDERS) {
                    TextButton(onClick = {
                        val initialTime = reminders.lastOrNull()?.plusHours(1) ?: LocalTime.of(9, 0)
                        TimePickerDialog(context, { _, h, m ->
                            val t = LocalTime.of(h, m)
                            if (t !in reminders) reminders = (reminders + t).sorted()
                        }, initialTime.hour, initialTime.minute, true).show()
                    }) { Text("+ Uhrzeit") }
                }
            }
            if (initial != null) {
                Text(
                    "Geänderte Ziele gelten ab heute – vergangene Tage behalten ihre damaligen Ziele.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) -> FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) }) }
    }
}
