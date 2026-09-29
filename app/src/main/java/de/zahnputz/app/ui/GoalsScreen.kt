package de.zahnputz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.zahnputz.core.Totals
import de.zahnputz.core.Xp
import de.zahnputz.core.achievementProgress
import de.zahnputz.core.levelFor

@Composable
fun GoalsScreen(totals: Totals) {
    val level = levelFor(totals.xp)
    val all = totals.achievementProgress()
    val (unlocked, locked) = all.partition { it.unlocked }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Text("Level ${level.number} · ${level.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Progress(level.progress)
            Text("${level.xpInLevel} / ${level.xpForNext} XP  ·  gesamt ${totals.xp} XP", style = MaterialTheme.typography.labelMedium)
            Text("🏆 ${unlocked.size} von ${all.size} Erfolgen freigeschaltet", style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard {
            Text("🎯 Offene Ziele", style = MaterialTheme.typography.titleMedium)
            locked.sortedByDescending { it.progress }.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider()
                AchievementRow(p)
            }
            if (locked.isEmpty()) Text("Alles geschafft – du bist eine Legende! 👑")
        }

        if (unlocked.isNotEmpty()) {
            SectionCard {
                Text("🏆 Erreicht", style = MaterialTheme.typography.titleMedium)
                unlocked.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    AchievementRow(p)
                }
            }
        }

        SectionCard {
            Text("So sammelst du XP", style = MaterialTheme.typography.titleMedium)
            Text("🪥 +${Xp.PER_BRUSH} pro Putzen (bis zum Tagesziel)")
            Text("🧵 +${Xp.PER_FLOSS} pro Zahnseide, +${Xp.BONUS_FLOSS} an freien Tagen")
            Text("✨ +${Xp.PERFECT_DAY} pro perfektem Tag")
            Text("🔥 +1 pro Serientag obendrauf (max. +${Xp.MAX_STREAK_BONUS})")
        }
    }
}
