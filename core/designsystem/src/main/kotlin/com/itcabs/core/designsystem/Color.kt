package com.itcabs.core.designsystem

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Raw tokens from the IT Cars design system (Stitch DESIGN.md). Exposed for the few
// places that need an exact token the M3 role mapping doesn't cover (e.g. the brand blue).
object ItCabsColors {
    val Primary = Color(0xFF004AC6)          // brand blue (icon squares)
    val PrimaryContainer = Color(0xFF2563EB) // action blue (buttons)
    val OnPrimary = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFAF8FF)          // base canvas
    val White = Color(0xFFFFFFFF)            // elevated cards / inputs
    val OnSurface = Color(0xFF191B23)
    val OnSurfaceVariant = Color(0xFF434655)
    val Outline = Color(0xFF737686)
    val OutlineVariant = Color(0xFFC3C6D7)
    val SurfaceContainerLow = Color(0xFFF3F3FE)
    val SurfaceContainer = Color(0xFFEDEDF9)
    val Error = Color(0xFFBA1A1A)
}

/**
 * Light scheme. The action blue (#2563EB) is mapped to M3 `primary` so default Buttons
 * match the design out of the box; the surface strategy is dual-tone (lavender canvas,
 * white cards) per the design's elevation model.
 */
internal val ItCabsLightColors = lightColorScheme(
    primary = ItCabsColors.PrimaryContainer,
    onPrimary = ItCabsColors.OnPrimary,
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF505F76),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E1FB),
    onSecondaryContainer = Color(0xFF54647A),
    background = ItCabsColors.Surface,
    onBackground = ItCabsColors.OnSurface,
    surface = ItCabsColors.White,
    onSurface = ItCabsColors.OnSurface,
    surfaceVariant = Color(0xFFE1E2ED),
    onSurfaceVariant = ItCabsColors.OnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = ItCabsColors.SurfaceContainerLow,
    surfaceContainer = ItCabsColors.SurfaceContainer,
    surfaceContainerHigh = Color(0xFFE7E7F3),
    surfaceContainerHighest = Color(0xFFE1E2ED),
    outline = ItCabsColors.Outline,
    outlineVariant = ItCabsColors.OutlineVariant,
    error = ItCabsColors.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)
