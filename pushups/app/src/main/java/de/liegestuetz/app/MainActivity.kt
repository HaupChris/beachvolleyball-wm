package de.liegestuetz.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.liegestuetz.app.data.Repository
import de.liegestuetz.app.reminder.Notifications
import de.liegestuetz.app.reminder.ReminderScheduler
import de.liegestuetz.app.ui.CalendarScreen
import de.liegestuetz.app.ui.GoalsScreen
import de.liegestuetz.app.ui.SettingsScreen
import de.liegestuetz.app.ui.StatsScreen
import de.liegestuetz.app.ui.TodayScreen
import de.liegestuetz.app.ui.LiegestuetzTheme
import de.liegestuetz.core.achievementProgress
import de.liegestuetz.core.levelFor
import de.liegestuetz.core.totals

class MainActivity : ComponentActivity() {
    private val repo by lazy { Repository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.createChannel(this)
        setContent { LiegestuetzTheme { App(repo) } }
    }

    override fun onResume() {
        super.onResume()
        repo.refreshToday()
        // Cheap and idempotent; also picks up a newly granted exact-alarm permission.
        ReminderScheduler.scheduleAll(this)
    }
}

private enum class Tab(val emoji: String, val label: String) {
    TODAY("💪", "Heute"),
    CALENDAR("📅", "Kalender"),
    STATS("📊", "Statistik"),
    GOALS("🏆", "Ziele"),
    SETTINGS("⚙️", "Optionen"),
}

@Composable
private fun App(repo: Repository) {
    val state by repo.state.collectAsStateWithLifecycle()
    val totals = remember(state) { state.history.totals() }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val snackbar = remember { SnackbarHostState() }

    RequestNotificationPermission()
    Celebrations(totals, state.history.day(state.today).isComplete, snackbar)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Text(it.emoji) },
                        label = { Text(it.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    state = state,
                    totals = totals,
                    onAddReps = { repo.addReps(state.today, it) },
                )
                Tab.CALENDAR -> CalendarScreen(state, repo::setReps)
                Tab.STATS -> StatsScreen(state, totals)
                Tab.GOALS -> GoalsScreen(totals)
                Tab.SETTINGS -> SettingsScreen(state.settings, repo::updateSettings)
            }
        }
    }
}

/** Positive feedback when something new is reached (not on first composition). */
@Composable
private fun Celebrations(totals: de.liegestuetz.core.Totals, todayComplete: Boolean, snackbar: SnackbarHostState) {
    val unlocked = totals.achievementProgress().filter { it.unlocked }.map { it.achievement }
    val level = levelFor(totals.xp).number
    var knownIds by remember { mutableStateOf<Set<String>?>(null) }
    var knownLevel by remember { mutableIntStateOf(level) }
    var knownComplete by remember { mutableStateOf(todayComplete) }

    LaunchedEffect(unlocked.map { it.id }.toSet(), level, todayComplete) {
        val ids = unlocked.map { it.id }.toSet()
        val messages = buildList {
            if (todayComplete && !knownComplete) add("🎉 Tagesziel geschafft! Weiter so!")
            if (level > knownLevel) add("⬆️ Level $level erreicht: ${levelFor(totals.xp).title}")
            knownIds?.let { old -> unlocked.filter { it.id !in old }.forEach { add("${it.emoji} Erfolg freigeschaltet: ${it.title}") } }
        }
        knownIds = ids
        knownLevel = level
        knownComplete = todayComplete
        messages.forEach { snackbar.showSnackbar(it) }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
