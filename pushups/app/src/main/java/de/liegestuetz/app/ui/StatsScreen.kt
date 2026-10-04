package de.liegestuetz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.liegestuetz.app.data.AppState
import de.liegestuetz.core.PeriodStats
import de.liegestuetz.core.Totals
import de.liegestuetz.core.recentMonths
import de.liegestuetz.core.recentWeeks
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

private val shortMonth = DateTimeFormatter.ofPattern("MMM", GERMAN)

@Composable
fun StatsScreen(state: AppState, totals: Totals) {
    var monthly by rememberSaveable { mutableStateOf(false) }
    val history = state.history
    val periods = if (monthly) history.recentMonths(6) else history.recentWeeks(8)
    val current = periods.last()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !monthly, onClick = { monthly = false }, label = { Text("Wochen") })
            FilterChip(selected = monthly, onClick = { monthly = true }, label = { Text("Monate") })
        }

        SectionCard {
            Text(if (monthly) "Dieser Monat" else "Diese Woche", style = MaterialTheme.typography.titleMedium)
            RateRow("✨ Perfekte Tage", current.perfectDays, current.trackedDays, current.perfectRate)
            RateRow("💪 Zielerfüllung", current.repsTowardsTarget, current.repsTarget, current.repsRate)
            Text("Liegestütze gesamt: ${current.reps}", style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard {
            Text("Anteil perfekter Tage", style = MaterialTheme.typography.titleMedium)
            BarChart(periods, label = { p ->
                if (monthly) p.from.format(shortMonth) else "KW${p.from.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
            })
        }

        SectionCard {
            Text("Gesamt", style = MaterialTheme.typography.titleMedium)
            Text("🔥 Längste Serie: ${totals.streaks.longest} Tage")
            Text("✨ Perfekte Tage: ${totals.perfectDays}")
            Text("💪 Liegestütze: ${totals.reps}")
            Text("⚡ Bester Tag: ${totals.bestDay}")
            Text("⭐ Perfekte Wochen: ${totals.perfectWeeks} · 🏆 Perfekte Monate: ${totals.perfectMonths}")
        }
    }
}

@Composable
private fun RateRow(label: String, done: Int, total: Int, rate: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, Modifier.weight(1f))
            Text(if (total == 0) "–" else "$done / $total · ${(rate * 100).toInt()} %", fontWeight = FontWeight.SemiBold)
        }
        Progress(rate, color = rateColor(rate))
    }
}

private fun rateColor(rate: Float) = when {
    rate >= 0.9f -> StatusColors.perfect
    rate >= 0.5f -> StatusColors.partial
    else -> StatusColors.missed
}

@Composable
private fun BarChart(periods: List<PeriodStats>, label: (PeriodStats) -> String) {
    Row(
        Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        periods.forEach { p ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (p.trackedDays == 0) "" else "${(p.perfectRate * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (p.trackedDays > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(maxOf(p.perfectRate, 0.02f))
                                .background(rateColor(p.perfectRate), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                    }
                }
                Text(label(p), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
