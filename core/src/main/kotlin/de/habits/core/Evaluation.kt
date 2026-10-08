package de.habits.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

enum class Outcome { SUCCESS, FAIL, PENDING }

/**
 * The unit a habit is judged in: a single due day (OnDays) or a Mon–Sun week (PerWeek).
 * [done]/[required]: BUILD → amount (day) or successful days (week) vs. what's needed;
 * QUIT → amount (day) or slip days (week) vs. what's allowed.
 */
data class Period(
    val start: LocalDate,
    val end: LocalDate,
    val weekly: Boolean,
    val outcome: Outcome,
    val done: Int,
    val required: Int,
) {
    /** Streak weight in days, so daily and weekly streaks are comparable. */
    val days get() = if (weekly) 7 else 1
}

/** Missed periods in a row that end a streak – a single slip is forgiven ("never miss twice"). */
const val MISSES_TO_BREAK = 2

/** 66 days is the median time to automaticity in Lally et al. (2009) – a rough guide, not a law. */
const val CONSOLIDATION_DAYS = 66L
const val CONSOLIDATION_RATE = 0.8f

class StreakCounter {
    /** Successful periods in the running streak. */
    var run = 0
        private set
    /** The running streak in days (weekly periods count 7). */
    var runDays = 0
        private set
    var longestDays = 0
        private set
    var misses = 0
        private set

    fun add(p: Period) {
        when (p.outcome) {
            Outcome.SUCCESS -> {
                run++
                runDays += p.days
                misses = 0
            }
            Outcome.FAIL -> {
                misses++
                if (misses >= MISSES_TO_BREAK) {
                    run = 0
                    runDays = 0
                }
            }
            Outcome.PENDING -> Unit
        }
        longestDays = maxOf(longestDays, runDays)
    }
}

data class Streak(
    /** Successful periods (days or weeks, see [weekly]). */
    val current: Int,
    val currentDays: Int,
    val longestDays: Int,
    val weekly: Boolean,
    /** The last decided period failed: failing the next one too ends the streak. */
    val atRisk: Boolean,
)

data class ChallengeStatus(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val pending: Int,
    val thresholdPercent: Int,
    val finished: Boolean,
) {
    val rate get() = if (total == 0) 0f else succeeded.toFloat() / total
    val passed get() = total > 0 && succeeded * 100 >= thresholdPercent * total
    /** The threshold can still be reached if all remaining periods succeed. */
    val reachable get() = total > 0 && (succeeded + pending) * 100 >= thresholdPercent * total
}

