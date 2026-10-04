package de.liegestuetz.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Missed days in a row that end a streak – a single slip is forgiven ("never miss twice"). */
const val MISSES_TO_BREAK = 2

data class Streaks(
    val current: Int,
    val longest: Int,
    /** The last finished day was missed: missing today as well ends the current streak. */
    val atRisk: Boolean,
)

/**
 * Counts perfect days towards the running streak. A single incomplete day doesn't count, but doesn't end
 * the streak either; [MISSES_TO_BREAK] incomplete days in a row do. An incomplete *today* is still pending.
 */
internal class StreakCounter {
    var run = 0
        private set
    var longest = 0
        private set
    var misses = 0
        private set

    fun add(day: DayRecord, isToday: Boolean) {
        if (day.isComplete) {
            run++
            misses = 0
        } else if (!isToday) {
            misses++
            if (misses >= MISSES_TO_BREAK) run = 0
        }
        longest = maxOf(longest, run)
    }
}

fun History.streaks(): Streaks {
    val counter = StreakCounter()
    for (d in trackedDays()) counter.add(d, isToday = d.date == today)
    return Streaks(current = counter.run, longest = counter.longest, atRisk = counter.run > 0 && counter.misses > 0)
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
