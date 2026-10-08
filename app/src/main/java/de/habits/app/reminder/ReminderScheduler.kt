package de.habits.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.habits.app.data.Repository
import de.habits.core.Direction
import de.habits.core.Habit
import de.habits.core.HabitHistory
import de.habits.core.Lifecycle
import de.habits.core.Measure
import de.habits.core.Outcome
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Each reminder time of a habit is a daily alarm; whether a notification is actually shown is decided when it
 * fires ([needsReminder]), so schedule changes, pauses and progress never require re-planning.
 */
object ReminderScheduler {
    const val MAX_REMINDERS = 8

    fun scheduleAll(context: Context) {
        Repository.get(context).state.value.habits.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, habit: Habit) {
        cancel(context, habit)
        if (habit.archived || habit.lifecycle(LocalDate.now()) == Lifecycle.FINISHED) return
        habit.reminders.take(MAX_REMINDERS).forEachIndexed { i, time -> scheduleOne(context, habit.id, i, time) }
    }

    fun scheduleOne(context: Context, habitId: Long, index: Int, time: LocalTime) {
        val now = LocalDateTime.now()
        val next = LocalDateTime.of(now.toLocalDate(), time).let { if (it.isAfter(now)) it else it.plusDays(1) }
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, habitId, index)
        if (canUseExactAlarms(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            // Inexact fallback: may be delayed by a few minutes in doze mode.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(context: Context, habit: Habit) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_REMINDERS) am.cancel(pendingIntent(context, habit.id, i))
    }

    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pendingIntent(context: Context, habitId: Long, index: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (habitId * MAX_REMINDERS + index).toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_HABIT, habitId)
                .putExtra(ReminderReceiver.EXTRA_INDEX, index),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Due today and not settled yet: BUILD not done (weekly: week open and nothing done today), QUIT not slipped.
     * For multi-check habits with one reminder per box, reminder [index] only fires while box [index] is open.
     */
    fun needsReminder(h: HabitHistory, index: Int? = null): Boolean {
        val habit = h.habit
        if (habit.lifecycle(h.today) != Lifecycle.ACTIVE) return false
        val period = h.periodAt(h.today) ?: return false
        if (period.outcome != Outcome.PENDING) return false
        if (habit.direction == Direction.QUIT) return true
        val plan = habit.planAt(h.today)
        val amount = h.amount(h.today)
        if (index != null && habit.measure == Measure.CHECK && habit.reminders.size == plan.target) {
            val slot = habit.reminders.sorted().indexOf(habit.reminders[index])
            return amount <= slot
        }
        return !habit.isOk(amount, plan)
    }
}
