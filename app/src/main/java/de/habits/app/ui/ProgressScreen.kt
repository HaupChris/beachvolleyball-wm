package de.habits.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.habits.app.data.AppState
import de.habits.core.AchievementProgress
import de.habits.core.HabitHistory
import de.habits.core.achievementProgress
import de.habits.core.levelFor
import de.habits.core.nextGoals
import de.habits.core.weekStart
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

private val shortMonth = DateTimeFormatter.ofPattern("MMM", GERMAN)
private const val HEATMAP_WEEKS = 17

@Composable
fun ProgressScreen(state: AppState) {
    val today = state.today
    var runningOnly by rememberSaveable { mutableStateOf(true) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var showAllAchievements by rememberSaveable { mutableStateOf(false) }
    val totals = state.overview.totals
    val level = levelFor(totals.xp)
    val histories = state.histories.filter { !runningOnly || it.habit.isRunning(today) }
    val primary = MaterialTheme.colorScheme.primary

    // Periods: the current one last.
    val weeks = (7 downTo 0).map { today.minusWeeks(it.toLong()).weekStart() }
    val months = (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
    fun rateOf(h: HabitHistory, i: Int): Float? = if (monthly) h.monthRate(months[i]) else h.weekRate(weeks[i])
    val count = if (monthly) months.size else weeks.size
    /** Average over habits, so a daily and a weekly habit weigh the same. */
    fun avg(i: Int): Float? = histories.mapNotNull { rateOf(it, i) }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fortschritt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Level ${level.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(level.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${totals.xp} XP", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgress(level.progress)
        Text("Noch ${level.xpForNext - level.xpInLevel} XP bis Level ${level.number + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Stat("${totals.perfectDays}", "perfekte Tage")
            Stat("${totals.longestStreakDays} T", "längste Serie")
            Stat("${totals.challengesPassed}", "Challenges")
            Stat("${totals.consolidated}", "gefestigt")
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = runningOnly, onClick = { runningOnly = true }, label = { Text("Laufend") })
            FilterChip(selected = !runningOnly, onClick = { runningOnly = false }, label = { Text("Alle") })
            Spacer(Modifier.weight(1f))
            FilterChip(selected = !monthly, onClick = { monthly = false }, label = { Text("Wochen") })
            FilterChip(selected = monthly, onClick = { monthly = true }, label = { Text("Monate") })
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(percent(avg(count - 1)), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                (if (monthly) "diesen Monat" else "diese Woche") + " · Ø über ${histories.size} Habits",
                Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RateBars(
            values = (0 until count).map(::avg),
            labels = if (monthly) months.map { it.format(shortMonth) } else weeks.map { "KW${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" },
            color = primary,
        )

        SectionTitle("Pro Habit")
        if (histories.isEmpty()) Text("Keine Habits in dieser Auswahl.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Weakest first – that's where attention helps most.
        histories.sortedBy { rateOf(it, count - 1) ?: 2f }.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                HabitAvatar(h.habit, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(h.habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text("🔥 ${streakText(h.streak)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MiniBars((0 until count).map { rateOf(h, it) }, habitColor(h.habit.color))
                Text(percent(rateOf(h, count - 1)), Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
            }
        }

        SectionTitle("Aktivität (alle täglichen Habits)")
        Heatmap(state, today)

        HorizontalDivider()
        val all = totals.achievementProgress()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Erfolge · ${all.count { it.unlocked }}/${all.size}", Modifier.weight(1f))
            TextButton(onClick = { showAllAchievements = !showAllAchievements }) { Text(if (showAllAchievements) "Weniger" else "Alle") }
        }
        val shown = if (showAllAchievements) all.sortedByDescending { it.unlocked } else totals.nextGoals(3)
        shown.forEach { AchievementRow(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AchievementRow(p: AchievementProgress) {
    val a = p.achievement
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(if (p.unlocked) a.emoji else "🔒", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (p.unlocked) FontWeight.SemiBold else FontWeight.Normal)
            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!p.unlocked) {
                LinearProgress(p.progress)
                Text("${p.value} / ${a.target}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Columns = weeks, rows = Mon..Sun; intensity = share of due day-habits done. */
@Composable
private fun Heatmap(state: AppState, today: LocalDate) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val firstWeek = today.weekStart().minusWeeks(HEATMAP_WEEKS - 1L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (w in 0 until HEATMAP_WEEKS) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0 until 7) {
                    val date = firstWeek.plusWeeks(w.toLong()).plusDays(d.toLong())
                    val (done, due) = if (date.isAfter(today)) 0 to 0 else state.overview.dayScore(date)
                    val color = when {
                        due == 0 -> empty.copy(alpha = if (date.isAfter(today)) 0.2f else 0.5f)
                        done == 0 -> empty
                        else -> primary.copy(alpha = 0.25f + 0.75f * done / due)
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(color, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("weniger", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0f, 0.33f, 0.66f, 1f).forEach { f ->
            Box(Modifier.size(10.dp).background(if (f == 0f) empty else primary.copy(alpha = 0.25f + 0.75f * f), RoundedCornerShape(2.dp)))
        }
        Text("mehr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
