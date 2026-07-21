package com.itcabs.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ponytail: Inter is the design typeface, but the .ttf isn't bundled — using the system
// FontFamily.Default with the design's exact sizes/weights. Drop Inter into res/font and
// swap FontFamily here to complete the look; sizes/weights stay as-is.
private val Inter = FontFamily.Default

/** Maps the design's type scale onto the M3 slots the auth screens use. */
val ItCabsTypography = Typography(
    // headline-lg-mobile — screen titles ("Welcome to IT Cars")
    headlineMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em,
    ),
    // title-lg — button / emphasis text
    titleLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    // body-lg
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // body-md
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // label-md — uppercase section labels ("MOBILE NUMBER")
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.em,
    ),
)
