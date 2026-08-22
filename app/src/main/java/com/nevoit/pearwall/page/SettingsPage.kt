package com.nevoit.pearwall.page

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.nevoit.pearwall.PearWallRuntime
import com.nevoit.pearwall.PearWallSettings
import com.nevoit.pearwall.R
import com.nevoit.pearwall.core.animation.Springs
import com.nevoit.pearwall.core.component.BrandHeader
import com.nevoit.pearwall.core.component.Icon
import com.nevoit.pearwall.core.component.NavigationBarSpacer
import com.nevoit.pearwall.core.component.Switch
import com.nevoit.pearwall.core.component.Text
import com.nevoit.pearwall.core.component.VGap
import com.nevoit.pearwall.core.interaction.rememberFlingBehavior
import com.nevoit.pearwall.core.modifier.cachedClip
import com.nevoit.pearwall.core.modifier.nullClickable
import com.nevoit.pearwall.core.modifier.thenIf
import com.nevoit.pearwall.core.theme.AppTheme
import com.nevoit.pearwall.core.theme.LocalContentColor
import com.nevoit.pearwall.media.MediaArtworkService
import com.nevoit.pearwall.pearmesh.PearMeshSurface
import com.nevoit.pearwall.wallpaper.PearWallpaperService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val settings = remember { PearWallSettings(context) }
    val state = remember { PearWallRuntime.createState(context) }
    DisposableEffect(state) { onDispose { PearWallRuntime.release(state) } }

    var behavior by remember { mutableIntStateOf(settings.noArtworkBehavior) }
    var scale by remember { mutableFloatStateOf(settings.renderScale) }
    var moruStyle by remember { mutableStateOf(settings.moruStyle) }
    var blurEnabled by remember { mutableStateOf(settings.blurEnabled) }
    var fps by remember { mutableFloatStateOf(settings.frameRate.toFloat()) }
    var randomize by remember { mutableStateOf(settings.randomizeOnScreenOn) }
    var audioVisualization by remember { mutableStateOf(settings.audioVisualizationEnabled) }
    var pauseUsesNoArtworkBehavior by remember {
        mutableStateOf(settings.pauseUsesNoArtworkBehavior)
    }
    var pauseFlowEnabled by remember { mutableStateOf(settings.pauseFlowEnabled) }
    var blockVideoPlayers by remember { mutableStateOf(settings.blockVideoPlayers) }
    var portrait by remember { mutableIntStateOf(settings.portraitPreset) }
    var landscape by remember { mutableIntStateOf(settings.landscapePreset) }
    var scrimSelection by remember { mutableIntStateOf(if (settings.scrimAlpha < 0.4f) 0 else 1) }
    var blurSelection by remember {
        mutableIntStateOf(
            when {
                settings.blurMultiplier < 1f -> 0
                settings.blurMultiplier > 1.4f -> 3
                settings.blurMultiplier > 1f -> 2
                else -> 1
            },
        )
    }
    var flowSpeedSelection by remember {
        mutableIntStateOf(settings.flowSpeed.coerceIn(0, 1))
    }
    var hasNotificationAccess by remember {
        mutableStateOf(hasNotificationListenerAccess(context))
    }

    var hasAudioPermission by remember {
        mutableStateOf(hasAudioPermission(context))
    }

    var isCurrentWallpaper by remember {
        mutableStateOf(isPearWallCurrentWallpaper(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = hasNotificationListenerAccess(context)
                isCurrentWallpaper = isPearWallCurrentWallpaper(context)
                hasAudioPermission = hasAudioPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted || hasAudioPermission(context)
    }

    fun requestAudioPermission() {
        if (hasAudioPermission) return
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val imagePickerScope = rememberCoroutineScope()
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settings.customArtworkUri = uri.toString()
            imagePickerScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeArtwork(context, uri)
                }
                bitmap?.let { PearWallRuntime.updateCustomArtwork(context, it) }
            }
        }
    val listState = rememberLazyListState()
    val containerSize = LocalWindowInfo.current.containerSize
    val containerDpSize = LocalWindowInfo.current.containerDpSize
    val isLandscape = containerSize.width > containerSize.height
    val lyricsScrollDistancePx = containerSize.height.toFloat() / 2f
    val resolvedHeaderPadding = containerDpSize.height / 3
    val leadingSpacerHeightPx = with(LocalDensity.current) { resolvedHeaderPadding.toPx() }

    LaunchedEffect(listState, lyricsScrollDistancePx, leadingSpacerHeightPx) {
        snapshotFlow {
            listState.behindLyricsProgress(
                distancePx = lyricsScrollDistancePx,
                leadingItemHeightPx = leadingSpacerHeightPx,
            )
        }.collect { progress ->
            state.setBehindLyricsProgress(progress)
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val resolvedPadding = if (!isLandscape) {
        WindowInsets.statusBars.asPaddingValues()
    } else {
        val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
        val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
        val landscapePadding =
            (containerDpSize.width - safeStart - safeEnd - containerDpSize.height * 1.2f) / 2
        PaddingValues(
            start = safeStart + landscapePadding,
            top = safeDrawingPadding.calculateTopPadding(),
            end = safeEnd + landscapePadding,
        )
    }
    var isCreditsBottomSheetVisible by remember { mutableStateOf(false) }
    var isAdvancedBottomSheetVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Hide system bars once the second (fullscreen background) page settles.
    val isOnBackgroundPage by remember {
        derivedStateOf { pagerState.currentPage == 1 }
    }
    val view = LocalView.current
    DisposableEffect(isOnBackgroundPage, view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(window, view) }
        if (controller != null) {
            if (isOnBackgroundPage) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun exportCurrentFrame() {
        scope.launch {
            val deferred = CompletableDeferred<Bitmap?>()
            state.requestFrameCapture { deferred.complete(it) }
            val bitmap = withTimeoutOrNull(2_000.milliseconds) { deferred.await() } ?: return@launch
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "pearwall-${System.currentTimeMillis()}.png",
                        )
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PearWall")
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore insert failed")
                    resolver.openOutputStream(uri)?.use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, EXPORT_PNG_QUALITY, output)
                    }
                    bitmap.recycle()
                }.isSuccess
            }
            Toast.makeText(
                context,
                if (saved) "已导出到 Pictures/PearWall" else "导出失败",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        PearMeshSurface(state, Modifier.fillMaxSize())
        CompositionLocalProvider(
            LocalContentColor provides Color.White
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page == 0) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = resolvedPadding,
                        flingBehavior = rememberFlingBehavior(),
                    ) {
                item {
                    VGap(resolvedHeaderPadding)
                }
                item {
                    BrandHeader(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { exportCurrentFrame() }
                            .graphicsLayer {
                                alpha = 0.8f
                                blendMode = BlendMode.Plus
                            },
                        text = "pear wall"
                    )
                }
                if (!isCurrentWallpaper) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            PrimaryButton(
                                modifier = Modifier.weight(1f),
                                text = "设为动态壁纸",
                                onClick = { openWallpaperPicker(context) },
                            )
//                            HGap()
//                            PrimaryButton(
//                                modifier = Modifier.weight(1f),
//                                text = "壁纸选择器",
//                                onClick = { openWallpaperChooser(context) },
//                            )
                        }
                        VGap()
                        VGap()
                    }
                }
                item {
                    SettingsCard("权限") {
                        ItemRow(
                            iconRes = R.drawable.ic_bell_badge,
                            title = "通知使用权",
                            subtitle = if (hasNotificationAccess) {
                                "已获取"
                            } else {
                                "用于读取正在播放的媒体封面"
                            },
                            onClick = { openNotificationListenerSettings(context) },
                        )
                        SwitchDivider()
                        ItemRow(
                            iconRes = R.drawable.ic_microphone,
                            title = "麦克风权限",
                            subtitle = if (hasAudioPermission) {
                                "已获取"
                            } else {
                                "用于捕获系统输出音频"
                            },
                            onClick = { requestAudioPermission() },
                        )
                    }
                    VGap()
                    VGap()
                }
                item {
                    SettingsCard("未获取到封面时") {
                        Choice(
                            text = "继续使用上一张封面",
                            iconRes = R.drawable.ic_history,
                            selected = behavior == PearWallSettings.KEEP_LAST,
                        ) {
                            behavior = PearWallSettings.KEEP_LAST
                            PearWallRuntime.setNoArtworkBehavior(context, behavior)
                        }
                        NormalDivider()
                        Choice(
                            text = "使用自选图片",
                            iconRes = R.drawable.ic_photo,
                            selected = behavior == PearWallSettings.CUSTOM_IMAGE,
                        ) {
                            behavior = PearWallSettings.CUSTOM_IMAGE
                            PearWallRuntime.setNoArtworkBehavior(context, behavior)
                        }
                        if (behavior == PearWallSettings.CUSTOM_IMAGE) {
                            NormalDivider()
                            ImagePickerRow {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        }
                    }
                    VGap()
                    VGap()
                }
                item {
                    SettingsCard("暂停时") {
                        if (behavior == PearWallSettings.CUSTOM_IMAGE) {
                            ToggleRow(
                                "使用自选图片",
                                "开启后，播放暂停时显示“未获取到封面时”设置的自选图片",
                                iconRes = R.drawable.ic_photo,
                                checked = pauseUsesNoArtworkBehavior,
                            ) { enabled ->
                                pauseUsesNoArtworkBehavior = enabled
                                PearWallRuntime.setPauseUsesNoArtworkBehavior(context, enabled)
                            }
                            SwitchDivider()
                        }
                        ToggleRow(
                            "暂停流动效果",
                            "暂停播放后冻结壁纸动画，恢复播放时继续",
                            iconRes = R.drawable.ic_play,
                            checked = pauseFlowEnabled,
                        ) { enabled ->
                            pauseFlowEnabled = enabled
                            settings.pauseFlowEnabled = enabled
                            state.setPauseFlowEnabled(enabled)
                        }
                    }
                    VGap()
                    VGap()
                }
                item {
                    SettingsCard("画面效果") {
                        ToggleRow(
                            "开启音频可视化",
                            "可能会略微增加功耗",
                            iconRes = R.drawable.ic_waveform,
                            checked = audioVisualization
                        ) { enabled ->
                            if (enabled && !hasAudioPermission) {
                                requestAudioPermission()
                                audioVisualization = true
                                PearWallRuntime.setAudioVisualizationEnabled(context, true)
                            } else {
                                audioVisualization = enabled
                                PearWallRuntime.setAudioVisualizationEnabled(context, enabled)
                            }
                        }
                        SwitchDivider()
                        EffectOptionRow(
                            "压暗",
                            R.drawable.ic_scrim,
                            listOf("轻微", "标准"),
                            scrimSelection
                        ) {
                            scrimSelection = it
                            val alpha = listOf(0.25f, 0.4f)[it]
                            settings.scrimAlpha = alpha
                            state.setScrimAlpha(alpha)
                            PearWallRuntime.applySettings(context)
                        }
                        TwoSideDivider()
                        EffectOptionRow(
                            "模糊半径",
                            R.drawable.ic_blur,
                            listOf("小", "标准", "大", "特大"),
                            blurSelection
                        ) {
                            blurSelection = it
                            val multiplier = listOf(0.75f, 1f, 1.4f, 2f)[it]
                            settings.blurMultiplier = multiplier
                            state.setBlurMultiplier(multiplier)
                            PearWallRuntime.applySettings(context)
                        }
                        TwoSideDivider()
                        EffectOptionRow(
                            "流动速度",
                            R.drawable.ic_speed,
                            listOf("标准", "快速"),
                            flowSpeedSelection
                        ) {
                            flowSpeedSelection = it
                            settings.flowSpeed = it
                            state.setFlowSpeed(it)
                            PearWallRuntime.applySettings(context)
                        }
                    }
                    VGap()
                    VGap()
                }
                item {
                    SettingsCard {
                        ItemRow(
                            iconRes = R.drawable.ic_engine,
                            title = "高级",
                            onClick = { isAdvancedBottomSheetVisible = true },
                        )
                    }
                    VGap()
                    VGap()
                }
                item {
                    SettingsCard("关于") {
                        ItemRow(
                            iconRes = R.drawable.ic_pet_paw,
                            title = "Nevoit",
                            subtitle = "主要开发者",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://github.com/Nevodev".toUri(),
                                        )
                                    )
                                }
                            },
                        )
                        NormalDivider()
                        ItemRow(
                            iconRes = R.drawable.ic_pet_paw,
                            title = "WXRIW",
                            subtitle = "特别感谢",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://github.com/WXRIW".toUri(),
                                        )
                                    )
                                }
                            },
                        )
                        NormalDivider()
                        ItemRow(
                            iconRes = R.drawable.ic_pet_paw,
                            title = "Raspberry Monster",
                            subtitle = "特别感谢",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://github.com/raspberry-monster".toUri(),
                                        )
                                    )
                                }
                            },
                        )
                        NormalDivider()
                        ItemRow(
                            iconRes = R.drawable.ic_info,
                            title = "致谢",
                            onClick = {
                                isCreditsBottomSheetVisible = true
                            }
                        )
                    }
                    VGap()
                }
                item {
                    NavigationBarSpacer()
                }
                    }
                }
            }
        }
        if (isCreditsBottomSheetVisible) {
            CreditsBottomSheet(onDismissed = { isCreditsBottomSheetVisible = false })
        }
        if (isAdvancedBottomSheetVisible) {
            AdvancedBottomSheet(
                scale = scale,
                fps = fps,
                moruStyle = moruStyle,
                blurEnabled = blurEnabled,
                portrait = portrait,
                landscape = landscape,
                randomize = randomize,
                blockVideoPlayers = blockVideoPlayers,
                onPortraitChanged = {
                    portrait = it
                    settings.portraitPreset = it
                    state.setPortraitPreset(it)
                    PearWallRuntime.applySettings(context)
                },
                onLandscapeChanged = {
                    landscape = it
                    settings.landscapePreset = it
                    state.setLandscapePreset(it)
                    PearWallRuntime.applySettings(context)
                },
                onRandomizeChanged = {
                    randomize = it
                    settings.randomizeOnScreenOn = it
                },
                onBlockVideoPlayersChanged = {
                    blockVideoPlayers = it
                    settings.blockVideoPlayers = it
                    MediaArtworkService.requestRefresh(context)
                },
                onScaleChanged = {
                    scale = it
                    state.setRenderScale(it)
                    settings.renderScale = it
                },
                onFpsChanged = {
                    fps = it
                    state.setTargetFrameRate(it.roundToInt())
                    settings.frameRate = it.roundToInt()
                },
                onMoruChanged = {
                    moruStyle = it
                    settings.moruStyle = it
                    state.setMoruStyle(it)
                    PearWallRuntime.applySettings(context)
                },
                onBlurEnabledChanged = {
                    blurEnabled = it
                    settings.blurEnabled = it
                    state.setBlurEnabled(it)
                    PearWallRuntime.applySettings(context)
                },
                onDismissed = { isAdvancedBottomSheetVisible = false },
            )
        }
    }
}

