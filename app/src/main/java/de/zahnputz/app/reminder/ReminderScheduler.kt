package de.zahnputz.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.zahnputz.app.data.Repository
import de.zahnputz.app.data.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A reminder is either brushing session [slot] (0-based) or flossing ([slot] == [FLOSS]). */
object ReminderScheduler {
    const val FLOSS = -1

    fun scheduleAll(context: Context) {
        val settings = Repository.get(context).state.value.settings
        for (slot in 0 until Settings.MAX_BRUSHES) cancel(context, slot)
        cancel(context, FLOSS)
        if (!settings.remindersEnabled) return
        settings.brushTimes.indices.forEach { schedule(context, settings, it) }
        if (settings.flossDays.isNotEmpty()) schedule(context, settings, FLOSS)
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
        val time: LocalTime = if (slot == FLOSS) settings.flossTime else settings.brushTimes.getOrNull(slot) ?: return null
        val candidates = generateSequence(now.toLocalDate()) { it.plusDays(1) }.take(8)
        return candidates
            .filter { slot != FLOSS || settings.plan.isFlossDay(it) }
            .map { LocalDateTime.of(it, time) }
            .firstOrNull { it.isAfter(now) }
    }

    private fun cancel(context: Context, slot: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, slot))
    }

    private fun pendingIntent(context: Context, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(slot),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_SLOT, slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun requestCode(slot: Int) = if (slot == FLOSS) 100 else slot

    /** Whether the reminder is obsolete because the task is already done today. */
    fun isDone(context: Context, slot: Int, date: LocalDate): Boolean {
        val day = Repository.get(context).day(date)
        return if (slot == FLOSS) day.flossed else day.brushCount > slot
    }
}
