package de.zahnputz.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.zahnputz.app.data.Repository
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(EXTRA_SLOT, Int.MIN_VALUE)
        if (slot == Int.MIN_VALUE) return
        val repo = Repository.get(context)
        repo.refreshToday()
        val today = LocalDate.now()
        when (intent.action) {
            ACTION_REMIND -> {
                if (!ReminderScheduler.isDone(context, slot, today)) Notifications.showReminder(context, slot)
                ReminderScheduler.schedule(context, repo.state.value.settings, slot)
            }
            ACTION_DONE -> {
                if (slot == ReminderScheduler.FLOSS) {
                    repo.setFlossed(today, true)
                } else {
                    val day = repo.day(today)
                    if (day.brushCount < day.brushTarget) repo.setBrushCount(today, day.brushCount + 1)
                }
                Notifications.cancel(context, slot)
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "de.zahnputz.app.REMIND"
        const val ACTION_DONE = "de.zahnputz.app.DONE"
        const val EXTRA_SLOT = "slot"
    }
}
