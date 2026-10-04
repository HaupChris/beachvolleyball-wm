package de.liegestuetz.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.liegestuetz.app.data.Repository
import de.liegestuetz.app.data.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One daily alarm per configured reminder time ([slot] = index into [Settings.reminderTimes]). */
object ReminderScheduler {

    fun scheduleAll(context: Context) {
        val settings = Repository.get(context).state.value.settings
        for (slot in 0 until Settings.MAX_REMINDERS) cancel(context, slot)
        if (!settings.remindersEnabled) return
        settings.reminderTimes.indices.forEach { schedule(context, settings, it) }
    }

    /** Schedules the next occurrence of one reminder (called again after each firing). */
    fun schedule(context: Context, settings: Settings, slot: Int) {
        val next = nextTrigger(settings, slot, LocalDateTime.now()) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, slot)
        if (canUseExactAlarms(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            // Inexact fallback: may be delayed by a few minutes in doze mode.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    internal fun nextTrigger(settings: Settings, slot: Int, now: LocalDateTime): LocalDateTime? {
        val time = settings.reminderTimes.getOrNull(slot) ?: return null
        val todayAt = LocalDateTime.of(now.toLocalDate(), time)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    private fun cancel(context: Context, slot: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, slot))
    }

    private fun pendingIntent(context: Context, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            slot,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_SLOT, slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Whether reminders are obsolete because today's target is already reached. */
    fun isDone(context: Context, date: LocalDate): Boolean = Repository.get(context).day(date).isComplete
}
