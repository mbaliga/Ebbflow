package ai.ebbflow.baseline.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF4FC3F7)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF101418),
    surface = Color(0xFF181D23),
)

private val LightColors = lightColorScheme(primary = Color(0xFF0277BD))

/** Minimal Material 3 theme for the bring-up UI. */
@Composable
fun EbbflowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
