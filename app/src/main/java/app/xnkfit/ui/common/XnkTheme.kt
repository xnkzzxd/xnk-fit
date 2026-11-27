package app.xnkfit.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import app.xnkfit.data.prefs.ThemeMode
import app.xnkfit.ui.design.XnkDesignTheme

/** Whether the dark scheme applies: the user's choice in Pengaturan (P-7), or the system's when set to follow it. */
fun useDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}

/** App theme: the XNK tokens (ui/design, docs/Design-Visual.md) in light or dark, Material 3 coloured from them. */
@Composable
fun XnkTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    XnkDesignTheme(dark = useDarkTheme(mode, isSystemInDarkTheme()), content = content)
}
