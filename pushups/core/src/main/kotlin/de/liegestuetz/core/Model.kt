package de.liegestuetz.core

import java.time.LocalDate

/** What the user wants to achieve per day. The target is snapshotted into [DayRecord]s so later plan changes don't rewrite history. */
data class Plan(val dailyTarget: Int) {
    fun emptyRecord(date: LocalDate) = DayRecord(date = date, reps = 0, target = dailyTarget)
}

data class DayRecord(
    val date: LocalDate,
    val reps: Int,
    val target: Int,
) {
    val isComplete get() = reps >= target
    val hasActivity get() = reps > 0
    val remaining get() = maxOf(0, target - reps)
}

enum class DayStatus {
    /** Daily target reached. */
    PERFECT,
    /** Past day with some reps, but below the target. */
    PARTIAL,
    /** Past day without any reps. */
    MISSED,
    /** Today, not (yet) complete. */
    PENDING,
    /** Before tracking started or in the future. */
    NOT_TRACKED,
}

/**
 * Read-only view on all tracked days. Days in the tracked range without a stored record
 * fall back to an empty record derived from the current [plan].
 */
class History(
    records: Collection<DayRecord>,
    val plan: Plan,
    startDate: LocalDate,
    val today: LocalDate,
) {
    private val byDate = records.associateBy { it.date }

    /** Tracking starts at the configured start date or the earliest stored record, whichever is first. */
    val startDate: LocalDate = (records.map { it.date } + startDate).min()

    fun isTracked(date: LocalDate) = !date.isBefore(startDate) && !date.isAfter(today)

    fun day(date: LocalDate): DayRecord = byDate[date] ?: plan.emptyRecord(date)

    fun status(date: LocalDate): DayStatus {
        if (!isTracked(date)) return DayStatus.NOT_TRACKED
        val d = day(date)
        return when {
            d.isComplete -> DayStatus.PERFECT
            date == today -> DayStatus.PENDING
            d.hasActivity -> DayStatus.PARTIAL
            else -> DayStatus.MISSED
        }
    }

    /** All tracked days in chronological order. */
    fun trackedDays(): Sequence<DayRecord> =
        generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map(::day)
}
