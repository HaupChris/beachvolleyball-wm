package de.zahnputz.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.zahnputz.app.data.Settings
import de.zahnputz.app.reminder.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalLayoutApi::class)
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
            Text("🪥 Zähneputzen pro Tag", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { onChange(settings.copy(brushTimes = settings.brushTimes.dropLast(1))) },
                    enabled = settings.brushTimes.size > 1,
                ) { Text("−") }
                Text("${settings.brushTimes.size}×", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = {
                        val added = Settings.suggestedTime(settings.brushTimes)
                        onChange(settings.copy(brushTimes = (settings.brushTimes + added).sorted()))
                    },
                    enabled = settings.brushTimes.size < Settings.MAX_BRUSHES,
                ) { Text("+") }
            }
            settings.brushTimes.forEachIndexed { i, time ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. Erinnerung", Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        pickTime(time) { picked ->
                            onChange(settings.copy(brushTimes = settings.brushTimes.toMutableList().also { it[i] = picked }.sorted()))
                        }
                    }) { Text(time.format(timeFormat)) }
                }
            }
        }

        SectionCard {
            Text("🧵 Zahnseide-Tage", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.values().forEach { day ->
                    val selected = day in settings.flossDays
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange(settings.copy(flossDays = if (selected) settings.flossDays - day else settings.flossDays + day))
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, GERMAN)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Erinnerung", Modifier.weight(1f))
                OutlinedButton(onClick = { pickTime(settings.flossTime) { onChange(settings.copy(flossTime = it)) } }) {
                    Text(settings.flossTime.format(timeFormat))
                }
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔 Erinnerungen", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.remindersEnabled, onCheckedChange = { onChange(settings.copy(remindersEnabled = it)) })
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
            "Änderungen gelten ab heute; vergangene Tage behalten ihre damaligen Ziele. " +
                "Alle Daten bleiben lokal auf dem Gerät (Tracking seit ${settings.startDate.format(dayFormat)}).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
