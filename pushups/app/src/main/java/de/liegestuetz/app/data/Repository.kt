package de.liegestuetz.app.data

import android.content.Context
import de.liegestuetz.app.reminder.Notifications
import de.liegestuetz.app.reminder.ReminderScheduler
import de.liegestuetz.core.DayRecord
import de.liegestuetz.core.History
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class AppState(
    val settings: Settings,
    val records: Map<LocalDate, DayRecord>,
    val today: LocalDate,
) {
    val history get() = History(records.values, settings.plan, settings.startDate, today)
}

/**
 * Process-wide single source of truth, shared by the UI and the notification receivers.
 * All data is tiny (one row per day), so it is kept in memory and written through to SQLite.
 */
class Repository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = DayDatabase(appContext)
    private val settingsStore = SettingsStore(appContext)

    private val _state = MutableStateFlow(
        LocalDate.now().let { today ->
            AppState(settingsStore.load(today), db.loadAll().associateBy { it.date }, today)
        }
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun refreshToday() {
        val today = LocalDate.now()
        if (today != _state.value.today) _state.update { it.copy(today = today) }
    }

    fun day(date: LocalDate): DayRecord = _state.value.history.day(date)

    fun setReps(date: LocalDate, reps: Int) = save(day(date).copy(reps = reps.coerceIn(0, MAX_REPS)))

    fun addReps(date: LocalDate, delta: Int) = setReps(date, day(date).reps + delta)

    fun updateSettings(settings: Settings) {
        settingsStore.save(settings)
        _state.update { it.copy(settings = settings) }
        // Today follows the new plan; past days keep the target they were recorded with.
        val today = _state.value.today
        _state.value.records[today]?.let { save(it.copy(target = settings.dailyTarget)) }
        ReminderScheduler.scheduleAll(appContext)
    }

    private fun save(record: DayRecord) {
        db.upsert(record)
        _state.update { it.copy(records = it.records + (record.date to record)) }
        // Dismiss a reminder that is no longer relevant.
        if (record.date == _state.value.today && record.isComplete) Notifications.cancel(appContext)
    }

    companion object {
        const val MAX_REPS = 9999

        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
