package de.liegestuetz.app.ui

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.liegestuetz.app.data.AppState
import de.liegestuetz.core.DayStatus
import de.liegestuetz.core.Totals
import de.liegestuetz.core.levelFor
import de.liegestuetz.core.nextGoals
import de.liegestuetz.core.weekStart
import java.time.format.TextStyle

@Composable
fun TodayScreen(
    state: AppState,
    totals: Totals,
    onAddReps: (Int) -> Unit,
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
            Text("💪 Liegestütze heute", style = MaterialTheme.typography.titleMedium)
            Text(
                "${today.reps} / ${today.target}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (today.isComplete) StatusColors.perfect else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Progress(today.reps.toFloat() / today.target, color = if (today.isComplete) StatusColors.perfect else MaterialTheme.colorScheme.primary)
            if (!today.isComplete) {
                Button(
                    onClick = { onAddReps(today.remaining) },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusColors.perfect),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (today.reps == 0) "✓  ${today.target} gemacht" else "✓  Restliche ${today.remaining} gemacht") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10).forEach { n ->
                    FilledTonalButton(onClick = { onAddReps(n) }, modifier = Modifier.weight(1f)) { Text("+$n") }
                }
            }
            if (today.reps > 0) {
                TextButton(onClick = { onAddReps(-1) }) { Text("Vertippt? −1") }
            }
        }

        if (today.isComplete) {
            SectionCard {
                Text(
                    if (today.reps > today.target) "🎉 Ziel geschafft – sogar ${today.reps - today.target} extra!" else "🎉 Tagesziel geschafft!",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
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
