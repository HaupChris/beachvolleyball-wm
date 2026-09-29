package de.zahnputz.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.zahnputz.core.DayStatus

private val Teal = Color(0xFF2E9CCA)

@Composable
fun ZahnputzTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Teal)
        else -> lightColorScheme(primary = Teal)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

object StatusColors {
    val perfect = Color(0xFF43A047)
    val partial = Color(0xFFFFB300)
    val missed = Color(0xFFE53935)
    val flame = Color(0xFFFF7043)
}

fun DayStatus.color(): Color? = when (this) {
    DayStatus.PERFECT -> StatusColors.perfect
    DayStatus.PARTIAL -> StatusColors.partial
    DayStatus.MISSED -> StatusColors.missed
    DayStatus.PENDING, DayStatus.NOT_TRACKED -> null
}
