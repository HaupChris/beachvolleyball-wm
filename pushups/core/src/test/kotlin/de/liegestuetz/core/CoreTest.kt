package de.liegestuetz.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    private val plan = Plan(dailyTarget = 12)

    // 2026-09-21 is a Monday.
    private val start = LocalDate.of(2026, 9, 21)

    private fun rec(date: LocalDate, reps: Int) = plan.emptyRecord(date).copy(reps = reps)

    private fun perfect(date: LocalDate) = rec(date, 12)

    @Test
    fun `status distinguishes perfect, partial, missed, pending and untracked`() {
        val today = start.plusDays(3)
        val h = History(listOf(perfect(start), rec(start.plusDays(1), 5), rec(today, 5)), plan, start, today)
        assertEquals(DayStatus.NOT_TRACKED, h.status(start.minusDays(1)))
        assertEquals(DayStatus.PERFECT, h.status(start))
        assertEquals(DayStatus.PARTIAL, h.status(start.plusDays(1)))
        assertEquals(DayStatus.MISSED, h.status(start.plusDays(2)))
        assertEquals(DayStatus.PENDING, h.status(today))
        assertEquals(DayStatus.NOT_TRACKED, h.status(today.plusDays(1)))
    }

    @Test
    fun `day is complete once the target is reached`() {
        assertFalse(rec(start, 11).isComplete)
        assertTrue(rec(start, 12).isComplete)
        assertTrue(rec(start, 30).isComplete)
        assertEquals(7, rec(start, 5).remaining)
        assertEquals(0, rec(start, 30).remaining)
    }

    @Test
    fun `stored target wins over current plan`() {
        val old = DayRecord(start, reps = 10, target = 10)
        val h = History(listOf(old), Plan(dailyTarget = 20), start, start.plusDays(1))
        assertEquals(DayStatus.PERFECT, h.status(start))
        assertEquals(20, h.day(start.plusDays(1)).target)
    }

    @Test
    fun `incomplete today does not break the current streak`() {
        val today = start.plusDays(4)
        val records = (0L..3L).map { perfect(start.plusDays(it)) } + rec(today, 3)
        val s = History(records, plan, start, today).streaks()
        assertEquals(4, s.current)
        assertEquals(4, s.longest)
    }

    @Test
    fun `single missed day is forgiven but does not count`() {
        val today = start.plusDays(6)
        val records = listOf(0L, 1L, 2L, 4L, 5L).map { perfect(start.plusDays(it)) }
        val s = History(records, plan, start, today).streaks()
        assertEquals(5, s.current)
        assertEquals(5, s.longest)
        assertFalse(s.atRisk)
    }

    @Test
    fun `two missed days in a row end the streak but keep longest`() {
        val today = start.plusDays(7)
        val records = listOf(0L, 1L, 2L, 5L, 6L).map { perfect(start.plusDays(it)) }
        val s = History(records, plan, start, today).streaks()
        assertEquals(2, s.current)
        assertEquals(3, s.longest)
    }

    @Test
    fun `streak is at risk after a missed day until today is done`() {
        val today = start.plusDays(3)
        val records = listOf(0L, 1L).map { perfect(start.plusDays(it)) }
        assertTrue(History(records, plan, start, today).streaks().atRisk)
        val done = History(records + perfect(today), plan, start, today).streaks()
        assertFalse(done.atRisk)
        assertEquals(3, done.current)
    }

    @Test
    fun `partial day counts as a miss`() {
        val today = start.plusDays(4)
        val records = listOf(perfect(start), rec(start.plusDays(1), 11), rec(start.plusDays(2), 3))
        assertEquals(0, History(records, plan, start, today).streaks().current)
    }

    @Test
    fun `week stats count all reps but cap the completion rate`() {
        val today = start.plusDays(2)
        val h = History(listOf(rec(start, 20), rec(start.plusDays(1), 6)), plan, start, today)
        val w = h.weekStats(today)
        assertEquals(3, w.trackedDays)
        assertEquals(1, w.perfectDays)
        assertEquals(26, w.reps)
        assertEquals(18, w.repsTowardsTarget)
        assertEquals(36, w.repsTarget)
    }

    @Test
    fun `perfect week and xp are counted`() {
        val today = start.plusDays(6)
        val h = History((0L..6L).map { perfect(start.plusDays(it)) }, plan, start, today)
        val t = h.totals()
        assertEquals(1, t.perfectWeeks)
        assertEquals(7, t.perfectDays)
        assertEquals(84, t.reps)
        // 84 reps * 1 + 7 * 20 + streak bonus 0+1+...+6
        assertEquals(84 + 140 + 21, t.xp)
        assertTrue(t.achievementProgress().first { it.achievement.id == "week_1" }.unlocked)
    }

    @Test
    fun `streak bonus continues after a forgiven miss`() {
        val today = start.plusDays(2)
        val h = History(listOf(perfect(start), perfect(today)), plan, start, today)
        // 24 reps + 2 perfect days * 20 + streak bonus 0 + 1
        assertEquals(24 + 40 + 1, h.totals().xp)
    }

    @Test
    fun `xp for extra reps is capped at twice the target`() {
        val h = History(listOf(rec(start, 500)), plan, start, start)
        val t = h.totals()
        assertEquals(500, t.reps)
        assertEquals(500, t.bestDay)
        assertEquals(24 + 20, t.xp)
        assertTrue(t.achievementProgress().first { it.achievement.id == "best_day_100" }.unlocked)
    }

    @Test
    fun `stored records before start date extend the tracked range`() {
        val h = History(listOf(perfect(start.minusDays(2))), plan, start, start)
        assertEquals(start.minusDays(2), h.startDate)
        assertEquals(DayStatus.MISSED, h.status(start.minusDays(1)))
    }

    @Test
    fun `levels get more expensive`() {
        assertEquals(1, levelFor(0).number)
        assertEquals(2, levelFor(100).number)
        assertEquals(2, levelFor(249).number)
        assertEquals(3, levelFor(250).number)
        assertEquals(50, levelFor(150).xpInLevel)
    }

    @Test
    fun `next goals use current streak for locked streak achievements`() {
        val today = start.plusDays(5)
        // Days 3 and 4 missed: the streak restarts on day 5.
        val records = listOf(0L, 1L, 2L, 5L).map { perfect(start.plusDays(it)) }
        val t = History(records, plan, start, today).totals()
        val streak7 = t.achievementProgress().first { it.achievement.id == "streak_7" }
        assertFalse(streak7.unlocked)
        assertEquals(1, streak7.value)
        assertTrue(t.achievementProgress().first { it.achievement.id == "streak_3" }.unlocked)
        assertEquals(3, t.nextGoals().size)
    }
}
