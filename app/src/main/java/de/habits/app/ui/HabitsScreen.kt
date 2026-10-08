package de.habits.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.habits.app.data.AppState
import de.habits.app.reminder.ReminderScheduler
import de.habits.core.HabitHistory
import de.habits.core.Lifecycle

@Composable
fun HabitsScreen(state: AppState, onOpen: (Long) -> Unit) {
    val today = state.today
    val groups = state.histories.groupBy { it.habit.lifecycle(today) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Habits", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            ExactAlarmHint(state)
        }
        section("Laufend", groups[Lifecycle.ACTIVE], onOpen)
        section("Pausiert", groups[Lifecycle.PAUSED], onOpen)
        section("Abgeschlossen", groups[Lifecycle.FINISHED], onOpen)
        val archived = groups[Lifecycle.ARCHIVED].orEmpty()
        if (archived.isNotEmpty()) {
            item {
                TextButton(onClick = { showArchived = !showArchived }) {
                    Text(if (showArchived) "Archiv ausblenden" else "Archiv anzeigen (${archived.size})")
                }
            }
            if (showArchived) section(null, archived, onOpen)
        }
        if (state.habits.isEmpty()) {
            item { Text("Tippe auf +, um deinen ersten Habit anzulegen.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String?, list: List<HabitHistory>?, onOpen: (Long) -> Unit) {
    if (list.isNullOrEmpty()) return
    if (title != null) item { SectionTitle(title, Modifier.padding(top = 16.dp, bottom = 4.dp)) }
    items(list, key = { it.habit.id }) { HabitRow(it, onClick = { onOpen(it.habit.id) }) }
}

@Composable
private fun HabitRow(h: HabitHistory, onClick: () -> Unit) {
    val habit = h.habit
    val details = buildList {
        add(scheduleText(habit))
        targetText(habit).takeIf { it.isNotEmpty() }?.let(::add)
        h.challengeStatus?.let { add(if (it.finished) (if (it.passed) "Challenge geschafft 🏆" else "Challenge beendet") else "Challenge bis ${habit.endDate!!.format(shortDate)}") }
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HabitAvatar(habit)
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (h.streak.current > 0) Text("🔥 ${h.streak.current}", style = MaterialTheme.typography.labelLarge)
    }
}

/** Asks for the exact-alarm permission (Android 12+) if any habit has reminders. */
@Composable
private fun ExactAlarmHint(state: AppState) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || state.habits.none { it.reminders.isNotEmpty() }) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ReminderScheduler.canUseExactAlarms(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = ReminderScheduler.canUseExactAlarms(context)
        onPauseOrDispose { }
    }
    if (allowed) return
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Erinnerungen können sich um einige Minuten verspäten.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Pünktliche Erinnerungen erlauben") }
        }
    }
}
