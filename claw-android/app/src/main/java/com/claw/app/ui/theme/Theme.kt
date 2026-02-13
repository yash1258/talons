package com.claw.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

// ── Claw Brand Colors ──────────────────────────────
// Predator palette: Amber gold accents on deep obsidian

private val Gold        = Color(0xFFD4A017)  // Primary — talon gold
private val GoldLight   = Color(0xFFE8C547)  // Lighter gold for containers
private val GoldDark    = Color(0xFF9A7200)  // Darker gold for pressed states
private val Amber       = Color(0xFFFFC107)  // Accent amber
private val Obsidian    = Color(0xFF0A0A0A)  // True dark background
private val Charcoal    = Color(0xFF141414)  // Elevated surface
private val DarkGray    = Color(0xFF1E1E1E)  // Cards / surface variant
private val MediumGray  = Color(0xFF2A2A2A)  // Borders / outlines
private val SoftWhite   = Color(0xFFF0ECE3)  // Text — warm off-white
private val MutedGold   = Color(0xFFB8A67E)  // Secondary text
private val Error       = Color(0xFFCF6679)  // Error — muted rose
private val Success     = Color(0xFF66BB6A)  // Success green

// ── Light Scheme (warm ivory base) ──────────────────

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B6914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E6B8),
    onPrimaryContainer = Color(0xFF2D1F00),
    secondary = Color(0xFF6B5E4B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E3CC),
    onSecondaryContainer = Color(0xFF241B0C),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFEDE4D3),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFCFC5B4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

// ── Dark Scheme (obsidian + gold) ───────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3D2E00),
    onPrimaryContainer = GoldLight,
    secondary = MutedGold,
    onSecondary = Color(0xFF1E1A10),
    secondaryContainer = MediumGray,
    onSecondaryContainer = SoftWhite,
    background = Obsidian,
    onBackground = SoftWhite,
    surface = Charcoal,
    onSurface = SoftWhite,
    surfaceVariant = DarkGray,
    onSurfaceVariant = Color(0xFFADA79E),
    outline = MediumGray,
    outlineVariant = Color(0xFF3A3A3A),
    error = Error,
    onError = Color(0xFF1E0006),
    errorContainer = Color(0xFF3B1018),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = SoftWhite,
    inverseOnSurface = Obsidian,
    inversePrimary = GoldDark,
    surfaceTint = Gold,
)

@Composable
fun ClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disabled dynamic color — Claw has its own brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar matches the dark surface, not the primary color
            window.statusBarColor = if (darkTheme) Obsidian.toArgb() else Color(0xFFFFFBF5).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
