package info.anodsplace.evtimer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FF7B6),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4D6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D7),
    onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFF3D6471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E9F9),
    onTertiaryContainer = Color(0xFF001F28),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73DA9C),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522B),
    onPrimaryContainer = Color(0xFF8FF7B6),
    secondary = Color(0xFFB3CCBB),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFCFE9D7),
    tertiary = Color(0xFFA4CDDC),
    onTertiary = Color(0xFF053542),
    tertiaryContainer = Color(0xFF234C59),
    onTertiaryContainer = Color(0xFFC0E9F9),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8B938B),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
