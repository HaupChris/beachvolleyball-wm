package de.habits.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.habits.app.data.AppState
import de.habits.core.Direction
import de.habits.core.HabitHistory
import de.habits.core.Lifecycle
import de.habits.core.Measure
import de.habits.core.Outcome
import de.habits.core.levelFor

@Composable
fun TodayScreen(
    state: AppState,
    onSetAmount: (habitId: Long, amount: Int) -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    val today = state.today
    val due = state.histories.filter { it.habit.lifecycle(today) == Lifecycle.ACTIVE && it.periodAt(today) != null }
    // Open items first, settled ones at the bottom.
    val sorted = due.sortedBy { if (isSettled(it)) 1 else 0 }
    val (done, total) = state.overview.dayScore(today)
    val level = levelFor(state.overview.totals.xp)
    var amountFor by remember { mutableStateOf<HabitHistory?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(today.format(dayFormat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Heute", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (total > 0) Text("$done / $total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lv ${level.number}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LinearProgress(level.progress, Modifier.weight(1f))
                Text(level.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.habits.isEmpty()) {
            item { EmptyState(onCreate) }
        } else if (due.isEmpty()) {
            item {
                Text(
                    "Heute ist nichts fällig. Genieß den freien Tag! 🌿",
                    Modifier.padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(sorted, key = { it.habit.id }) { h ->
            TodayRow(
                h,
                onOpen = { onOpen(h.habit.id) },
                onQuick = {
                    if (isSingleCheck(h.habit, today)) onSetAmount(h.habit.id, checkToggleValue(h, today)) else amountFor = h
                },
                onSetAmount = { onSetAmount(h.habit.id, it) },
            )
        }
        item { Spacer(Modifier.height(72.dp)) } // room for the FAB
    }

    amountFor?.let { h ->
        AmountDialog(h, today, onDismiss = { amountFor = null }, onSave = {
            onSetAmount(h.habit.id, it)
            amountFor = null
        })
    }
}

/** Nothing left to do today: BUILD done (or week complete), QUIT is never "settled" before the day ends. */
private fun isSettled(h: HabitHistory): Boolean {
    val p = h.periodAt(h.today) ?: return true
    return when (h.habit.direction) {
        Direction.BUILD -> p.outcome == Outcome.SUCCESS || h.habit.isOk(h.amount(h.today), h.habit.planAt(h.today))
        Direction.QUIT -> p.outcome == Outcome.FAIL
    }
}

@Composable
private fun TodayRow(h: HabitHistory, onOpen: () -> Unit, onQuick: () -> Unit, onSetAmount: (Int) -> Unit) {
    val habit = h.habit
    val today = h.today
    val period = h.periodAt(today)!!
    val amount = h.amount(today)
    val plan = habit.planAt(today)
    val okToday = habit.isOk(amount, plan)
    val color = habitColor(habit.color)

    val subtitle = buildList {
        if (period.weekly) {
            add(if (habit.direction == Direction.BUILD) "${period.done}/${period.required} diese Woche" else "${period.done}/${period.required} Ausnahmen diese Woche")
        }
        if (habit.measure == Measure.COUNT) add("$amount / ${targetText(habit, plan.target)}")
        if (habit.direction == Direction.QUIT && !period.weekly && habit.measure == Measure.CHECK) add(if (okToday) "Bisher clean" else "Ausgerutscht")
        if (h.streak.current > 0) add("🔥 ${streakText(h.streak)}")
        if (h.streak.atRisk && !okToday) add("⚠ nicht zweimal verpassen")
    }.joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HabitAvatar(habit)
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSettled(h)) FontWeight.Normal else FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            habit.direction == Direction.QUIT && habit.measure == Measure.CHECK ->
                OutlinedButton(onClick = onQuick) { Text(if (amount > 0) "Rückgängig" else "Ausrutscher") }
            isSingleCheck(habit, today) -> CheckCircle(done = okToday, color = color, onClick = onQuick)
            habit.measure == Measure.CHECK -> {
                // One tick box per time of day; ticking fills them up from the left, unticking removes the last.
                val labels = checkLabels(habit, plan.target)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until plan.target) {
                        val done = amount > i
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CheckCircle(done = done, color = color, size = 36.dp, onClick = { onSetAmount(if (done) amount - 1 else amount + 1) })
                            labels?.let { Text(it[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            else -> {
                val fraction = if (habit.direction == Direction.BUILD) {
                    if (plan.target == 0) 1f else amount.toFloat() / plan.target
                } else {
                    if (plan.target == 0) (if (amount > 0) 1f else 0f) else amount.toFloat() / plan.target
                }
                val ringColor = if (habit.direction == Direction.QUIT && !okToday) Status.fail else color
                ProgressRing(fraction, ringColor, Modifier.clickable(onClick = onQuick)) {
                    Text(if (habit.direction == Direction.BUILD && okToday) "✓" else "$amount", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CheckCircle(done: Boolean, color: Color, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(if (done) color else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🌱", style = MaterialTheme.typography.displayMedium)
        Text("Noch keine Habits", style = MaterialTheme.typography.titleMedium)
        Text("Leg los mit etwas Kleinem – du kannst Vorlagen nutzen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onCreate) { Text("Ersten Habit anlegen") }
    }
}
