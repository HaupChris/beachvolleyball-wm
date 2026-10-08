package de.habits.core

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    // 2026-09-21 is a Monday.
    private val mon = LocalDate.of(2026, 9, 21)

    private fun habit(
        schedule: Schedule = Schedule.DAILY,
        direction: Direction = Direction.BUILD,
        measure: Measure = Measure.CHECK,
        target: Int = if (direction == Direction.BUILD) 1 else 0,
        start: LocalDate = mon,
        challenge: Challenge? = null,
    ) = Habit(1, "Test", "✅", 0, direction, measure, "", listOf(Plan(start, target, schedule)), challenge)

    private fun days(vararg offsets: Long, amount: Int = 1) = offsets.associate { mon.plusDays(it) to amount }

    @Test
    fun `daily build - today pending, past judged`() {
        val h = HabitHistory(habit(), days(0, 2), today = mon.plusDays(3))
        assertEquals(listOf(Outcome.SUCCESS, Outcome.FAIL, Outcome.SUCCESS, Outcome.PENDING), h.periods.map { it.outcome })
    }

    @Test
    fun `only scheduled weekdays are due`() {
        val h = HabitHistory(habit(Schedule.OnDays(setOf(MONDAY, WEDNESDAY, FRIDAY))), days(0, 2), today = mon.plusDays(6))
        assertEquals(3, h.periods.size)
        assertEquals(2, h.streak.current)
        assertTrue(h.streak.atRisk)
    }

    @Test
    fun `a single miss is forgiven, two in a row end the streak`() {
        val h1 = HabitHistory(habit(), days(0, 1, 3, 4), today = mon.plusDays(5))
        assertEquals(4, h1.streak.current)
        val h2 = HabitHistory(habit(), days(0, 1, 4), today = mon.plusDays(5))
        assertEquals(1, h2.streak.current)
        assertEquals(2, h2.streak.longestDays)
    }

    @Test
    fun `count habits need the target amount`() {
        val h = HabitHistory(habit(measure = Measure.COUNT, target = 20), mapOf(mon to 15, mon.plusDays(1) to 25), today = mon.plusDays(2))
        assertEquals(listOf(Outcome.FAIL, Outcome.SUCCESS, Outcome.PENDING), h.periods.map { it.outcome })
        assertEquals(40, h.sum(mon, mon.plusDays(6)))
    }

    @Test
    fun `quit habit - no entry is success, slip fails immediately`() {
        val h = HabitHistory(habit(direction = Direction.QUIT), days(1, 3), today = mon.plusDays(3))
        assertEquals(listOf(Outcome.SUCCESS, Outcome.FAIL, Outcome.SUCCESS, Outcome.FAIL), h.periods.map { it.outcome })
        val clean = HabitHistory(habit(direction = Direction.QUIT), emptyMap(), today = mon)
        assertEquals(Outcome.PENDING, clean.periods.single().outcome)
    }

    @Test
    fun `quit with limit allows up to the limit`() {
        val h = HabitHistory(habit(direction = Direction.QUIT, measure = Measure.COUNT, target = 2), mapOf(mon to 2, mon.plusDays(1) to 3), mon.plusDays(2))
        assertEquals(listOf(Outcome.SUCCESS, Outcome.FAIL, Outcome.PENDING), h.periods.map { it.outcome })
    }

    @Test
    fun `weekly build succeeds once enough days are done, fails at week end`() {
        val h = HabitHistory(habit(Schedule.PerWeek(3)), days(0, 2, 4, 8), today = mon.plusDays(9))
        assertEquals(2, h.periods.size)
        assertEquals(Outcome.SUCCESS, h.periods[0].outcome)
        assertEquals(Outcome.PENDING, h.periods[1].outcome)
        assertEquals(1, h.periods[1].done)

        val failed = HabitHistory(habit(Schedule.PerWeek(3)), days(0), today = mon.plusDays(7))
        assertEquals(Outcome.FAIL, failed.periods[0].outcome)
        assertTrue(failed.streak.weekly)
    }

    @Test
    fun `weekly build in a partial first week asks proportionally less`() {
        val thu = mon.plusDays(3)
        val h = HabitHistory(habit(Schedule.PerWeek(3), start = thu), mapOf(thu to 1, thu.plusDays(1) to 1), today = mon.plusDays(6))
        assertEquals(2, h.periods.single().required) // ceil(3 * 4/7)
        assertEquals(Outcome.SUCCESS, h.periods.single().outcome)
    }

    @Test
    fun `weekly quit fails with too many slip days`() {
        val h = HabitHistory(habit(Schedule.PerWeek(1), direction = Direction.QUIT), days(0, 7, 9), today = mon.plusDays(10))
        assertEquals(listOf(Outcome.SUCCESS, Outcome.FAIL), h.periods.map { it.outcome })
    }

    @Test
    fun `paused days are skipped`() {
        val h = habit().pausedFrom(mon.plusDays(1)).resumedOn(mon.plusDays(3))
        val hist = HabitHistory(h, days(0, 3), today = mon.plusDays(3))
        assertEquals(listOf(mon, mon.plusDays(3)), hist.periods.map { it.start })
        assertEquals(2, hist.streak.current)
        assertTrue(habit().pausedFrom(mon).resumedOn(mon).pauses.isEmpty())
    }

    @Test
    fun `plan changes keep history`() {
        val h = habit(measure = Measure.COUNT, target = 10).withPlan(mon.plusDays(2), 20, Schedule.DAILY)
        val hist = HabitHistory(h, mapOf(mon to 10, mon.plusDays(2) to 10), today = mon.plusDays(3))
        assertEquals(listOf(Outcome.SUCCESS, Outcome.FAIL, Outcome.FAIL, Outcome.PENDING), hist.periods.map { it.outcome })
        assertEquals(2, h.plans.size)
        assertEquals(listOf(5, 20), h.withPlan(mon, 5, Schedule.DAILY).plans.map { it.target })
        assertEquals(mon.minusDays(5), h.withStartDate(mon.minusDays(5)).startDate)
    }

    @Test
    fun `challenge status counts future periods as pending`() {
        val c = Challenge(mon.plusDays(9), thresholdPercent = 80)
        val hist = HabitHistory(habit(challenge = c), days(0, 1, 2, 3), today = mon.plusDays(4))
        val s = hist.challengeStatus!!
        assertEquals(10, s.total)
        assertEquals(4, s.succeeded)
        assertEquals(6, s.pending)
        assertTrue(s.reachable)
        assertFalse(s.passed)
        assertEquals(5, hist.periods.size)

        val done = HabitHistory(habit(challenge = c), days(*LongArray(8) { it.toLong() }), today = mon.plusDays(12))
        assertTrue(done.challengeStatus!!.finished)
        assertTrue(done.challengeStatus!!.passed)
        assertEquals(10, done.periods.size)
    }

    @Test
    fun `consolidated after 66 days with at least 80 percent`() {
        val entries = (0L until 66L).filter { it % 10 != 9L }.associate { mon.plusDays(it) to 1 }
        val hist = HabitHistory(habit(), entries, today = mon.plusDays(70))
        assertEquals(mon.plusDays(65), hist.consolidatedOn)
        assertNull(HabitHistory(habit(), entries, today = mon.plusDays(30)).consolidatedOn)
    }

    @Test
    fun `overview - perfect days, xp and achievements`() {
        val a = HabitHistory(habit(), days(0, 1, 2), today = mon.plusDays(2))
        val b = HabitHistory(habit(direction = Direction.QUIT).copy(id = 2), emptyMap(), today = mon.plusDays(2))
        val o = Overview(listOf(a, b), mon.plusDays(2))
        assertTrue(o.isPerfectDay(mon.plusDays(2))) // quit counts as done today unless slipped
        val t = o.totals
        assertEquals(3, t.perfectDays)
        assertEquals(5, t.successes) // 3 build + 2 past quit days
        // build: 10+11+12, quit: 10+11, perfect days: 3*15
        assertEquals(33 + 21 + 45, t.xp)
        assertTrue(t.achievementProgress().first { it.achievement.id == "perfect_1" }.unlocked)
    }

    @Test
    fun `levels get more expensive`() {
        assertEquals(1, levelFor(99).number)
        assertEquals(2, levelFor(100).number)
        assertEquals(3, levelFor(250).number)
    }
}