private fun hasNotificationListenerAccess(context: android.content.Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

private fun hasAudioPermission(context: android.content.Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun isPearWallCurrentWallpaper(context: android.content.Context): Boolean {
    val wallpaperInfo = runCatching {
        WallpaperManager.getInstance(context).wallpaperInfo
    }.getOrNull() ?: return false
    return wallpaperInfo.packageName == context.packageName &&
            wallpaperInfo.serviceName == PearWallpaperService::class.java.name
}

private fun openNotificationListenerSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }.onFailure {
        Toast.makeText(context, "无法打开通知使用权设置", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun PrimaryButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            animationProgress.animateTo(1f, Springs.smooth(300, 0.0, 0.0001f))
        } else {
            animationProgress.animateTo(0f, Springs.smooth(300, 0.0, 0.0001f))
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
                alpha = 1f - animationProgress.value * 0.2f
                scaleX = 1f - animationProgress.value * 0.05f
                scaleY = 1f - animationProgress.value * 0.05f
            }
            .clip(Capsule())
            .background(Color.White)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            )
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.body.copy(fontWeight = FontWeight.W600),
            color = Color.Black.copy(.8f),
        )
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
    val directStarted = runCatching {
        context.startActivity(directIntent)
        true
    }.getOrElse { error ->
        android.util.Log.w("PearWall", "Direct live wallpaper preview failed", error)
        false
    }
    if (directStarted) return

    openWallpaperChooser(context)
}

