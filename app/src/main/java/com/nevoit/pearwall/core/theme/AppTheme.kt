package com.nevoit.pearwall.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val typography: AppTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTypography.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDark.current

    @Composable
    operator fun invoke(
        darkTheme: Boolean = isSystemInDarkTheme(),
        colors: AppColors = if (darkTheme) AppDarkColors else AppLightColors,
        typography: AppTypography = DefaultAppTypography,
        content: @Composable () -> Unit,
    ) {
        ConfigureSystemBars(darkTheme)

        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppTypography provides typography,
            LocalContentColor provides colors.content,
            LocalTextStyle provides typography.body,
            LocalIsDark provides darkTheme,
            content = content,
        )
    }
}

private val LocalIsDark = staticCompositionLocalOf { false }

@Composable
private fun ConfigureSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
