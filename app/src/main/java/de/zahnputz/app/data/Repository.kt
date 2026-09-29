package de.zahnputz.app.data

import android.content.Context
import de.zahnputz.app.reminder.Notifications
import de.zahnputz.app.reminder.ReminderScheduler
import de.zahnputz.core.DayRecord
import de.zahnputz.core.History
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

    fun setBrushCount(date: LocalDate, count: Int) =
        save(day(date).copy(brushCount = count.coerceIn(0, 9)))

    fun setFlossed(date: LocalDate, flossed: Boolean) = save(day(date).copy(flossed = flossed))

    fun updateSettings(settings: Settings) {
        settingsStore.save(settings)
        _state.update { it.copy(settings = settings) }
        // Today follows the new plan; past days keep the targets they were recorded with.
        val today = _state.value.today
        _state.value.records[today]?.let { r ->
            val plan = settings.plan
            save(r.copy(brushTarget = plan.brushesPerDay, flossRequired = plan.isFlossDay(today)))
        }
        ReminderScheduler.scheduleAll(appContext)
    }

    private fun save(record: DayRecord) {
        db.upsert(record)
        _state.update { it.copy(records = it.records + (record.date to record)) }
        if (record.date == _state.value.today) {
            // Dismiss reminders that are no longer relevant.
            for (slot in 0 until record.brushCount) Notifications.cancel(appContext, slot)
            if (record.flossed) Notifications.cancel(appContext, ReminderScheduler.FLOSS)
        }
    }

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
