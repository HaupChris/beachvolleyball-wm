package de.habits.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.habits.app.data.AppState
import de.habits.app.data.Repository
import de.habits.app.reminder.Notifications
import de.habits.app.reminder.ReminderScheduler
import de.habits.app.ui.HabitDetailScreen
import de.habits.app.ui.HabitEditor
import de.habits.app.ui.HabitsScreen
import de.habits.app.ui.HabitsTheme
import de.habits.app.ui.ProgressScreen
import de.habits.app.ui.TodayScreen
import de.habits.core.achievementProgress
import de.habits.core.levelFor

class MainActivity : ComponentActivity() {
    private val repo by lazy { Repository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.createChannel(this)
        setContent { HabitsTheme { App(repo) } }
    }

    override fun onResume() {
        super.onResume()
        repo.refreshToday()
        // Cheap and idempotent; also picks up a newly granted exact-alarm permission.
        ReminderScheduler.scheduleAll(this)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Heute", Icons.Filled.Home),
    HABITS("Habits", Icons.AutoMirrored.Filled.List),
    PROGRESS("Fortschritt", Icons.Filled.Star),
}

/** -1 = no editor, 0 = new habit, otherwise the id of the habit being edited. */
private const val NO_EDITOR = -1L
private const val NEW_HABIT = 0L

@Composable
private fun App(repo: Repository) {
    val state by repo.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorId by rememberSaveable { mutableStateOf(NO_EDITOR) }
    val snackbar = remember { SnackbarHostState() }

    RequestNotificationPermission()
    Celebrations(state, snackbar)

    if (editorId != NO_EDITOR) {
        BackHandler { editorId = NO_EDITOR }
        HabitEditor(
            initial = repo.habit(editorId),
            today = state.today,
            onSave = { habit ->
                val id = repo.save(habit)
                if (editorId == NEW_HABIT) detailId = id
                editorId = NO_EDITOR
            },
            onCancel = { editorId = NO_EDITOR },
        )
        return
    }

    val detail = detailId?.let(state::history)
    if (detail != null) {
        BackHandler { detailId = null }
        val habit = detail.habit
        HabitDetailScreen(
            history = detail,
            onBack = { detailId = null },
            onEdit = { editorId = habit.id },
            onSetAmount = { date, amount -> repo.setAmount(habit.id, date, amount) },
            onPauseToggle = { repo.save(if (habit.isPaused(state.today)) habit.resumedOn(state.today) else habit.pausedFrom(state.today)) },
            onArchiveToggle = { repo.save(habit.copy(archived = !habit.archived)) },
            onDelete = {
                repo.delete(habit.id)
                detailId = null
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab != Tab.PROGRESS) FloatingActionButton(onClick = { editorId = NEW_HABIT }) { Icon(Icons.Filled.Add, "Neuer Habit") }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(it.icon, null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    state = state,
                    onSetAmount = { id, amount -> repo.setAmount(id, state.today, amount) },
                    onOpen = { detailId = it },
                    onCreate = { editorId = NEW_HABIT },
                )
                Tab.HABITS -> HabitsScreen(state, onOpen = { detailId = it })
                Tab.PROGRESS -> ProgressScreen(state)
            }
        }
    }
}

/** Positive feedback when something new is reached (not on first composition). */
@Composable
private fun Celebrations(state: AppState, snackbar: SnackbarHostState) {
    val totals = state.overview.totals
    val unlocked = totals.achievementProgress().filter { it.unlocked }.map { it.achievement }
    val level = levelFor(totals.xp)
    val perfectToday = state.overview.isPerfectDay(state.today)
    var knownIds by remember { mutableStateOf<Set<String>?>(null) }
    var knownLevel by remember { mutableIntStateOf(level.number) }
    var knownPerfect by remember { mutableStateOf(perfectToday) }

    LaunchedEffect(unlocked.map { it.id }.toSet(), level.number, perfectToday) {
        val messages = buildList {
            if (perfectToday && !knownPerfect) add("🎉 Alles für heute erledigt!")
            if (level.number > knownLevel) add("⬆️ Level ${level.number}: ${level.title}")
            knownIds?.let { old -> unlocked.filter { it.id !in old }.forEach { add("${it.emoji} Erfolg: ${it.title}") } }
        }
        knownIds = unlocked.map { it.id }.toSet()
        knownLevel = level.number
        knownPerfect = perfectToday
        messages.forEach { snackbar.showSnackbar(it) }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
