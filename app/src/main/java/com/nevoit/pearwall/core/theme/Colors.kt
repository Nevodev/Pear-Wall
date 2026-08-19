package com.nevoit.pearwall.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nevoit.pearwall.core.theme.tokens.Blue500
import com.nevoit.pearwall.core.theme.tokens.Green500

@Immutable
data class AppColors(
    val inactiveTrack: Color,
    val activeTrack: Color,
    val inactiveThumb: Color,
    val activeThumb: Color,
    val content: Color,
    val primary: Color = Blue500,
    val elevatedPageBackground: Color,
    val elevatedCardBackground: Color,
    val scrimNormal: Color,
    val scrimMedium: Color,
    val contentVariant: Color,
    val background: Color,
    val pageBackground: Color,
    val cardBackground: Color,
    val segmentedControlBackground: Color = scrimNormal,
    val onSegmentedControlBackground: Color = contentVariant,
    val segmentedControlIndicator: Color,
    val onSegmentedControlIndicator: Color = content
)

val AppLightColors = AppColors(
    background = Color.White,
    inactiveTrack = Color(0xFF787880).copy(.25f),
    activeTrack = Green500,
    inactiveThumb = Color.White,
    activeThumb = Color.White,
    content = Color.Black,
    elevatedPageBackground = Color(0xFFF3F4F6),
    elevatedCardBackground = Color.White,
    scrimNormal = Color.Black.copy(alpha = 0.05f),
    contentVariant = Color.Black.copy(.5f),
    pageBackground = Color(0xFFF3F4F6),
    cardBackground = Color.White,
    scrimMedium = Color.Black.copy(alpha = 0.1f),
    segmentedControlIndicator = Color.White
)

val AppDarkColors = AppColors(
    background = Color.Black,
    inactiveTrack = Color(0xFF787880).copy(.25f),
    activeTrack = Green500,
    inactiveThumb = Color.White,
    activeThumb = Color.White,
    content = Color.White,
    elevatedPageBackground = Color(0xFF1C1C1E),
    elevatedCardBackground = Color(0xFF2C2C2E),
    scrimNormal = Color.White.copy(alpha = 0.1f),
    contentVariant = Color.White.copy(.5f),
    pageBackground = Color.Black,
    cardBackground = Color(0xFF1B1C1D),
    scrimMedium = Color.White.copy(alpha = 0.2f),
    segmentedControlIndicator = Color(0xFF636366),
)

internal val LocalAppColors = staticCompositionLocalOf { AppLightColors }
