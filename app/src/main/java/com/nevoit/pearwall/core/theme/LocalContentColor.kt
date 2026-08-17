package com.nevoit.pearwall.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle

val LocalContentColor = compositionLocalOf { AppLightColors.content }

val LocalTextStyle = compositionLocalOf { DefaultAppTypography.body }

@Composable
fun ProvideTextStyle(
    value: TextStyle,
    content: @Composable () -> Unit,
) {
    val mergedStyle = LocalTextStyle.current.merge(value)
    CompositionLocalProvider(
        LocalTextStyle provides mergedStyle,
        content = content,
    )
}
