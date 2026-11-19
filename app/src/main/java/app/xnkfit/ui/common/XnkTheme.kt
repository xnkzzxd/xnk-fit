package app.xnkfit.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import app.xnkfit.data.prefs.ThemeMode

/** Whether the dark scheme applies: the user's choice in Pengaturan (P-7), or the system's when set to follow it. */
fun useDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}

/** Skeleton theme: Material 3 defaults, light and dark. Real tokens come with the visual pass (Design §11). */
@Composable
fun XnkTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val colors = if (useDarkTheme(mode, isSystemInDarkTheme())) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
