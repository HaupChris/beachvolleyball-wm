package de.habits.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.habits.app.MainActivity
import de.habits.app.R
import de.habits.app.data.Repository
import de.habits.core.Direction
import de.habits.core.HabitHistory

object Notifications {
    private const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Erinnerungen an deine Habits" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder(context: Context, h: HabitHistory) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)

        val habit = h.habit
        val streak = h.streak
        val unit = if (streak.weekly) "Wochen" else "Tage"
        val text = when {
            habit.direction == Direction.QUIT -> "Bleib dran – heute zählt jede Stunde. 💪"
            streak.atRisk -> "Letztes Mal verpasst – heute nicht auslassen, sonst endet die Serie."
            streak.current > 0 -> "Halte deine Serie von ${streak.current} $unit am Leben 🔥"
            else -> "Heute ist ein guter Tag, um anzufangen."
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${habit.emoji} ${habit.name}")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (habit.direction == Direction.BUILD) {
            val done = PendingIntent.getBroadcast(
                context,
                habit.id.toInt(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_DONE)
                    .putExtra(ReminderReceiver.EXTRA_HABIT, habit.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "✓ Erledigt", done)
        }
        NotificationManagerCompat.from(context).notify(notificationId(habit.id), builder.build())
    }

    fun cancel(context: Context, habitId: Long) = NotificationManagerCompat.from(context).cancel(notificationId(habitId))

    /** Removes a habit's notification once it no longer needs a reminder today. */
    fun cancelIfSettled(context: Context, habitId: Long) {
        val h = Repository.get(context).state.value.history(habitId) ?: return
        if (!ReminderScheduler.needsReminder(h)) cancel(context, habitId)
    }

    private fun notificationId(habitId: Long) = 10_000 + habitId.toInt()
}
