package de.liegestuetz.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.liegestuetz.app.data.Repository
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repository.get(context)
        repo.refreshToday()
        val today = LocalDate.now()
        when (intent.action) {
            ACTION_REMIND -> {
                val slot = intent.getIntExtra(EXTRA_SLOT, -1)
                if (slot < 0) return
                if (!ReminderScheduler.isDone(context, today)) Notifications.showReminder(context)
                ReminderScheduler.schedule(context, repo.state.value.settings, slot)
            }
            ACTION_DONE -> {
                // "Done" fills up today's remaining reps.
                repo.addReps(today, repo.day(today).remaining)
                Notifications.cancel(context)
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "de.liegestuetz.app.REMIND"
        const val ACTION_DONE = "de.liegestuetz.app.DONE"
        const val EXTRA_SLOT = "slot"
    }
}
