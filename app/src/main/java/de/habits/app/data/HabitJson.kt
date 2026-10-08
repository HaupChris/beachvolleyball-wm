package de.habits.app.data

import de.habits.core.Challenge
import de.habits.core.Direction
import de.habits.core.Habit
import de.habits.core.Measure
import de.habits.core.Pause
import de.habits.core.Plan
import de.habits.core.Schedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Habits are stored as one JSON document per row – flexible, and the data is tiny. */
object HabitJson {
    fun encode(h: Habit): String = JSONObject().apply {
        put("name", h.name)
        put("emoji", h.emoji)
        put("color", h.color)
        put("direction", h.direction.name)
        put("measure", h.measure.name)
        put("unit", h.unit)
        put("plans", JSONArray(h.plans.map { p ->
            JSONObject().apply {
                put("from", p.from.toString())
                put("target", p.target)
                when (val s = p.schedule) {
                    is Schedule.OnDays -> put("days", JSONArray(s.days.map { it.value }.sorted()))
                    is Schedule.PerWeek -> put("perWeek", s.times)
                }
            }
        }))
        h.challenge?.let { put("challenge", JSONObject().put("end", it.endDate.toString()).put("threshold", it.thresholdPercent)) }
        put("pauses", JSONArray(h.pauses.map { p -> JSONObject().put("from", p.from.toString()).put("to", p.to?.toString() ?: JSONObject.NULL) }))
        put("archived", h.archived)
        put("reminders", JSONArray(h.reminders.map { it.toString() }))
    }.toString()

    fun decode(id: Long, json: String): Habit {
        val o = JSONObject(json)
        return Habit(
            id = id,
            name = o.getString("name"),
            emoji = o.optString("emoji", "✅"),
            color = o.optInt("color", 0),
            direction = Direction.valueOf(o.optString("direction", Direction.BUILD.name)),
            measure = Measure.valueOf(o.optString("measure", Measure.CHECK.name)),
            unit = o.optString("unit", ""),
            plans = o.getJSONArray("plans").objects().map { p ->
                Plan(
                    from = LocalDate.parse(p.getString("from")),
                    target = p.getInt("target"),
                    schedule = if (p.has("perWeek")) Schedule.PerWeek(p.getInt("perWeek"))
                    else Schedule.OnDays(p.getJSONArray("days").ints().map { DayOfWeek.of(it) }.toSet()),
                )
            },
            challenge = o.optJSONObject("challenge")?.let { Challenge(LocalDate.parse(it.getString("end")), it.getInt("threshold")) },
            pauses = o.optJSONArray("pauses")?.objects()?.map { p ->
                Pause(LocalDate.parse(p.getString("from")), if (p.isNull("to")) null else LocalDate.parse(p.getString("to")))
            } ?: emptyList(),
            archived = o.optBoolean("archived", false),
            reminders = o.optJSONArray("reminders")?.let { a -> (0 until a.length()).map { LocalTime.parse(a.getString(it)) } } ?: emptyList(),
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
}
