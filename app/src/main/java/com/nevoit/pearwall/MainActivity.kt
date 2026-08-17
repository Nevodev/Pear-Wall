package com.nevoit.pearwall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import com.nevoit.pearwall.core.interaction.DimIndication
import com.nevoit.pearwall.core.interaction.overscroll.rememberOffsetOverscrollFactory
import com.nevoit.pearwall.core.theme.AppTheme
import com.nevoit.pearwall.page.SettingsPage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val overscroll = rememberOffsetOverscrollFactory()
                val indication = DimIndication()
                CompositionLocalProvider(
                    LocalOverscrollFactory provides overscroll,
                    LocalIndication provides indication
                ) {
                    SettingsPage()
                }
            }
        }
    }
}
