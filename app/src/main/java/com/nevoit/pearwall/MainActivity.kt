package com.nevoit.pearwall

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.nevoit.pearwall.pearmesh.PearMeshState
import com.nevoit.pearwall.pearmesh.PearMeshSurface
import com.nevoit.pearwall.ui.theme.PearWallTheme
import com.nevoit.pearwall.wallpaper.PearWallpaperService
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PearWallTheme { SettingsScreen() } }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { PearWallSettings(context) }
    val state = remember { PearWallRuntime.createState(context) }
    DisposableEffect(state) { onDispose { PearWallRuntime.release(state) } }

    var behavior by remember { mutableIntStateOf(settings.noArtworkBehavior) }
    var scale by remember { mutableFloatStateOf(settings.renderScale) }
    var fps by remember { mutableFloatStateOf(settings.frameRate.toFloat()) }
    var randomize by remember { mutableStateOf(settings.randomizeOnScreenOn) }
    var portrait by remember { mutableIntStateOf(settings.portraitPreset) }
    var landscape by remember { mutableIntStateOf(settings.landscapePreset) }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            settings.customArtworkUri = uri.toString()
            context.contentResolver.openInputStream(uri)
                ?.use(android.graphics.BitmapFactory::decodeStream)
                ?.let { PearWallRuntime.updateArtwork(context, it) }
        }

    Box(Modifier.fillMaxSize()) {
        PearMeshSurface(state, Modifier.fillMaxSize())
        Box(Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .34f)))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pear Wall", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            SettingsCard("无封面时的行为") {
                Choice("自选图片作为输入", behavior == PearWallSettings.CUSTOM_IMAGE) {
                    behavior = PearWallSettings.CUSTOM_IMAGE; settings.noArtworkBehavior = behavior
                }
                Choice("保留最后一次的封面", behavior == PearWallSettings.KEEP_LAST) {
                    behavior = PearWallSettings.KEEP_LAST; settings.noArtworkBehavior = behavior
                }
                if (behavior == PearWallSettings.CUSTOM_IMAGE) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("选择图片") }
                }
            }
            SettingsCard("渲染") {
                Text("渲染倍率 ${(scale * 100).roundToInt()}%", color = Color.White)
                Slider(
                    scale, { scale = it; state.setRenderScale(it) }, valueRange = .1f..1f,
                    onValueChangeFinished = { settings.renderScale = scale })
                Text("帧数 ${fps.roundToInt()} FPS", color = Color.White)
                Slider(
                    fps,
                    { fps = it; state.setTargetFrameRate(it.roundToInt()) },
                    valueRange = 10f..60f,
                    steps = 4,
                    onValueChangeFinished = { settings.frameRate = fps.roundToInt() })
            }
            SettingsCard("预设") {
                ToggleRow("每次亮屏随机预设", randomize) {
                    randomize = it; settings.randomizeOnScreenOn = it
                }
                Text("竖屏预设", color = Color.White)
                PresetButtons(PearMeshState.PortraitPresetCount, portrait) {
                    portrait = it; settings.portraitPreset = it; state.setPortraitPreset(it)
                }
                Text("横屏预设", color = Color.White)
                PresetButtons(PearMeshState.LandscapePresetCount, landscape) {
                    landscape = it; settings.landscapePreset = it; state.setLandscapePreset(it)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    openWallpaperPicker(context)
                },
            ) { Text("设为动态壁纸") }
        }
    }
}

private fun openWallpaperPicker(context: android.content.Context) {
    if (needsXiaomiWallpaperPermission(context)) {
        openXiaomiPermissionEditor(context)
        return
    }

    val component = ComponentName(context, PearWallpaperService::class.java)
    val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }
    val chooserIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
    val directStarted = runCatching {
        context.startActivity(directIntent)
        true
    }.getOrElse { error ->
        android.util.Log.w("PearWall", "Direct live wallpaper preview failed", error)
        false
    }
    if (directStarted) return

    runCatching {
        context.startActivity(chooserIntent)
    }.onFailure { error ->
        android.util.Log.w("PearWall", "Live wallpaper chooser failed", error)
        Toast.makeText(context, "系统不支持动态壁纸选择器", Toast.LENGTH_LONG).show()
    }
}

private fun needsXiaomiWallpaperPermission(context: android.content.Context): Boolean {
    val isXiaomiDevice = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Redmi", ignoreCase = true) ||
            Build.BRAND.equals("POCO", ignoreCase = true)
    if (!isXiaomiDevice) return false

    val preferences =
        context.getSharedPreferences("wallpaper_permission", android.content.Context.MODE_PRIVATE)
    return !preferences.getBoolean("xiaomi_permission_page_shown", false)
}

private fun openXiaomiPermissionEditor(context: android.content.Context) {
    val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
        )
        putExtra("extra_pkgname", context.packageName)
    }
    val opened = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
    if (opened) {
        context.getSharedPreferences("wallpaper_permission", android.content.Context.MODE_PRIVATE)
            .edit {
                putBoolean("xiaomi_permission_page_shown", true)
            }
        Toast.makeText(context, "请开启“动态壁纸服务”，返回后再次点击设置", Toast.LENGTH_LONG).show()
    } else {
        openWallpaperPickerWithoutXiaomiCheck(context)
    }
}

private fun openWallpaperPickerWithoutXiaomiCheck(context: android.content.Context) {
    val component = ComponentName(context, PearWallpaperService::class.java)
    val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }
    runCatching { context.startActivity(directIntent) }.onFailure { error ->
        android.util.Log.w("PearWall", "Direct live wallpaper preview failed", error)
        runCatching {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }.onFailure { chooserError ->
            android.util.Log.w("PearWall", "Live wallpaper chooser failed", chooserError)
            Toast.makeText(context, "系统不支持动态壁纸选择器", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(.62f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White); content()
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, select: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, select); Text(text, color = Color.White)
    }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, color = Color.White); Switch(checked, change)
    }
}

@Composable
private fun PresetButtons(count: Int, selected: Int, select: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            FilterChip(
                selected == index,
                { select(index) },
                { Text("${index + 1}") })
        }
    }
}
