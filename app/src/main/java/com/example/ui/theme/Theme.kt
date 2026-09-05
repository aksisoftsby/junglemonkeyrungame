package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JungleDarkColorScheme = darkColorScheme(
    primary = BrightJungleGreen,
    onPrimary = DarkUI,
    primaryContainer = ForestGreen,
    onPrimaryContainer = Color.White,
    secondary = CoinGold,
    onSecondary = DarkUI,
    tertiary = BananaYellow,
    background = DarkUI,
    surface = DeepJungleGreen,
    onBackground = Color.White,
    onSurface = Color.White
)

private val JungleLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = LeafGreen,
    onPrimaryContainer = Color.White,
    secondary = CoinGold,
    onSecondary = DarkUI,
    tertiary = BananaYellow,
    background = SkyLightGreen,
    surface = Color(0xFFE8F5E9),
    onBackground = DarkUI,
    onSurface = DarkUI
)

@Composable
fun JungleMonkeyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JungleDarkColorScheme else JungleLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    JungleMonkeyTheme(darkTheme = darkTheme, content = content)
}
