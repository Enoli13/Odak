package com.enoli.odak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object OdakColors {
    val Paper = Color(0xFFF6F3EC)
    val Card = Color(0xFFFFFCF6)
    val Ink = Color(0xFF1F2A24)
    val Muted = Color(0xFF6B7069)
    val Green = Color(0xFF2F6B4F)
    val GreenSoft = Color(0xFFDCEBE1)
    val Amber = Color(0xFFC98A2B)
    val AmberSoft = Color(0xFFF6E7CC)
    val Line = Color(0xFFE3DED3)
}

private val scheme = lightColorScheme(
    primary = OdakColors.Green,
    onPrimary = Color.White,
    primaryContainer = OdakColors.GreenSoft,
    onPrimaryContainer = OdakColors.Ink,
    secondary = OdakColors.Amber,
    onSecondary = Color.White,
    secondaryContainer = OdakColors.AmberSoft,
    onSecondaryContainer = OdakColors.Ink,
    background = OdakColors.Paper,
    onBackground = OdakColors.Ink,
    surface = OdakColors.Paper,
    onSurface = OdakColors.Ink,
    surfaceVariant = OdakColors.Card,
    onSurfaceVariant = OdakColors.Muted,
    surfaceContainer = OdakColors.Card,
    surfaceContainerLow = OdakColors.Card,
    surfaceContainerHigh = OdakColors.Card,
    outline = OdakColors.Line,
    outlineVariant = OdakColors.Line,
)

private val serif = FontFamily.Serif

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.Medium, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
)

@Composable
fun OdakTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
