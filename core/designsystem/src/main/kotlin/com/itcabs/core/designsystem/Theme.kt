package com.itcabs.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Design shape language: small components (buttons, inputs) 8px; cards 16px; large sheets 20px.
private val ItCabsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The app theme. Light-only for now (the design ships a light "Modern Corporate" look);
 * a dark scheme can be added later without touching call sites.
 */
@Composable
fun ItCabsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ItCabsLightColors,
        typography = ItCabsTypography,
        shapes = ItCabsShapes,
        content = content,
    )
}
