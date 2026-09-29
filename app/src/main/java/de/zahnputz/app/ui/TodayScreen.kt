package de.zahnputz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.zahnputz.app.data.AppState
import de.zahnputz.core.DayStatus
import de.zahnputz.core.Totals
import de.zahnputz.core.levelFor
import de.zahnputz.core.nextGoals
import de.zahnputz.core.weekStart
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TodayScreen(
    state: AppState,
    totals: Totals,
    onBrushCount: (Int) -> Unit,
    onFloss: (Boolean) -> Unit,
) {
    val history = state.history
    val today = history.day(state.today)
    val level = levelFor(totals.xp)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.today.format(dayFormat), style = MaterialTheme.typography.titleMedium)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔥 ${totals.streaks.current}", style = MaterialTheme.typography.displaySmall, color = StatusColors.flame)
                    Text(if (totals.streaks.current == 1) "Tag in Folge" else "Tage in Folge")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Level ${level.number}", fontWeight = FontWeight.Bold)
                    Text(level.title, style = MaterialTheme.typography.bodySmall)
                    Text("${totals.xp} XP", style = MaterialTheme.typography.labelSmall)
                }
            }
            Progress(level.progress)
            Text(
                "Noch ${level.xpForNext - level.xpInLevel} XP bis Level ${level.number + 1}",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        SectionCard {
            Text("🪥 Zähneputzen  ${minOf(today.brushCount, today.brushTarget)}/${today.brushTarget}", style = MaterialTheme.typography.titleMedium)
            state.settings.brushTimes.forEachIndexed { slot, time ->
                val done = today.brushCount > slot
                TaskButton(
                    done = done,
                    label = "${slot + 1}. Mal (${time.format(timeFormat)})",
                    onClick = { onBrushCount(if (done) today.brushCount - 1 else today.brushCount + 1) },
                )
            }
        }

        SectionCard {
            if (today.flossRequired) {
                Text("🧵 Zahnseide", style = MaterialTheme.typography.titleMedium)
                TaskButton(done = today.flossed, label = "Zahnseide benutzt", onClick = { onFloss(!today.flossed) })
            } else {
                Text("🧵 Heute ist kein Zahnseide-Tag", style = MaterialTheme.typography.titleMedium)
                TaskButton(done = today.flossed, label = "Trotzdem gemacht (+5 XP Bonus)", onClick = { onFloss(!today.flossed) })
            }
        }

        if (today.isComplete) {
            SectionCard {
                Text("🎉 Heute alles erledigt – perfekter Tag!", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        SectionCard {
            val weekStart = state.today.weekStart()
            val days = (0L..6L).map { weekStart.plusDays(it) }
            val perfect = days.count { history.status(it) == DayStatus.PERFECT }
            Text("Wochen-Challenge: $perfect / 7 perfekte Tage", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { date ->
                    val status = history.status(date)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, GERMAN), style = MaterialTheme.typography.labelSmall)
                        StatusDot(status, isToday = date == state.today)
                    }
                }
            }
        }

        val goals = totals.nextGoals(2)
        if (goals.isNotEmpty()) {
            SectionCard {
                Text("🎯 Nächste Ziele", style = MaterialTheme.typography.titleMedium)
                goals.forEach { AchievementRow(it) }
            }
        }
    }
}

@Composable
private fun TaskButton(done: Boolean, label: String, onClick: () -> Unit) {
    if (done) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = StatusColors.perfect),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("✓  $label") }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("○  $label") }
    }
}

@Composable
fun StatusDot(status: DayStatus, isToday: Boolean) {
    val color = status.color()
    val base = Modifier.padding(top = 4.dp).size(28.dp)
    Box(
        modifier = when {
            color != null -> base.background(color, CircleShape)
            isToday -> base.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            else -> base.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (status == DayStatus.PERFECT) Text("✓", color = MaterialTheme.colorScheme.surface)
    }
}
