package de.zahnputz.app.reminder

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
import de.zahnputz.app.MainActivity
import de.zahnputz.app.R
import de.zahnputz.app.data.Repository
import de.zahnputz.core.streaks

object Notifications {
    private const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Erinnerungen ans Zähneputzen und an Zahnseide" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder(context: Context, slot: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)

        val streak = Repository.get(context).state.value.history.streaks().current
        val (title, action) = if (slot == ReminderScheduler.FLOSS) {
            "🧵 Zeit für Zahnseide" to "Zahnseide erledigt"
        } else {
            "🪥 Zeit zum Zähneputzen" to "Geputzt"
        }
        val text = if (streak > 0) "Halte deine Serie von $streak Tagen am Leben! 🔥" else "Starte heute eine neue Serie!"

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context,
            1000 + ReminderScheduler.requestCode(slot),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_DONE)
                .putExtra(ReminderReceiver.EXTRA_SLOT, slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "✓ $action", done)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(slot), notification)
    }

    fun cancel(context: Context, slot: Int) = NotificationManagerCompat.from(context).cancel(notificationId(slot))

    private fun notificationId(slot: Int) = 2000 + ReminderScheduler.requestCode(slot)
}
