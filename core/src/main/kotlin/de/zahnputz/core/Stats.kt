package de.zahnputz.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class Streaks(
    val current: Int,
    val longest: Int,
    val currentFloss: Int,
    val longestFloss: Int,
)

/**
 * A streak counts consecutive perfect days. An incomplete *today* doesn't break the streak yet.
 * The floss streak counts consecutive floss days (non-floss days are skipped) on which the user flossed.
 */
fun History.streaks(): Streaks {
    var longest = 0
    var run = 0
    var longestFloss = 0
    var flossRun = 0
    for (d in trackedDays()) {
        val pendingToday = d.date == today
        if (d.isComplete) {
            run++
        } else if (!pendingToday) {
            run = 0
        }
        longest = maxOf(longest, run)

        if (d.flossRequired) {
            if (d.flossed) flossRun++ else if (!pendingToday) flossRun = 0
        }
        longestFloss = maxOf(longestFloss, flossRun)
    }
    return Streaks(current = run, longest = longest, currentFloss = flossRun, longestFloss = longestFloss)
}

data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val trackedDays: Int,
    val perfectDays: Int,
    val brushDone: Int,
    val brushTarget: Int,
    val flossDone: Int,
    val flossRequired: Int,
) {
    val perfectRate get() = if (trackedDays == 0) 0f else perfectDays.toFloat() / trackedDays
    val brushRate get() = if (brushTarget == 0) 0f else brushDone.toFloat() / brushTarget
    val flossRate get() = if (flossRequired == 0) 0f else flossDone.toFloat() / flossRequired
}

/** Stats for [from]..[to] (inclusive), restricted to tracked days. Brushing beyond the target isn't counted. */
fun History.stats(from: LocalDate, to: LocalDate): PeriodStats {
    var tracked = 0
    var perfect = 0
    var brushDone = 0
    var brushTarget = 0
    var flossDone = 0
    var flossRequired = 0
    var date = maxOf(from, startDate)
    val end = minOf(to, today)
    while (!date.isAfter(end)) {
        val d = day(date)
        tracked++
        if (d.isComplete) perfect++
        brushDone += minOf(d.brushCount, d.brushTarget)
        brushTarget += d.brushTarget
        if (d.flossRequired) {
            flossRequired++
            if (d.flossed) flossDone++
        }
        date = date.plusDays(1)
    }
    return PeriodStats(from, to, tracked, perfect, brushDone, brushTarget, flossDone, flossRequired)
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
