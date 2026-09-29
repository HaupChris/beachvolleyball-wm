package de.zahnputz.app.data

import android.content.Context
import de.zahnputz.core.Plan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Settings(
    /** One reminder time per brushing session; its size is the number of brushings per day. */
    val brushTimes: List<LocalTime>,
    val flossDays: Set<DayOfWeek>,
    val flossTime: LocalTime,
    val remindersEnabled: Boolean,
    val startDate: LocalDate,
) {
    val plan get() = Plan(brushTimes.size, flossDays)

    companion object {
        const val MAX_BRUSHES = 4
        private val extraTimes = listOf(LocalTime.of(7, 30), LocalTime.of(21, 30), LocalTime.of(13, 0), LocalTime.of(17, 30))

        fun defaults(today: LocalDate) = Settings(
            brushTimes = extraTimes.take(2),
            flossDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            flossTime = LocalTime.of(21, 35),
            remindersEnabled = true,
            startDate = today,
        )

        /** Suggested time for a newly added brushing session. */
        fun suggestedTime(existing: List<LocalTime>): LocalTime =
            extraTimes.firstOrNull { it !in existing } ?: LocalTime.NOON
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
            brushTimes = prefs.getString(KEY_BRUSH_TIMES, null)
                ?.split(',')?.filter { it.isNotBlank() }?.map(LocalTime::parse)
                ?.takeIf { it.isNotEmpty() } ?: d.brushTimes,
            flossDays = prefs.getString(KEY_FLOSS_DAYS, null)
                ?.split(',')?.filter { it.isNotBlank() }?.map { DayOfWeek.of(it.toInt()) }?.toSet() ?: d.flossDays,
            flossTime = prefs.getString(KEY_FLOSS_TIME, null)?.let(LocalTime::parse) ?: d.flossTime,
            remindersEnabled = prefs.getBoolean(KEY_REMINDERS, d.remindersEnabled),
            startDate = prefs.getString(KEY_START, null)?.let(LocalDate::parse) ?: today,
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putString(KEY_BRUSH_TIMES, s.brushTimes.joinToString(","))
            .putString(KEY_FLOSS_DAYS, s.flossDays.map { it.value }.sorted().joinToString(","))
            .putString(KEY_FLOSS_TIME, s.flossTime.toString())
            .putBoolean(KEY_REMINDERS, s.remindersEnabled)
            .putString(KEY_START, s.startDate.toString())
            .apply()
    }

    private companion object {
        const val KEY_BRUSH_TIMES = "brush_times"
        const val KEY_FLOSS_DAYS = "floss_days"
        const val KEY_FLOSS_TIME = "floss_time"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_START = "start_date"
    }
}
