package de.zahnputz.core

import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    private val plan = Plan(brushesPerDay = 2, flossDays = setOf(MONDAY, THURSDAY))

    // 2026-09-21 is a Monday.
    private val start = LocalDate.of(2026, 9, 21)

    private fun rec(date: LocalDate, brush: Int, floss: Boolean = false) =
        plan.emptyRecord(date).copy(brushCount = brush, flossed = floss)

    private fun perfect(date: LocalDate) = rec(date, 2, floss = plan.isFlossDay(date))

    @Test
    fun `status distinguishes perfect, partial, missed, pending and untracked`() {
        val today = start.plusDays(3)
        val h = History(listOf(perfect(start), rec(start.plusDays(1), 1), rec(today, 1)), plan, start, today)
        assertEquals(DayStatus.NOT_TRACKED, h.status(start.minusDays(1)))
        assertEquals(DayStatus.PERFECT, h.status(start))
        assertEquals(DayStatus.PARTIAL, h.status(start.plusDays(1)))
        assertEquals(DayStatus.MISSED, h.status(start.plusDays(2)))
        assertEquals(DayStatus.PENDING, h.status(today))
        assertEquals(DayStatus.NOT_TRACKED, h.status(today.plusDays(1)))
    }

    @Test
    fun `floss day without floss is not complete`() {
        assertFalse(rec(start, 2).isComplete)
        assertTrue(rec(start.plusDays(1), 2).isComplete)
    }

    @Test
    fun `incomplete today does not break the current streak`() {
        val today = start.plusDays(4)
        val records = (0L..3L).map { perfect(start.plusDays(it)) } + rec(today, 1)
        val s = History(records, plan, start, today).streaks()
        assertEquals(4, s.current)
        assertEquals(4, s.longest)
    }

    @Test
    fun `missed day resets streak but keeps longest`() {
        val today = start.plusDays(6)
        val records = listOf(0L, 1L, 2L, 4L, 5L).map { perfect(start.plusDays(it)) }
        val s = History(records, plan, start, today).streaks()
        assertEquals(2, s.current)
        assertEquals(3, s.longest)
    }

    @Test
    fun `floss streak skips non floss days`() {
        val today = start.plusDays(7) // next Monday, not flossed yet
        val records = listOf(rec(start, 2, true), rec(start.plusDays(3), 0, true))
        val s = History(records, plan, start, today).streaks()
        assertEquals(2, s.currentFloss)
        assertEquals(2, s.longestFloss)
    }

    @Test
    fun `week stats cap extra brushing and ignore untracked days`() {
        val today = start.plusDays(2)
        val h = History(listOf(rec(start, 3, true), rec(start.plusDays(1), 1)), plan, start, today)
        val w = h.weekStats(today)
        assertEquals(3, w.trackedDays)
        assertEquals(1, w.perfectDays)
        assertEquals(3, w.brushDone)
        assertEquals(6, w.brushTarget)
        assertEquals(1, w.flossDone)
        assertEquals(1, w.flossRequired)
    }

    @Test
    fun `perfect week and xp are counted`() {
        val today = start.plusDays(6)
        val h = History((0L..6L).map { perfect(start.plusDays(it)) }, plan, start, today)
        val t = h.totals()
        assertEquals(1, t.perfectWeeks)
        assertEquals(7, t.perfectDays)
        assertEquals(14, t.brushes)
        assertEquals(2, t.flosses)
        // 14 brushes * 10 + 2 flosses * 15 + 7 * 20 + streak bonus 0+1+...+6
        assertEquals(140 + 30 + 140 + 21, t.xp)
        assertTrue(t.achievementProgress().first { it.achievement.id == "week_1" }.unlocked)
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
        val records = listOf(0L, 1L, 2L, 3L, 5L).map { perfect(start.plusDays(it)) }
        val t = History(records, plan, start, today).totals()
        val streak7 = t.achievementProgress().first { it.achievement.id == "streak_7" }
        assertFalse(streak7.unlocked)
        assertEquals(1, streak7.value)
        assertTrue(t.achievementProgress().first { it.achievement.id == "streak_3" }.unlocked)
        assertEquals(3, t.nextGoals().size)
    }
}
