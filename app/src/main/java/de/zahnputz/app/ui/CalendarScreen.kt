package de.zahnputz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.zahnputz.app.data.AppState
import de.zahnputz.core.DayStatus
import de.zahnputz.core.monthStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN)

@Composable
fun CalendarScreen(state: AppState, onEdit: (LocalDate, Int, Boolean) -> Unit) {
    val history = state.history
    var month by remember { mutableStateOf(YearMonth.from(state.today)) }
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(month.format(monthFormat), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = { month = month.plusMonths(1) },
                enabled = month < YearMonth.from(state.today),
            ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
        }

        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, GERMAN),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                status = history.status(date),
                                brush = history.day(date).brushCount,
                                flossed = history.day(date).flossed,
                                isToday = date == state.today,
                                onClick = if (!date.isAfter(state.today)) ({ editing = date }) else null,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }

        Legend()

        val stats = history.monthStats(month)
        if (stats.trackedDays > 0) {
            SectionCard {
                Text("Monatsbilanz", style = MaterialTheme.typography.titleMedium)
                Text("✨ Perfekte Tage: ${stats.perfectDays} / ${stats.trackedDays}")
                Text("🪥 Putzen: ${stats.brushDone} / ${stats.brushTarget} (${(stats.brushRate * 100).toInt()} %)")
                Text("🧵 Zahnseide: ${stats.flossDone} / ${stats.flossRequired} (${(stats.flossRate * 100).toInt()} %)")
            }
        }
        Text("Tippe auf einen Tag, um ihn nachzutragen oder zu korrigieren.", style = MaterialTheme.typography.bodySmall)
    }

    editing?.let { date ->
        EditDayDialog(
            day = history.day(date),
            onDismiss = { editing = null },
            onSave = { brush, floss ->
                onEdit(date, brush, floss)
                editing = null
            },
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    status: DayStatus,
    brush: Int,
    flossed: Boolean,
    isToday: Boolean,
    onClick: (() -> Unit)?,
) {
    val color = status.color()
    val shape = RoundedCornerShape(8.dp)
    var m = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
    m = if (color != null) m.background(color.copy(alpha = 0.8f)) else m.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    if (isToday) m = m.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (color != null) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            if (status != DayStatus.NOT_TRACKED || brush > 0 || flossed) {
                Text(
                    (if (brush > 0) "🪥$brush" else "") + (if (flossed) "🧵" else ""),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(StatusColors.perfect, "perfekt")
        LegendItem(StatusColors.partial, "teilweise")
        LegendItem(StatusColors.missed, "verpasst")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
