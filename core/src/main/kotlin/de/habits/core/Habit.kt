package de.habits.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Build a habit up (do it) or quit it (don't do it / stay below a limit). */
enum class Direction { BUILD, QUIT }

/** Tick off once, or count an amount (reps, minutes, glasses …). */
enum class Measure { CHECK, COUNT }

sealed interface Schedule {
    /** Due on fixed weekdays; every due day is judged on its own. */
    data class OnDays(val days: Set<DayOfWeek>) : Schedule

    /**
     * [times] days per Mon–Sun week, freely distributed. For BUILD the week succeeds with [times] successful days,
     * for QUIT it fails with more than [times] slip days.
     */
    data class PerWeek(val times: Int) : Schedule

    companion object {
        val DAILY = OnDays(DayOfWeek.values().toSet())
    }
}

/**
 * What a habit asks for, valid from [from] until the next plan. Changes create a new plan, so history keeps
 * being judged by the rules that applied back then.
 *
 * [target]: BUILD → amount needed per day (1 for CHECK). QUIT → maximum allowed per day (0 for CHECK).
 */
data class Plan(val from: LocalDate, val target: Int, val schedule: Schedule)

data class Pause(val from: LocalDate, val to: LocalDate?) {
    operator fun contains(date: LocalDate) = !date.isBefore(from) && (to == null || !date.isAfter(to))
}

/** A habit with an end date. It's passed if at least [thresholdPercent] of its periods succeeded. */
data class Challenge(val endDate: LocalDate, val thresholdPercent: Int)

enum class Lifecycle { ACTIVE, PAUSED, FINISHED, ARCHIVED }

data class Habit(
    val id: Long,
    val name: String,
    val emoji: String,
    val color: Int,
    val direction: Direction,
    val measure: Measure,
    /** Unit label for COUNT habits, e.g. "Wdh." or "min". */
    val unit: String,
    /** Sorted by [Plan.from]; the first plan starts the habit. */
    val plans: List<Plan>,
    val challenge: Challenge? = null,
    val pauses: List<Pause> = emptyList(),
    val archived: Boolean = false,
    val reminders: List<LocalTime> = emptyList(),
) {
    init {
        require(plans.isNotEmpty()) { "A habit needs a plan" }
    }

    val startDate: LocalDate get() = plans.first().from
    val endDate: LocalDate? get() = challenge?.endDate
    val currentPlan: Plan get() = plans.last()

    fun planAt(date: LocalDate): Plan = plans.lastOrNull { !it.from.isAfter(date) } ?: plans.first()

    fun isPaused(date: LocalDate) = pauses.any { date in it }

    /** Within the habit's lifetime (ignoring pauses). */
    fun inLifetime(date: LocalDate) = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    fun isActive(date: LocalDate) = inLifetime(date) && !isPaused(date)

    fun lifecycle(today: LocalDate): Lifecycle = when {
        archived -> Lifecycle.ARCHIVED
        endDate != null && today.isAfter(endDate) -> Lifecycle.FINISHED
        isPaused(today) -> Lifecycle.PAUSED
        else -> Lifecycle.ACTIVE
    }

    /** Running = neither archived nor finished (paused habits are still running). */
    fun isRunning(today: LocalDate) = lifecycle(today).let { it == Lifecycle.ACTIVE || it == Lifecycle.PAUSED }

    /** Whether a day's amount meets the plan: BUILD reaches the target, QUIT stays within the limit. */
    fun isOk(amount: Int, plan: Plan) = when (direction) {
        Direction.BUILD -> amount >= plan.target
        Direction.QUIT -> amount <= plan.target
    }

    /** New target/schedule effective from [date]; earlier days keep their plan. */
    fun withPlan(date: LocalDate, target: Int, schedule: Schedule): Habit {
        val current = planAt(date)
        if (current.target == target && current.schedule == schedule) return this
        if (!date.isAfter(startDate)) return copy(plans = listOf(Plan(startDate, target, schedule)) + plans.drop(1).filter { it.from.isAfter(date) })
        return copy(plans = plans.filter { it.from.isBefore(date) } + Plan(date, target, schedule))
    }

    /** Moves the start (e.g. to enter older data); plans before the new start collapse into the first one. */
    fun withStartDate(date: LocalDate): Habit =
        copy(plans = listOf(planAt(date).copy(from = date)) + plans.filter { it.from.isAfter(date) })

    fun pausedFrom(date: LocalDate): Habit = if (isPaused(date)) this else copy(pauses = pauses + Pause(date, null))

    /** Ends an open pause; a pause started today is removed entirely. */
    fun resumedOn(today: LocalDate): Habit = copy(
        pauses = pauses.mapNotNull {
            when {
                it.to != null -> it
                !it.from.isBefore(today) -> null
                else -> it.copy(to = today.minusDays(1))
            }
        }
    )
}
