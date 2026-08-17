package com.nevoit.pearwall.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nevoit.pearwall.R

val BrandFontFamily = FontFamily(
    Font(
        resId = R.font.playwrite_us_trad_regular,
        weight = FontWeight.W400,
        style = FontStyle.Normal,
    ),
)

val BrandPosterTextStyle = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.W400,
    fontSize = 40.sp,
    lineHeight = 48.sp,
    letterSpacing = 0.sp,
)