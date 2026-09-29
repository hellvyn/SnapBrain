package com.snapbrain.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Spec §6.1: light is the main look (mockup row B); dark follows the system setting (row A).
private val LightColors = lightColorScheme(
    primary = Color(0xFF16151C),
    onPrimary = Color.White,
    secondary = Color(0xFF6C4CF5),
    onSecondary = Color.White,
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF16151C),
    surface = Color.White,
    onSurface = Color(0xFF16151C),
    surfaceVariant = Color(0xFFECEAF4),
    onSurfaceVariant = Color(0xFF6B6880),
    outline = Color(0xFFC9C5DA),
    outlineVariant = Color(0xFFECEAF4),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF12141A),
    secondary = Color(0xFF8FB0FF),
    onSecondary = Color(0xFF12141A),
    background = Color(0xFF12141A),
    onBackground = Color(0xFFF2F3F7),
    surface = Color(0xFF1C1F28),
    onSurface = Color(0xFFF2F3F7),
    surfaceVariant = Color(0xFF252936),
    onSurfaceVariant = Color(0xFFA3A9BA),
    outline = Color(0xFF4A5063),
    outlineVariant = Color(0xFF2E3342),
    error = Color(0xFFFF8A7A),
)

@Composable
fun SnapBrainTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, typography = SnapTypography) {
        // Outside a Scaffold nothing provides a content color, so default text/icons would render black in dark mode.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

/** Upcoming (not yet overdue) due dates. */
val soonColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB86B) else Color(0xFFB4530F)

/** A white card with a soft shadow in light mode, a bordered card in dark mode. */
@Composable
fun SnapCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = color)
    val elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp)
    val border = if (dark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    }
}
