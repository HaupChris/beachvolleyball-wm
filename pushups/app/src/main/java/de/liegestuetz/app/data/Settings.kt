package de.liegestuetz.app.data

import android.content.Context
import de.liegestuetz.core.Plan
import java.time.LocalDate
import java.time.LocalTime

data class Settings(
    val dailyTarget: Int,
    /** Reminders fire at these times as long as today's target isn't reached yet. */
    val reminderTimes: List<LocalTime>,
    val remindersEnabled: Boolean,
    val startDate: LocalDate,
) {
    val plan get() = Plan(dailyTarget)

    companion object {
        const val MIN_TARGET = 1
        const val MAX_TARGET = 500
        const val MAX_REMINDERS = 3
        private val extraTimes = listOf(LocalTime.of(7, 45), LocalTime.of(19, 0), LocalTime.of(12, 30))

        fun defaults(today: LocalDate) = Settings(
            dailyTarget = 12,
            reminderTimes = extraTimes.take(2).sorted(),
            remindersEnabled = true,
            startDate = today,
        )

        /** Suggested time for a newly added reminder. */
        fun suggestedTime(existing: List<LocalTime>): LocalTime =
            extraTimes.firstOrNull { it !in existing } ?: LocalTime.of(17, 0)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(today: LocalDate): Settings {
        if (!prefs.contains(KEY_START)) {
            return Settings.defaults(today).also(::save)
        }
        val d = Settings.defaults(today)
        return Settings(
            dailyTarget = prefs.getInt(KEY_TARGET, d.dailyTarget),
            reminderTimes = prefs.getString(KEY_REMINDER_TIMES, null)
                ?.split(',')?.filter { it.isNotBlank() }?.map(LocalTime::parse)
                ?: d.reminderTimes,
            remindersEnabled = prefs.getBoolean(KEY_REMINDERS, d.remindersEnabled),
            startDate = prefs.getString(KEY_START, null)?.let(LocalDate::parse) ?: today,
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putInt(KEY_TARGET, s.dailyTarget)
            .putString(KEY_REMINDER_TIMES, s.reminderTimes.joinToString(","))
            .putBoolean(KEY_REMINDERS, s.remindersEnabled)
            .putString(KEY_START, s.startDate.toString())
            .apply()
    }

    private companion object {
        const val KEY_TARGET = "daily_target"
        const val KEY_REMINDER_TIMES = "reminder_times"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_START = "start_date"
    }
}
