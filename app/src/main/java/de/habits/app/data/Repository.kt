package de.habits.app.data

import android.content.Context
import de.habits.app.reminder.Notifications
import de.habits.app.reminder.ReminderScheduler
import de.habits.core.Habit
import de.habits.core.HabitHistory
import de.habits.core.Overview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class AppState(
    val habits: List<Habit>,
    val entries: Map<Long, Map<LocalDate, Int>>,
    val today: LocalDate,
) {
    val histories: List<HabitHistory> by lazy { habits.map { HabitHistory(it, entries[it.id].orEmpty(), today) } }
    val overview: Overview by lazy { Overview(histories, today) }

    fun history(id: Long) = histories.firstOrNull { it.habit.id == id }
}

/**
 * Process-wide single source of truth, shared by the UI and the notification receivers.
 * The data is small, so it is kept in memory and written through to SQLite.
 */
class Repository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = HabitDatabase(appContext)

    private val _state = MutableStateFlow(AppState(db.loadHabits(), db.loadEntries(), LocalDate.now()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun refreshToday() {
        val today = LocalDate.now()
        if (today != _state.value.today) _state.update { it.copy(today = today) }
    }

    fun habit(id: Long) = _state.value.habits.firstOrNull { it.id == id }

    fun amount(id: Long, date: LocalDate) = _state.value.entries[id]?.get(date) ?: 0

    /** Inserts or updates a habit and re-plans its reminders. Returns the id. */
    fun save(habit: Habit): Long {
        val id = db.saveHabit(habit)
        val saved = habit.copy(id = id)
        _state.update { s ->
            val habits = if (s.habits.any { it.id == id }) s.habits.map { if (it.id == id) saved else it } else s.habits + saved
            s.copy(habits = habits)
        }
        ReminderScheduler.schedule(appContext, saved)
        return id
    }

    fun delete(id: Long) {
        habit(id)?.let { ReminderScheduler.cancel(appContext, it) }
        db.deleteHabit(id)
        _state.update { s -> s.copy(habits = s.habits.filterNot { it.id == id }, entries = s.entries - id) }
    }

    fun setAmount(id: Long, date: LocalDate, amount: Int) {
        val value = amount.coerceIn(0, 99_999)
        db.setAmount(id, date, value)
        _state.update { s ->
            val forHabit = s.entries[id].orEmpty().let { if (value == 0) it - date else it + (date to value) }
            s.copy(entries = s.entries + (id to forHabit))
        }
        if (date == _state.value.today) Notifications.cancelIfSettled(appContext, id)
    }

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
