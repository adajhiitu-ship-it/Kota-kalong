package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = PureWhite,
    primaryContainer = MintContainer,
    onPrimaryContainer = ForestGreenDark,
    secondary = EmeraldAccent,
    onSecondary = PureWhite,
    secondaryContainer = MintContainerStrong,
    onSecondaryContainer = ForestGreenDark,
    tertiary = WarmGold,
    onTertiary = PureWhite,
    tertiaryContainer = WarmGoldContainer,
    onTertiaryContainer = CharcoalInk,
    background = SoftMintSurface,
    onBackground = CharcoalInk,
    surface = PureWhite,
    onSurface = CharcoalInk,
    surfaceVariant = SoftMintSurface,
    onSurfaceVariant = SlateInk,
    outline = SageOutline,
    outlineVariant = SageOutlineLight,
    error = ErrorCrimson,
    onError = PureWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorCrimson
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkForestPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = ForestGreen,
    onPrimaryContainer = MintContainer,
    secondary = EmeraldAccent,
    onSecondary = PureWhite,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = WarmGold,
    onTertiary = CharcoalInk,
    tertiaryContainer = Color(0xFF47360A),
    onTertiaryContainer = WarmGoldContainer,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF334B3E),
    outlineVariant = Color(0xFF23362C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun KotaKalongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
