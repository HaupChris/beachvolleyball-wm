package de.liegestuetz.core

import java.time.YearMonth

object Xp {
    const val PER_REP = 1
    /** Extra reps earn XP up to this multiple of the daily target – keeps typos/huge entries from inflating XP. */
    const val MAX_TARGET_MULTIPLE = 2
    const val PERFECT_DAY = 20
    const val MAX_STREAK_BONUS = 30
}

data class Totals(
    val xp: Int,
    val reps: Int,
    val bestDay: Int,
    val perfectDays: Int,
    val perfectWeeks: Int,
    val perfectMonths: Int,
    val streaks: Streaks,
)

/**
 * XP rules: every rep (up to twice the daily target), plus a bonus per perfect day
 * that grows with the running streak (capped).
 */
fun History.totals(): Totals {
    var xp = 0
    var reps = 0
    var best = 0
    var perfect = 0
    var run = 0
    for (d in trackedDays()) {
        reps += d.reps
        best = maxOf(best, d.reps)
        xp += minOf(d.reps, d.target * Xp.MAX_TARGET_MULTIPLE) * Xp.PER_REP
        if (d.isComplete) {
            perfect++
            run++
            xp += Xp.PERFECT_DAY + minOf(run - 1, Xp.MAX_STREAK_BONUS)
        } else if (d.date != today) {
            run = 0
        }
    }
    return Totals(
        xp = xp,
        reps = reps,
        bestDay = best,
        perfectDays = perfect,
        perfectWeeks = countPerfectWeeks(),
        perfectMonths = countPerfectMonths(),
        streaks = streaks(),
    )
}

/** Mon–Sun weeks that lie completely in the tracked range and consist only of perfect days. */
private fun History.countPerfectWeeks(): Int {
    var count = 0
    var start = startDate.weekStart().let { if (it.isBefore(startDate)) it.plusWeeks(1) else it }
    while (!start.plusDays(6).isAfter(today)) {
        if ((0L..6L).all { day(start.plusDays(it)).isComplete }) count++
        start = start.plusWeeks(1)
    }
    return count
}

private fun History.countPerfectMonths(): Int {
    var count = 0
    var month = YearMonth.from(startDate).let { if (it.atDay(1).isBefore(startDate)) it.plusMonths(1) else it }
    while (!month.atEndOfMonth().isAfter(today)) {
        val stats = monthStats(month)
        if (stats.perfectDays == stats.trackedDays) count++
        month = month.plusMonths(1)
    }
    return count
}

data class Level(
    val number: Int,
    val title: String,
    /** XP collected within the current level. */
    val xpInLevel: Int,
    /** XP needed to reach the next level from the start of this one. */
    val xpForNext: Int,
) {
    val progress get() = xpInLevel.toFloat() / xpForNext
}

private val levelTitles = listOf(
    "Couch-Kartoffel",
    "Aufwärmer",
    "Bodenkontakt",
    "Armbeuger",
    "Brustpresser",
    "Trizeps-Titan",
    "Liegestütz-Profi",
    "Eiserner Rumpf",
    "Schwerkraft-Bezwinger",
    "Legende der Matte",
)

/** Level L → L+1 costs 100 + 50·(L−1) XP, so early levels come quickly. */
fun levelFor(xp: Int): Level {
    var level = 1
    var remaining = xp
    while (true) {
        val cost = 100 + 50 * (level - 1)
        if (remaining < cost) {
            return Level(level, levelTitles[minOf(level, levelTitles.size) - 1], remaining, cost)
        }
        remaining -= cost
        level++
    }
}

enum class Metric { STREAK, REPS, BEST_DAY, PERFECT_DAYS, PERFECT_WEEKS, PERFECT_MONTHS }

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val metric: Metric,
    val target: Int,
)

data class AchievementProgress(val achievement: Achievement, val value: Int, val unlocked: Boolean) {
    val progress get() = if (unlocked) 1f else minOf(1f, value.toFloat() / achievement.target)
}

val achievements: List<Achievement> = buildList {
    add(Achievement("perfect_1", "🌱", "Guter Start", "Erster perfekter Tag", Metric.PERFECT_DAYS, 1))
    listOf(3 to "🔥", 7 to "📆", 14 to "💪", 30 to "🏅", 60 to "🥈", 100 to "🥇", 365 to "👑").forEach { (n, e) ->
        add(Achievement("streak_$n", e, "$n-Tage-Streak", "$n perfekte Tage in Folge", Metric.STREAK, n))
    }
    listOf(100, 500, 1000, 2500, 5000, 10000).forEach { n ->
        add(Achievement("reps_$n", "🏋️", "$n Liegestütze", "Insgesamt $n Liegestütze", Metric.REPS, n))
    }
    listOf(25, 50, 100).forEach { n ->
        add(Achievement("best_day_$n", "⚡", "$n an einem Tag", "$n Liegestütze an einem einzigen Tag", Metric.BEST_DAY, n))
    }
    add(Achievement("perfect_50", "✨", "Durchhalter", "50 perfekte Tage", Metric.PERFECT_DAYS, 50))
    add(Achievement("week_1", "⭐", "Perfekte Woche", "Eine ganze Woche (Mo–So) perfekt", Metric.PERFECT_WEEKS, 1))
    add(Achievement("week_4", "🌟", "Vier perfekte Wochen", "Vier Wochen (Mo–So) perfekt", Metric.PERFECT_WEEKS, 4))
    add(Achievement("month_1", "🏆", "Perfekter Monat", "Einen ganzen Kalendermonat perfekt", Metric.PERFECT_MONTHS, 1))
}

/** Best value ever reached – decides whether an achievement is unlocked. */
private fun Totals.bestValue(metric: Metric) = when (metric) {
    Metric.STREAK -> streaks.longest
    Metric.REPS -> reps
    Metric.BEST_DAY -> bestDay
    Metric.PERFECT_DAYS -> perfectDays
    Metric.PERFECT_WEEKS -> perfectWeeks
    Metric.PERFECT_MONTHS -> perfectMonths
}

/** Progress towards a locked achievement: streaks have to be reached in one go, so the current streak counts. */
private fun Totals.currentValue(metric: Metric) = when (metric) {
    Metric.STREAK -> streaks.current
    else -> bestValue(metric)
}

fun Totals.achievementProgress(): List<AchievementProgress> = achievements.map {
    val unlocked = bestValue(it.metric) >= it.target
    AchievementProgress(it, if (unlocked) it.target else currentValue(it.metric), unlocked)
}

/** The locked achievements closest to being unlocked – the "next goals" to work towards. */
fun Totals.nextGoals(count: Int = 3): List<AchievementProgress> =
    achievementProgress().filterNot { it.unlocked }.sortedByDescending { it.progress }.take(count)
