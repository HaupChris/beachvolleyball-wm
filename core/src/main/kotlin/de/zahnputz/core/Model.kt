package de.zahnputz.core

import java.time.DayOfWeek
import java.time.LocalDate

/** What the user wants to achieve per day. Targets are snapshotted into [DayRecord]s so later plan changes don't rewrite history. */
data class Plan(
    val brushesPerDay: Int,
    val flossDays: Set<DayOfWeek>,
) {
    fun isFlossDay(date: LocalDate) = date.dayOfWeek in flossDays

    fun emptyRecord(date: LocalDate) = DayRecord(
        date = date,
        brushCount = 0,
        brushTarget = brushesPerDay,
        flossed = false,
        flossRequired = isFlossDay(date),
    )
}

data class DayRecord(
    val date: LocalDate,
    val brushCount: Int,
    val brushTarget: Int,
    val flossed: Boolean,
    val flossRequired: Boolean,
) {
    val isComplete get() = brushCount >= brushTarget && (!flossRequired || flossed)
    val hasActivity get() = brushCount > 0 || flossed
}

enum class DayStatus {
    /** All goals of the day reached. */
    PERFECT,
    /** Past day with some, but not all goals reached. */
    PARTIAL,
    /** Past day without any activity. */
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
