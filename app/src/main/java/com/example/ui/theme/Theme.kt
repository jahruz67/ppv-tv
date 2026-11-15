package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val PpvTvColorScheme = darkColorScheme(
    primary = TvPrimary,
    onPrimary = TvOnPrimary,
    primaryContainer = TvSurfaceHighlight,
    onPrimaryContainer = TvTextPrimary,
    secondary = TvSecondary,
    onSecondary = TvOnSecondary,
    secondaryContainer = TvSurfaceElevated,
    onSecondaryContainer = TvTextPrimary,
    tertiary = TvUpcomingAmber,
    onTertiary = TvBackground,
    background = TvBackground,
    onBackground = TvTextPrimary,
    surface = TvSurface,
    onSurface = TvTextPrimary,
    surfaceVariant = TvSurfaceElevated,
    onSurfaceVariant = TvTextSecondary,
    error = TvErrorRed,
    onError = TvOnPrimary,
    border = TvBorderSubtle,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PpvTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PpvTvColorScheme,
        typography = TvAppTypography,
        content = content,
    )
}
