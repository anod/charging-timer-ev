package info.anodsplace.evtimer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val LightColors = lightColorScheme(
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
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFE6EEE3),
    surfaceContainerHigh = Color(0xFFDCE5DD),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),
)

internal val DarkColors = darkColorScheme(
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
    surfaceContainerLow = Color(0xFF1C241B),
    surfaceContainer = Color(0xFF222B21),
    surfaceContainerHigh = Color(0xFF303A2F),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
)

private val TonalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp)
)
private val DefaultTypography = Typography()
private val TonalTypography = DefaultTypography.copy(
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)
private val LocalUsesDynamicColor = staticCompositionLocalOf { true }
internal val TonalChipShape = RoundedCornerShape(16.dp)

// Dynamic palettes are wallpaper-dependent; only the audited static surfaces use alpha.
internal fun tonalSurfaceColor(surface: Color, usesDynamicColor: Boolean): Color =
    surface.copy(alpha = if (usesDynamicColor) 1f else 0.9f)

@Composable
internal fun tonalCardColors() = CardDefaults.cardColors(
    containerColor = tonalSurfaceColor(MaterialTheme.colorScheme.surfaceContainer, LocalUsesDynamicColor.current),
    contentColor = MaterialTheme.colorScheme.onSurface
)

@Composable
internal fun tonalCardBorder() =
    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

@Composable
internal fun tonalSummaryColors() = AssistChipDefaults.elevatedAssistChipColors(
    containerColor = tonalSurfaceColor(MaterialTheme.colorScheme.surfaceContainerLow, LocalUsesDynamicColor.current),
    labelColor = MaterialTheme.colorScheme.onSurface
)

@Composable
internal fun tonalFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
)

// Expect an Android provided dynamic color scheme. Non-Android platforms return null.
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dynamicScheme = if (dynamicColor) platformDynamicColorScheme(darkTheme) else null
    val colorScheme = dynamicScheme ?: if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalUsesDynamicColor provides (dynamicScheme != null)) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = TonalShapes,
            typography = TonalTypography,
            content = content
        )
    }
}