private fun openWallpaperChooser(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
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
        openWallpaperChooser(context)
    }
}

@Composable
private fun SettingsCard(
    title: String? = null,
    useCachedClip: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        title?.let {
            Text(
                text = title,
                style = AppTheme.typography.subHeadline,
                color = Color.White,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .graphicsLayer {
                        alpha = 0.6f
                        blendMode = BlendMode.Plus
                    })
            VGap(8.dp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        blendMode = BlendMode.Plus
                    }
                    .thenIf(useCachedClip) {
                        cachedClip(RoundedRectangle(24.dp))
                    }
                    .thenIf(!useCachedClip) {
                        clip(RoundedRectangle(24.dp))
                    }
                    .background(color = Color.White.copy(.1f)))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .thenIf(useCachedClip) {
                        cachedClip(RoundedRectangle(24.dp))
                    }
                    .thenIf(!useCachedClip) {
                        clip(RoundedRectangle(24.dp))
                    }) {
                content()
            }
        }
    }
}

@Composable
private fun Choice(
    text: String,
    iconRes: Int,
    selected: Boolean,
    select: () -> Unit,
) {
    Row(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = select)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.White.copy(alpha = 0.6f),
        )
        Text(
            text = text,
            style = AppTheme.typography.body,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_checkmark),
                contentDescription = "已选择",
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ItemRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .thenIf(onClick != null) {
                clickable(onClick = onClick!!)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.White.copy(alpha = 0.6f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.body,
                color = Color.White.copy(alpha = 0.8f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTheme.typography.subHeadline,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_forward),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.White.copy(alpha = if (onClick != null) 0.5f else 0f),
        )
    }
}

