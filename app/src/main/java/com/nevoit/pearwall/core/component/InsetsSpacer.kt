package com.nevoit.pearwall.core.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StatusBarSpacer() {
    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
}

@Composable
fun NavigationBarSpacer() {
    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}
