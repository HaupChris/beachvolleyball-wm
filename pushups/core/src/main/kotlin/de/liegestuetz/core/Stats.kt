package de.liegestuetz.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class Streaks(val current: Int, val longest: Int)

/** A streak counts consecutive perfect days. An incomplete *today* doesn't break the streak yet. */
fun History.streaks(): Streaks {
    var longest = 0
    var run = 0
    for (d in trackedDays()) {
        if (d.isComplete) {
            run++
        } else if (d.date != today) {
            run = 0
        }
        longest = maxOf(longest, run)
    }
    return Streaks(current = run, longest = longest)
}

data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val trackedDays: Int,
    val perfectDays: Int,
    /** All reps, including those beyond the daily target. */
    val reps: Int,
    /** Reps counted towards the target (capped per day), for the completion rate. */
    val repsTowardsTarget: Int,
    val repsTarget: Int,
) {
    val perfectRate get() = if (trackedDays == 0) 0f else perfectDays.toFloat() / trackedDays
    val repsRate get() = if (repsTarget == 0) 0f else repsTowardsTarget.toFloat() / repsTarget
}

/** Stats for [from]..[to] (inclusive), restricted to tracked days. */
fun History.stats(from: LocalDate, to: LocalDate): PeriodStats {
    var tracked = 0
    var perfect = 0
    var reps = 0
    var towards = 0
    var target = 0
    var date = maxOf(from, startDate)
    val end = minOf(to, today)
    while (!date.isAfter(end)) {
        val d = day(date)
        tracked++
        if (d.isComplete) perfect++
        reps += d.reps
        towards += minOf(d.reps, d.target)
        target += d.target
        date = date.plusDays(1)
    }
    return PeriodStats(from, to, tracked, perfect, reps, towards, target)
}

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun History.weekStats(anyDayInWeek: LocalDate): PeriodStats {
    val start = anyDayInWeek.weekStart()
    return stats(start, start.plusDays(6))
}

fun History.monthStats(month: YearMonth): PeriodStats = stats(month.atDay(1), month.atEndOfMonth())

/** The last [count] weeks, oldest first, ending with the current week. */
fun History.recentWeeks(count: Int): List<PeriodStats> =
    (count - 1 downTo 0).map { weekStats(today.minusWeeks(it.toLong())) }

/** The last [count] months, oldest first, ending with the current month. */
fun History.recentMonths(count: Int): List<PeriodStats> =
    (count - 1 downTo 0).map { monthStats(YearMonth.from(today).minusMonths(it.toLong())) }