private fun decodeArtwork(
    context: android.content.Context,
    uri: android.net.Uri
): android.graphics.Bitmap? {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val maxDimension = 4096
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension ||
        bounds.outHeight / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }
    val options = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, options)
    }
}

@Composable
private fun ImagePickerRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(start = 52.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "选择图片",
            style = AppTheme.typography.body,
            color = Color.White.copy(.5f),
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_forward),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.White.copy(.5f),
        )
    }
}

@Composable
private fun NormalDivider() {
    Spacer(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .padding(start = 52.dp)
            .height(1.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f))
    )
}

@Composable
private fun SwitchDivider() {
    Spacer(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .padding(start = 52.dp, end = 16.dp)
            .height(1.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f)),
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    extraBottomPadding: Dp = 0.dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .padding(bottom = extraBottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    blendMode = BlendMode.Plus
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = AppTheme.typography.subHeadline,
                color = Color.White.copy(.8f),
            )
            Text(
                text = valueLabel,
                style = AppTheme.typography.subHeadline,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        PearSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun PearSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit,
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = if (valueRange.endInclusive == valueRange.start) 0f else {
        (coercedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    }
    val currentValue by rememberUpdatedState(coercedValue)
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = coercedValue,
                    range = valueRange,
                    steps = steps,
                )
                setProgress { targetValue ->
                    onValueChange(snapSliderValue(targetValue, valueRange, steps))
                    onValueChangeFinished()
                    true
                }
            }
            .pointerInput(valueRange, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val trackRadius = 2.dp.toPx()
                    val trackEnd = (size.width.toFloat() - trackRadius).coerceAtLeast(trackRadius)
                    val usableWidth = (trackEnd - trackRadius).coerceAtLeast(1f)
                    val initialFraction = if (valueRange.endInclusive == valueRange.start) 0f else {
                        (currentValue - valueRange.start) /
                                (valueRange.endInclusive - valueRange.start)
                    }
                    val thumbCenter = Offset(
                        x = trackRadius + usableWidth * initialFraction,
                        y = size.height / 2f,
                    )
                    val thumbRadius = 11.dp.toPx()
                    if ((down.position - thumbCenter).getDistance() > thumbRadius) {
                        return@awaitEachGesture
                    }

                    down.consume()
                    val initialX = down.position.x
                    do {
                        val change = awaitPointerEvent().changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            val positionFraction = (
                                    initialFraction +
                                            (change.position.x - initialX) / usableWidth
                                    ).coerceIn(0f, 1f)
                            val rawValue = valueRange.start +
                                    positionFraction *
                                    (valueRange.endInclusive - valueRange.start)
                            onValueChange(snapSliderValue(rawValue, valueRange, steps))
                            change.consume()
                        }
                    } while (change.pressed)
                    onValueChangeFinished()
                }
            }
            .drawWithCache {
                val shadowContext = obtainShadowContext()
                val nearShadow = shadowContext.createDropShadowPainter(
                    CircleShape,
                    Shadow(
                        radius = 4.dp,
                        color = Color.Black,
                        offset = DpOffset(0.dp, 0.5.dp),
                        alpha = 0.12f,
                    ),
                )
                val farShadow = shadowContext.createDropShadowPainter(
                    CircleShape,
                    Shadow(
                        radius = 13.dp,
                        color = Color.Black,
                        offset = DpOffset(0.dp, 6.dp),
                        alpha = 0.12f,
                    ),
                )
                val trackHeight = 4.dp.toPx()
                val trackRadius = trackHeight / 2f
                val start = Offset(trackRadius, size.height / 2f)
                val end = Offset(size.width - trackRadius, size.height / 2f)
                val thumb = Offset(
                    x = start.x + (end.x - start.x) * fraction,
                    y = start.y,
                )
                val trackTop = start.y - trackRadius
                val trackCornerRadius = CornerRadius(trackRadius)
                val thumbDiameter = 22.dp.toPx()
                val thumbTopLeft = Offset(
                    x = thumb.x - thumbDiameter / 2f,
                    y = thumb.y - thumbDiameter / 2f,
                )
                val thumbSize = Size(thumbDiameter, thumbDiameter)

                onDrawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.2f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = trackCornerRadius,
                        blendMode = BlendMode.Plus
                    )
                    if (thumb.x > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = Offset(0f, trackTop),
                            size = Size(thumb.x, trackHeight),
                            cornerRadius = trackCornerRadius,
                            blendMode = BlendMode.Plus
                        )
                    }
                    if (steps > 0) {
                        val tickCount = steps + 2
                        val tickY = start.y + 20.dp.toPx()
                        repeat(tickCount) { index ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.2f),
                                radius = 2.dp.toPx(),
                                center = Offset(
                                    x = start.x +
                                            (end.x - start.x) * index / (tickCount - 1),
                                    y = tickY,
                                ),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
                    translate(thumbTopLeft.x, thumbTopLeft.y) {
                        with(farShadow) { draw(thumbSize) }
                        with(nearShadow) { draw(thumbSize) }
                    }
                    drawCircle(
                        color = Color.White,
                        radius = thumbDiameter / 2f,
                        center = thumb,
                    )
                }
            },
    )
}

