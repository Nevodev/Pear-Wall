package com.nevoit.pearwall.page

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nevoit.pearwall.audio.GlobalAudioMeter
import com.nevoit.pearwall.core.component.Text

@Composable
fun AudioMeterTest() {
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("等待录音权限") }
    val displayedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(45, easing = FastOutSlowInEasing),
        label = "displayedLevel",
    )
    val displayedBass by animateFloatAsState(
        targetValue = bass,
        animationSpec = tween(45, easing = FastOutSlowInEasing),
        label = "displayedBass",
    )
    val meter = remember {
        GlobalAudioMeter(
            onLevel = { level = it },
            onBass = { bass = it },
            onStatus = { status = it },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) meter.start() else status = "未授予 RECORD_AUDIO 权限"
    }

    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            meter.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { meter.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("整体音量  ${(displayedLevel * 100).toInt()}%")
        MeterBar(value = displayedLevel)
        Text(
            "低频响应  ${(displayedBass * 100).toInt()}%",
            modifier = Modifier.padding(top = 20.dp),
        )
        MeterBar(value = displayedBass)
        Text(status, modifier = Modifier.padding(top = 16.dp))
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .background(Color(0x225C6BC0), RoundedCornerShape(18.dp))
                .clickable { meter.start() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("重新启动 Visualizer(0)")
        }
    }
}

@Composable
private fun MeterBar(value: Float) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0x223F51B5), RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value)
                .height(24.dp)
                .background(Color(0xFF5C6BC0), RoundedCornerShape(12.dp)),
        )
    }
}
