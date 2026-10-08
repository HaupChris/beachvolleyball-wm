package de.habits.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.habits.app.data.Repository

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_HABIT, -1)
        if (id < 0) return
        val repo = Repository.get(context)
        repo.refreshToday()
        val state = repo.state.value
        val habit = repo.habit(id) ?: return
        when (intent.action) {
            ACTION_REMIND -> {
                val history = state.history(id) ?: return
                if (ReminderScheduler.needsReminder(history)) Notifications.showReminder(context, history)
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                habit.reminders.getOrNull(index)?.let { ReminderScheduler.scheduleOne(context, id, index, it) }
            }
            ACTION_DONE -> {
                val target = habit.planAt(state.today).target
                repo.setAmount(id, state.today, maxOf(repo.amount(id, state.today), target))
                Notifications.cancel(context, id)
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "de.habits.app.REMIND"
        const val ACTION_DONE = "de.habits.app.DONE"
        const val EXTRA_HABIT = "habit"
        const val EXTRA_INDEX = "index"
    }
}