/** Evaluation of one habit against its entries (amount per day). */
class HabitHistory(
    val habit: Habit,
    entries: Map<LocalDate, Int>,
    val today: LocalDate,
) {
    private val entries = entries.filterValues { it > 0 }

    fun amount(date: LocalDate) = entries[date] ?: 0

    /** All periods from the start up to today (or the challenge end, whichever comes first). */
    val periods: List<Period> by lazy { periodsUntil(minOf(today, habit.endDate ?: today)) }

    /** For challenges: all periods until the end date, future ones pending. */
    private val allChallengePeriods: List<Period> by lazy { habit.endDate?.let(::periodsUntil) ?: periods }

    private fun periodsUntil(last: LocalDate): List<Period> {
        val result = mutableListOf<Period>()
        var weekStart = habit.startDate.weekStart()
        while (!weekStart.isAfter(last)) {
            val firstDay = maxOf(weekStart, habit.startDate)
            val plan = habit.planAt(firstDay)
            if (plan.schedule is Schedule.PerWeek) {
                weekPeriod(weekStart, plan, plan.schedule)?.let(result::add)
            } else {
                // A switch to a weekly schedule mid-week takes effect the next week.
                for (i in 0L..6L) {
                    val date = weekStart.plusDays(i)
                    if (date.isAfter(last)) break
                    dayPeriod(date)?.let(result::add)
                }
            }
            weekStart = weekStart.plusWeeks(1)
        }
        return result
    }

    private fun dayPeriod(date: LocalDate): Period? {
        if (!habit.isActive(date)) return null
        val plan = habit.planAt(date)
        val schedule = plan.schedule as? Schedule.OnDays ?: return null
        if (date.dayOfWeek !in schedule.days) return null
        val amount = amount(date)
        val ok = habit.isOk(amount, plan)
        val outcome = when {
            date.isBefore(today) -> if (ok) Outcome.SUCCESS else Outcome.FAIL
            date == today -> when (habit.direction) {
                Direction.BUILD -> if (ok) Outcome.SUCCESS else Outcome.PENDING
                Direction.QUIT -> if (ok) Outcome.PENDING else Outcome.FAIL
            }
            else -> Outcome.PENDING
        }
        return Period(date, date, weekly = false, outcome = outcome, done = amount, required = plan.target)
    }

    private fun weekPeriod(weekStart: LocalDate, plan: Plan, schedule: Schedule.PerWeek): Period? {
        val days = (0L..6L).map { weekStart.plusDays(it) }.filter { habit.isActive(it) }
        if (days.isEmpty()) return null
        // Partial weeks (start, end, pauses) ask for – or allow – proportionally less.
        val share = schedule.times * days.size / 7.0
        val required = when (habit.direction) {
            Direction.BUILD -> ceil(share).toInt().coerceIn(1, maxOf(1, schedule.times))
            Direction.QUIT -> Math.round(share).toInt()
        }
        val elapsed = days.filter { !it.isAfter(today) }
        val over = days.last().isBefore(today)
        return when (habit.direction) {
            Direction.BUILD -> {
                val hits = elapsed.count { habit.isOk(amount(it), plan) }
                val outcome = when {
                    hits >= required -> Outcome.SUCCESS
                    over -> Outcome.FAIL
                    else -> Outcome.PENDING
                }
                Period(days.first(), days.last(), weekly = true, outcome = outcome, done = hits, required = required)
            }
            Direction.QUIT -> {
                val slips = elapsed.count { !habit.isOk(amount(it), plan) }
                val outcome = when {
                    slips > required -> Outcome.FAIL
                    over -> Outcome.SUCCESS
                    else -> Outcome.PENDING
                }
                Period(days.first(), days.last(), weekly = true, outcome = outcome, done = slips, required = required)
            }
        }
    }

    /** The period containing [date], if the habit is due/judged then. */
    fun periodAt(date: LocalDate): Period? = periodIndex[date]

    private val periodIndex: Map<LocalDate, Period> by lazy {
        buildMap {
            for (p in allChallengePeriods) {
                var d = p.start
                while (!d.isAfter(p.end)) {
                    put(d, p)
                    d = d.plusDays(1)
                }
            }
        }
    }

    val streak: Streak by lazy {
        val counter = StreakCounter()
        periods.forEach(counter::add)
        Streak(
            current = counter.run,
            currentDays = counter.runDays,
            longestDays = counter.longestDays,
            weekly = habit.currentPlan.schedule is Schedule.PerWeek,
            atRisk = counter.run > 0 && counter.misses > 0,
        )
    }

    /** Share of decided periods that succeeded among those ending in [from]..[to]; null if none were decided. */
    fun rate(from: LocalDate, to: LocalDate): Float? {
        val decided = periods.filter { !it.end.isBefore(from) && !it.end.isAfter(to) && it.outcome != Outcome.PENDING }
        if (decided.isEmpty()) return null
        return decided.count { it.outcome == Outcome.SUCCESS }.toFloat() / decided.size
    }

    fun weekRate(anyDay: LocalDate) = anyDay.weekStart().let { rate(it, it.plusDays(6)) }

    fun monthRate(month: YearMonth) = rate(month.atDay(1), month.atEndOfMonth())

    /** Total amount (COUNT habits) in a date range. */
    fun sum(from: LocalDate, to: LocalDate) = entries.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values.sum()

    val challengeStatus: ChallengeStatus? by lazy {
        val c = habit.challenge ?: return@lazy null
        val all = allChallengePeriods
        ChallengeStatus(
            total = all.size,
            succeeded = all.count { it.outcome == Outcome.SUCCESS },
            failed = all.count { it.outcome == Outcome.FAIL },
            pending = all.count { it.outcome == Outcome.PENDING },
            thresholdPercent = c.thresholdPercent,
            finished = today.isAfter(c.endDate),
        )
    }

    /**
     * The first day on which the habit counted as consolidated: at least [CONSOLIDATION_DAYS] old and
     * ≥ [CONSOLIDATION_RATE] of the periods in the trailing window succeeded.
     */
    val consolidatedOn: LocalDate? by lazy {
        val decided = periods.filter { it.outcome != Outcome.PENDING }
        decided.firstOrNull { p ->
            val windowStart = p.end.minusDays(CONSOLIDATION_DAYS - 1)
            if (windowStart.isBefore(habit.startDate)) return@firstOrNull false
            val window = decided.filter { !it.end.isBefore(windowStart) && !it.end.isAfter(p.end) }
            window.count { it.outcome == Outcome.SUCCESS } >= CONSOLIDATION_RATE * window.size
        }?.end
    }

    /** Days since start, capped at [CONSOLIDATION_DAYS] – progress towards consolidation. */
    val ageDays: Long get() = (ChronoUnit.DAYS.between(habit.startDate, today) + 1).coerceIn(0, CONSOLIDATION_DAYS)
}