@Composable
private fun TwoSideDivider() {
    Box(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.1f)),
    )
}

@Composable
private fun ToggleRow(
    text: String,
    subtitle: String? = null,
    iconRes: Int = R.drawable.ic_shuffle,
    checked: Boolean,
    change: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(56.dp)
            .clickable { change(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer {
                    blendMode = BlendMode.Plus
                }
                .size(24.dp),
            tint = Color.White.copy(alpha = 0.6f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .padding(vertical = if (subtitle != null) 12.dp else 0.dp)
                .graphicsLayer {
                    blendMode = BlendMode.Plus
                },
        ) {
            Text(
                text = text,
                style = AppTheme.typography.body,
                color = Color.White.copy(alpha = 0.8f)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTheme.typography.subHeadline,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = change,
        )
    }
}

@Composable
internal fun PresetSectionHeader(text: String, iconRes: Int) {
    Row(
        modifier = Modifier
            .graphicsLayer {
                blendMode = BlendMode.Plus
            }
            .nullClickable()
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.White.copy(alpha = 0.6f),
        )
        Text(
            text = text,
            style = AppTheme.typography.body,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun EffectOptionRow(
    label: String,
    iconRes: Int,
    options: List<String>,
    selected: Int,
    select: (Int) -> Unit,
) {
    Column {
        PresetSectionHeader(label, iconRes)
        PresetSegmentedControl(options, selected, select)
    }
}

@Composable
internal fun PresetSegmentedControl(count: Int, selected: Int, select: (Int) -> Unit) {
    PresetSegmentedControl(List(count) { "${it + 1}" }, selected, select)
}

@Composable
internal fun PresetSegmentedControl(options: List<String>, selected: Int, select: (Int) -> Unit) {
    val count = options.size
    val spacing = 4.dp
    val selectedIndex = selected.toFloat().coerceIn(0f, (count - 1).toFloat())
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex,
        animationSpec = Springs.smooth(
            durationMillis = 250,
            extraBounce = 0.1,
            visibilityThreshold = 0.0001f,
        ),
        label = "PresetSegmentedControlSelectedIndex",
    )
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .graphicsLayer {
                shape = Capsule()
                clip = true
                blendMode = BlendMode.Plus
            }
            .drawBehind {
                drawRect(
                    color = Color.White.copy(alpha = 0.1f)
                )
            }
            .padding(4.dp)
            .drawWithCache {
                val gap = spacing.toPx()
                val segmentWidth = (size.width - gap * (count - 1)) / count
                val indicatorOffset = (segmentWidth + gap) * animatedIndex
                val indicatorOutline = Capsule().createOutline(
                    size = Size(segmentWidth, size.height),
                    layoutDirection = layoutDirection,
                    density = this,
                )
                onDrawWithContent {
                    translate(left = indicatorOffset) {
                        drawOutline(
                            outline = indicatorOutline,
                            color = Color.Black
                        )
                    }
                    drawContent()
                }
            },
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(count) { index ->
            PresetSegment(
                text = options[index],
                selected = selected == index,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    select(index)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PresetSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(Capsule())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.body,
            color = if (selected) Color.White.copy(.8f) else Color.White.copy(.4f)
        )
    }
}

private fun LazyListState.behindLyricsProgress(
    distancePx: Float,
    leadingItemHeightPx: Float,
): Float {
    val scrollOffsetPx = when (firstVisibleItemIndex) {
        0 -> firstVisibleItemScrollOffset.toFloat()
        1 -> leadingItemHeightPx + firstVisibleItemScrollOffset
        else -> distancePx
    }
    return (1f - scrollOffsetPx / distancePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

private const val EXPORT_PNG_QUALITY = 95