package de.habits.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.habits.core.CONSOLIDATION_DAYS
import de.habits.core.Direction
import de.habits.core.HabitHistory
import de.habits.core.Lifecycle
import de.habits.core.Measure
import de.habits.core.Outcome
import de.habits.core.streakMilestones
import de.habits.core.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitDetailScreen(
    history: HabitHistory,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSetAmount: (LocalDate, Int) -> Unit,
    onPauseToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val habit = history.habit
    val today = history.today
    val color = habitColor(habit.color)
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Bearbeiten") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HabitAvatar(habit, 56.dp)
                Column {
                    Text(listOf(scheduleText(habit), targetText(habit)).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "seit ${habit.startDate.format(shortDate)}" + when (habit.lifecycle(today)) {
                            Lifecycle.PAUSED -> " · pausiert"
                            Lifecycle.ARCHIVED -> " · archiviert"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Stat(streakText(history.streak), "Serie")
                Stat("${history.streak.longestDays} T", "Beste Serie")
                Stat(percent(history.rate(today.minusDays(29), today)), "Quote 30 Tage")
            }
            if (history.streak.atRisk) {
                Text("⚠ Letzte Periode verpasst – nicht zweimal hintereinander auslassen.", style = MaterialTheme.typography.bodySmall, color = Status.fail)
            }

            history.challengeStatus?.let { c ->
                HorizontalDivider()
                SectionTitle("Challenge")
                val elapsed = c.succeeded + c.failed
                LinearProgress(if (c.total == 0) 0f else elapsed.toFloat() / c.total, color = color)
                Text("${c.succeeded} von ${c.total} geschafft · Ziel ${c.thresholdPercent} % · bis ${habit.endDate!!.format(shortDate)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        c.finished && c.passed -> "🏆 Geschafft! ${percent(c.rate)} erreicht."
                        c.finished -> "Nicht geschafft (${percent(c.rate)}). Neuer Versuch?"
                        c.passed -> "✓ Ziel schon sicher erreicht – weiter so!"
                        c.reachable -> "Auf Kurs – noch ${c.pending} offen."
                        else -> "Das Ziel ist nicht mehr erreichbar – zieh trotzdem durch."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider()
            SectionTitle("Gewohnheit festigen")
            val consolidated = history.consolidatedOn
            if (consolidated != null) {
                Text("🧱 Gefestigt seit ${consolidated.format(shortDate)}", style = MaterialTheme.typography.bodyMedium)
            } else {
                LinearProgress(history.ageDays.toFloat() / CONSOLIDATION_DAYS, color = color)
                Text(
                    "Tag ${history.ageDays} von $CONSOLIDATION_DAYS · gefestigt bei ≥ 80 % in den letzten $CONSOLIDATION_DAYS Tagen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                streakMilestones.forEach { m ->
                    val reached = history.streak.longestDays >= m
                    AssistChip(onClick = {}, label = { Text(if (reached) "🔥 $m T" else "$m T") }, enabled = reached)
                }
            }

            HorizontalDivider()
            MonthCalendar(history, onDayClick = { date ->
                if (isSingleCheck(habit, date)) onSetAmount(date, checkToggleValue(history, date)) else editDate = date
            })

            HorizontalDivider()
            SectionTitle("Letzte 8 Wochen")
            val weeks = (7 downTo 0).map { today.minusWeeks(it.toLong()).weekStart() }
            RateBars(
                values = weeks.map { history.weekRate(it) },
                labels = weeks.map { "KW${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" },
                color = color,
            )
            if (habit.measure == Measure.COUNT) {
                val month = YearMonth.from(today)
                Text(
                    "Diese Woche: ${history.sum(today.weekStart(), today)} ${habit.unit} · Diesen Monat: ${history.sum(month.atDay(1), today)} ${habit.unit}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (habit.lifecycle(today) == Lifecycle.ACTIVE || habit.lifecycle(today) == Lifecycle.PAUSED) {
                    OutlinedButton(onClick = onPauseToggle) { Text(if (habit.isPaused(today)) "Fortsetzen" else "Pausieren") }
                }
                OutlinedButton(onClick = onArchiveToggle) { Text(if (habit.archived) "Wiederherstellen" else "Archivieren") }
                TextButton(onClick = { confirmDelete = true }) { Text("Löschen", color = Status.fail) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editDate?.let { date ->
        AmountDialog(history, date, onDismiss = { editDate = null }, onSave = {
            onSetAmount(date, it)
            editDate = null
        })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${habit.name}“ löschen?") },
            text = { Text("Alle Einträge gehen verloren. Archivieren behält die Historie.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen", color = Status.fail) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

/**
 * Month grid. Day habits: green = done, red = missed, amber = partly. Weekly habits: tinted days were done,
 * the dot after each row shows the week's result.
 */
@Composable
private fun MonthCalendar(history: HabitHistory, onDayClick: (LocalDate) -> Unit) {
    val today = history.today
    val habit = history.habit
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val color = habitColor(habit.color)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vormonat") }
            Text(month.format(monthFormat), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Monat")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.values().forEach {
                Text(it.short(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(12.dp))
        }
        val first = month.atDay(1)
        val cells: List<LocalDate?> = List(first.dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (date != null) DayCell(history, date, color, onClick = { onDayClick(date) })
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                val weekPeriod = week.firstNotNullOfOrNull { d -> d?.let(history::periodAt)?.takeIf { it.weekly } }
                Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
                    val dot = when (weekPeriod?.outcome) {
                        Outcome.SUCCESS -> Status.success
                        Outcome.FAIL -> Status.fail
                        else -> null
                    }
                    if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
                }
            }
        }
        Text("Tippe auf einen Tag, um ihn nachzutragen.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCell(history: HabitHistory, date: LocalDate, color: Color, onClick: () -> Unit) {
    val habit = history.habit
    val today = history.today
    val period = history.periodAt(date)
    val amount = history.amount(date)
    val editable = habit.inLifetime(date) && !date.isAfter(today)
    val ok = habit.isOk(amount, habit.planAt(date))
    val fill: Color? = when {
        period == null || !editable -> null
        period.weekly -> when {
            habit.direction == Direction.BUILD && ok -> color.copy(alpha = 0.6f)
            habit.direction == Direction.QUIT && !ok -> Status.fail.copy(alpha = 0.6f)
            else -> null
        }
        period.outcome == Outcome.SUCCESS -> Status.success
        period.outcome == Outcome.FAIL && habit.direction == Direction.BUILD && amount > 0 -> Status.partial
        period.outcome == Outcome.FAIL -> Status.fail
        else -> null
    }
    val shape = RoundedCornerShape(8.dp)
    var m = Modifier.fillMaxWidth().aspectRatio(1f)
    m = if (fill != null) m.background(fill, shape) else m.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (period != null && editable) 0.6f else 0.2f), shape)
    if (date == today) m = m.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    if (editable) m = m.clickable(onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = if (fill != null) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (editable) 1f else 0.4f),
            )
            if (amount > 0 && (habit.measure == Measure.COUNT || habit.planAt(date).target > 1)) {
                Text("$amount", style = MaterialTheme.typography.labelSmall, color = if (fill != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
