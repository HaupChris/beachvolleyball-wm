package de.liegestuetz.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.liegestuetz.app.data.Settings
import de.liegestuetz.app.reminder.ReminderScheduler
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit) {
    val context = LocalContext.current
    // Re-check after returning from the system settings page.
    var exactAlarms by remember { mutableStateOf(ReminderScheduler.canUseExactAlarms(context)) }
    LifecycleResumeEffect(Unit) {
        exactAlarms = ReminderScheduler.canUseExactAlarms(context)
        onPauseOrDispose { }
    }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, true).show()
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Text("💪 Tagesziel", style = MaterialTheme.typography.titleMedium)
            Stepper(
                value = "${settings.dailyTarget} Liegestütze",
                onChange = { delta ->
                    val target = (settings.dailyTarget + delta).coerceIn(Settings.MIN_TARGET, Settings.MAX_TARGET)
                    onChange(settings.copy(dailyTarget = target))
                },
                canDecrease = settings.dailyTarget > Settings.MIN_TARGET,
                canIncrease = settings.dailyTarget < Settings.MAX_TARGET,
            )
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔 Erinnerungen", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.remindersEnabled, onCheckedChange = { onChange(settings.copy(remindersEnabled = it)) })
            }
            if (settings.remindersEnabled) {
                Text(
                    "Erinnert nur, solange das Tagesziel noch nicht erreicht ist.",
                    style = MaterialTheme.typography.bodySmall,
                )
                settings.reminderTimes.forEachIndexed { i, time ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. Erinnerung", Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            pickTime(time) { picked ->
                                onChange(settings.copy(reminderTimes = settings.reminderTimes.toMutableList().also { it[i] = picked }.sorted()))
                            }
                        }) { Text(time.format(timeFormat)) }
                        TextButton(
                            onClick = { onChange(settings.copy(reminderTimes = settings.reminderTimes.filterIndexed { j, _ -> j != i })) },
                            enabled = settings.reminderTimes.size > 1,
                        ) { Text("✕") }
                    }
                }
                if (settings.reminderTimes.size < Settings.MAX_REMINDERS) {
                    TextButton(onClick = {
                        val added = Settings.suggestedTime(settings.reminderTimes)
                        onChange(settings.copy(reminderTimes = (settings.reminderTimes + added).sorted()))
                    }) { Text("+ Erinnerung hinzufügen") }
                }
            }
            if (settings.remindersEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarms) {
                Text(
                    "Erinnerungen können sich um einige Minuten verzögern. Für pünktliche Erinnerungen " +
                        "\"Wecker & Erinnerungen\" erlauben.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("Pünktliche Erinnerungen erlauben") }
            }
        }

        Text(
            "Änderungen gelten ab heute; vergangene Tage behalten ihr damaliges Ziel. " +
                "Alle Daten bleiben lokal auf dem Gerät (Tracking seit ${settings.startDate.format(dayFormat)}).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
