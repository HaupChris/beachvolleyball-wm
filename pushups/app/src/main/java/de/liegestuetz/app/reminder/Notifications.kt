package de.liegestuetz.app.reminder

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
import de.liegestuetz.app.MainActivity
import de.liegestuetz.app.R
import de.liegestuetz.app.data.Repository
import de.liegestuetz.core.streaks
import java.time.LocalDate

/** A single reminder notification; later reminders of the same day replace it. */
object Notifications {
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 2000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Erinnerungen an die täglichen Liegestütze" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)

        val repo = Repository.get(context)
        val streak = repo.state.value.history.streaks().current
        val day = repo.day(LocalDate.now())
        val title = if (day.reps == 0) "💪 Zeit für ${day.target} Liegestütze" else "💪 Noch ${day.remaining} Liegestütze"
        val text = if (streak > 0) "Halte deine Serie von $streak Tagen am Leben! 🔥" else "Starte heute eine neue Serie!"

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context,
            1000,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "✓ ${day.remaining} gemacht", done)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}
