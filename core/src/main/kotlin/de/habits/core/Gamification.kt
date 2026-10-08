package de.habits.core

import java.time.LocalDate

/** All habits together: daily overview, XP, levels, achievements. */
class Overview(val histories: List<HabitHistory>, val today: LocalDate) {

    /** Done and due day-scheduled habits on [date]. Weekly habits have no single due day and are left out. */
    fun dayScore(date: LocalDate): Pair<Int, Int> {
        var done = 0
        var due = 0
        for (h in histories) {
            val p = h.periodAt(date)?.takeIf { !it.weekly } ?: continue
            due++
            if (p.outcome == Outcome.SUCCESS || (date == today && h.habit.direction == Direction.QUIT && p.outcome == Outcome.PENDING)) done++
        }
        return done to due
    }

    /** All due day-habits done (a quit habit counts as done today unless it already slipped). */
    fun isPerfectDay(date: LocalDate) = dayScore(date).let { (done, due) -> due > 0 && done == due }

    val totals: Totals by lazy { computeTotals() }

    private fun computeTotals(): Totals {
        var xp = 0
        var successes = 0
        var longest = 0
        var currentBest = 0
        var challenges = 0
        var consolidated = 0
        for (h in histories) {
            val counter = StreakCounter()
            for (p in h.periods) {
                counter.add(p)
                if (p.outcome == Outcome.SUCCESS) {
                    successes++
                    val bonus = minOf(counter.run - 1, Xp.MAX_STREAK_BONUS)
                    xp += if (p.weekly) Xp.WEEK + bonus * Xp.WEEK_STREAK_FACTOR else Xp.DAY + bonus
                }
            }
            longest = maxOf(longest, counter.longestDays)
            if (h.habit.isRunning(today)) currentBest = maxOf(currentBest, counter.runDays)
            h.challengeStatus?.let { if (it.finished && it.passed) { challenges++; xp += Xp.CHALLENGE } }
            h.consolidatedOn?.let { consolidated++; xp += Xp.CONSOLIDATED }
        }
        val start = histories.minOfOrNull { it.habit.startDate }
        var perfect = 0
        if (start != null) {
            var d: LocalDate = start
            while (!d.isAfter(today)) {
                if (isPerfectDay(d)) perfect++
                d = d.plusDays(1)
            }
        }
        xp += perfect * Xp.PERFECT_DAY
        return Totals(xp, successes, perfect, longest, currentBest, challenges, consolidated)
    }
}

object Xp {
    const val DAY = 10
    const val WEEK = 40
    const val WEEK_STREAK_FACTOR = 4
    const val MAX_STREAK_BONUS = 10
    const val PERFECT_DAY = 15
    const val CHALLENGE = 150
    const val CONSOLIDATED = 100
}

data class Totals(
    val xp: Int,
    val successes: Int,
    val perfectDays: Int,
    val longestStreakDays: Int,
    val currentBestStreakDays: Int,
    val challengesPassed: Int,
    val consolidated: Int,
)

data class Level(val number: Int, val title: String, val xpInLevel: Int, val xpForNext: Int) {
    val progress get() = xpInLevel.toFloat() / xpForNext
}

private val levelTitles = listOf(
    "Neuanfang", "Neugierig", "Dranbleiber", "Routinier", "Gewohnheitstier",
    "Disziplin-Profi", "Meister der Routine", "Willensstark", "Unaufhaltsam", "Legende",
)

/** Level L → L+1 costs 100 + 50·(L−1) XP, so early levels come quickly. */
fun levelFor(xp: Int): Level {
    var level = 1
    var remaining = xp
    while (true) {
        val cost = 100 + 50 * (level - 1)
        if (remaining < cost) return Level(level, levelTitles[minOf(level, levelTitles.size) - 1], remaining, cost)
        remaining -= cost
        level++
    }
}

enum class Metric { SUCCESSES, PERFECT_DAYS, STREAK_DAYS, CHALLENGES, CONSOLIDATED }

data class Achievement(val id: String, val emoji: String, val title: String, val description: String, val metric: Metric, val target: Int)

data class AchievementProgress(val achievement: Achievement, val value: Int, val unlocked: Boolean) {
    val progress get() = if (unlocked) 1f else minOf(1f, value.toFloat() / achievement.target)
}

val achievements: List<Achievement> = buildList {
    add(Achievement("success_1", "🌱", "Erster Schritt", "Zum ersten Mal ein Ziel erreicht", Metric.SUCCESSES, 1))
    listOf(10, 50, 100, 250, 500, 1000).forEach {
        add(Achievement("success_$it", "✅", "$it× geschafft", "$it erfüllte Tage bzw. Wochen über alle Habits", Metric.SUCCESSES, it))
    }
    listOf(1 to "☀️", 7 to "⭐", 30 to "🌟", 100 to "💎").forEach { (n, e) ->
        add(Achievement("perfect_$n", e, if (n == 1) "Perfekter Tag" else "$n perfekte Tage", "An $n Tagen alle fälligen Habits erledigt", Metric.PERFECT_DAYS, n))
    }
    listOf(7 to "🔥", 21 to "📆", 30 to "🏅", 66 to "🧠", 100 to "🥇", 365 to "👑").forEach { (n, e) ->
        add(Achievement("streak_$n", e, "$n-Tage-Serie", "Eine Serie von $n Tagen bei einem Habit (Wochenziele zählen 7 Tage)", Metric.STREAK_DAYS, n))
    }
    listOf(1, 3, 10).forEach {
        add(Achievement("challenge_$it", "🏆", if (it == 1) "Challenge gemeistert" else "$it Challenges", "$it Challenge(s) erfolgreich abgeschlossen", Metric.CHALLENGES, it))
    }
    listOf(1, 3, 5).forEach {
        add(Achievement("consolidated_$it", "🧱", if (it == 1) "Gefestigt" else "$it gefestigte Habits", "$it Habit(s) $CONSOLIDATION_DAYS Tage mit ≥ 80 % gehalten", Metric.CONSOLIDATED, it))
    }
}

private fun Totals.best(m: Metric) = when (m) {
    Metric.SUCCESSES -> successes
    Metric.PERFECT_DAYS -> perfectDays
    Metric.STREAK_DAYS -> longestStreakDays
    Metric.CHALLENGES -> challengesPassed
    Metric.CONSOLIDATED -> consolidated
}

/** Streaks must be reached in one go, so locked streak goals show the current best streak. */
private fun Totals.current(m: Metric) = if (m == Metric.STREAK_DAYS) currentBestStreakDays else best(m)

fun Totals.achievementProgress(): List<AchievementProgress> = achievements.map {
    val unlocked = best(it.metric) >= it.target
    AchievementProgress(it, if (unlocked) it.target else current(it.metric), unlocked)
}

fun Totals.nextGoals(count: Int = 3): List<AchievementProgress> =
    achievementProgress().filterNot { it.unlocked }.sortedByDescending { it.progress }.take(count)

/** Per-habit streak milestones (in days). */
val streakMilestones = listOf(7, 21, 66, 100, 365)
