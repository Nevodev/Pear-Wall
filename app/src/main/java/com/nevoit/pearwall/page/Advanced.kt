package com.nevoit.pearwall.page

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.nevoit.pearwall.R
import com.nevoit.pearwall.core.animation.Springs
import com.nevoit.pearwall.core.component.BottomSheet
import com.nevoit.pearwall.core.component.Icon
import com.nevoit.pearwall.core.component.ListColors
import com.nevoit.pearwall.core.component.ListStack
import com.nevoit.pearwall.core.component.Text
import com.nevoit.pearwall.core.component.VGap
import com.nevoit.pearwall.core.modifier.cachedClip
import com.nevoit.pearwall.core.theme.AppTheme
import com.nevoit.pearwall.core.theme.LocalAppColors
import com.nevoit.pearwall.pearmesh.MoruStyle
import com.nevoit.pearwall.pearmesh.PearMeshState
import kotlin.math.roundToInt


@Composable
fun AdvancedBottomSheet(
    scale: Float,
    fps: Float,
    moruStyle: MoruStyle,
    blurEnabled: Boolean,
    portrait: Int,
    landscape: Int,
    randomize: Boolean,
    blockVideoPlayers: Boolean,
    onPortraitChanged: (Int) -> Unit,
    onLandscapeChanged: (Int) -> Unit,
    onRandomizeChanged: (Boolean) -> Unit,
    onBlockVideoPlayersChanged: (Boolean) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onFpsChanged: (Float) -> Unit,
    onMoruChanged: (MoruStyle) -> Unit,
    onBlurEnabledChanged: (Boolean) -> Unit,
    onDismissed: () -> Unit,
) {
    val listState = rememberLazyListState()
    val colors = LocalAppColors.current
    val backgroundColor = AppTheme.colors.elevatedPageBackground
    val backdrop = rememberLayerBackdrop {
        drawRect(
            backgroundColor,
            size = Size(this.size.width * 3, this.size.height * 3),
            topLeft = Offset(-this.size.width, -this.size.height)
        )
        drawContent()
    }

    BottomSheet(onDismissed = onDismissed) { slideOut ->
        ListStack(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .layerBackdrop(backdrop),
            colors = ListColors(
                background = AppTheme.colors.elevatedPageBackground,
                rowBackground = AppTheme.colors.elevatedCardBackground,
                headerText = colors.contentVariant,
                footerText = colors.contentVariant.copy(alpha = .3f),
            ),
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
        ) {
            item { VGap(72.dp) }
            NoPaddingSection(header = { "常规" }, key = "general") {
                PaddedSwitchRow(
                    separator = false,
                    checked = blockVideoPlayers,
                    onCheckedChange = onBlockVideoPlayersChanged,
                    horizontalPadding = 16.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shuffle),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp),
                            tint = colors.primary,
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .padding(vertical = 12.dp)
                        ) {
                            Text("屏蔽视频播放器")
                            Text(
                                "忽略内置黑名单中的媒体通知和封面",
                                style = AppTheme.typography.subHeadline,
                                color = colors.contentVariant,
                            )
                        }
                    }
                }
            }
            NoPaddingSection(header = { "画面效果" }, key = "moru") {
                PaddedSwitchRow(
                    separator = false,
                    checked = blurEnabled,
                    onCheckedChange = onBlurEnabledChanged,
                    horizontalPadding = 16.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_blur),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = colors.primary,
                        )
                        Text("模糊", modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Row(separator = false) {
                    Column {
                        SwitchDivider()
                        SegmentedControlHeader(
                            text = "长虹玻璃",
                            iconRes = R.drawable.ic_moruglass
                        )
                        val moruValues = listOf(
                            MoruStyle.OFF,
                            MoruStyle.NARROW,
                            MoruStyle.WIDE,
                            MoruStyle.SMOOTH,
                        )
                        SegmentedControl(
                            items = listOf("关闭", "窄", "宽", "平滑"),
                            selected = moruValues.indexOf(moruStyle).coerceAtLeast(0),
                            onSelected = { index -> onMoruChanged(moruValues[index]) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        )
                    }
                }
            }
            NoPaddingSection(header = { "预设方案" }, key = "presets") {
                PaddedSwitchRow(
                    separator = false,
                    checked = randomize,
                    onCheckedChange = onRandomizeChanged,
                    horizontalPadding = 16.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shuffle),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp),
                            tint = colors.primary,
                        )
                        Text("亮屏时随机切换", modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Row(separator = false) {
                    Column {
                        SwitchDivider()
                        SegmentedControlHeader(
                            text = "竖屏方案",
                            iconRes = R.drawable.ic_phone_vertical,
                        )
                        SegmentedControl(
                            items = List(PearMeshState.PortraitPresetCount) { "${it + 1}" },
                            selected = portrait,
                            onSelected = onPortraitChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        )
                        TwoSideDivider()
                    }
                }
                Row(separator = false) {
                    Column {
                        SegmentedControlHeader(
                            text = "横屏方案",
                            iconRes = R.drawable.ic_phone_horizontal,
                        )
                        SegmentedControl(
                            items = List(PearMeshState.LandscapePresetCount) { "${it + 1}" },
                            selected = landscape,
                            onSelected = onLandscapeChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        )
                    }
                }
            }
            NoPaddingSection(header = { "渲染" }, key = "render") {
                Row(separator = false) {
                    Column {
                        AdvancedSliderRow(
                            label = "渲染倍率",
                            valueLabel = "${(scale * 100).roundToInt()}%",
                            value = scale,
                            valueRange = .1f..1f,
                            onValueChange = onScaleChanged,
                            onValueChangeFinished = {},
                            extraBottomPadding = 12.dp,
                        )
                        TwoSideDivider()
                    }
                }
                Row(separator = false) {
                    AdvancedSliderRow(
                        label = "帧数",
                        valueLabel = "${fps.roundToInt()} FPS",
                        value = fps,
                        valueRange = 10f..60f,
                        steps = 4,
                        onValueChange = onFpsChanged,
                        onValueChangeFinished = {},
                        extraBottomPadding = 12.dp,
                    )
                }
            }
            item { VGap() }
        }
        TopBar(title = "高级", visible = true, backdrop = backdrop, onClose = slideOut)
    }
}

@Composable
fun Segment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    val fontColor =
        if (selected) colors.onSegmentedControlIndicator else colors.onSegmentedControlBackground

    val hapticController = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(Capsule())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    hapticController.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTheme.typography.body,
            color = fontColor
        )
    }
}

@Composable
fun SegmentedControl(
    modifier: Modifier = Modifier,
    items: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    val segmentSpacing = 4.dp
    val selectedIndex = selected.toFloat()
    val animatedSelectedIndex by animateFloatAsState(
        targetValue = selectedIndex,
        animationSpec = Springs.smooth(
            durationMillis = 250,
            extraBounce = 0.1,
            visibilityThreshold = 0.0001f
        ),
        label = "CustomReminderSegmentedControlSelectedIndex"
    )

    val indicatorColor = colors.segmentedControlIndicator

    Row(
        modifier = modifier
            .fillMaxWidth()
            .cachedClip(Capsule())
            .background(colors.segmentedControlBackground)
            .padding(4.dp)
            .drawWithContent {
                val shadowColor = Color.Black.copy(alpha = 0.08f)
                val spacing = segmentSpacing.toPx()
                val indicatorWidth = (size.width - spacing * items.size.minus(1)) / items.size
                val indicatorOffset = (indicatorWidth + spacing) * animatedSelectedIndex
                val shadowRadius = 8.dp.toPx()
                val shadowOffsetY = 4.dp.toPx()
                val outline = Capsule().createOutline(
                    size = Size(indicatorWidth, size.height),
                    layoutDirection = layoutDirection,
                    density = this
                )

                withTransform({ translate(left = indicatorOffset) }) {
                    withTransform({ translate(top = shadowOffsetY) }) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = Paint().nativePaint.apply {
                                isAntiAlias = true
                                maskFilter =
                                    BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
                                color = shadowColor.toArgb()
                            }
                            when (outline) {
                                is Outline.Rectangle -> {
                                    drawContext.canvas.nativeCanvas.drawRect(
                                        outline.rect.left,
                                        outline.rect.top,
                                        outline.rect.right,
                                        outline.rect.bottom,
                                        paint
                                    )
                                }

                                is Outline.Rounded -> {
                                    drawContext.canvas.nativeCanvas.drawRoundRect(
                                        outline.roundRect.left,
                                        outline.roundRect.top,
                                        outline.roundRect.right,
                                        outline.roundRect.bottom,
                                        outline.roundRect.bottomLeftCornerRadius.x,
                                        outline.roundRect.bottomLeftCornerRadius.y,
                                        paint
                                    )
                                }

                                is Outline.Generic -> {
                                    drawContext.canvas.nativeCanvas.drawPath(
                                        outline.path.asAndroidPath(),
                                        paint
                                    )
                                }
                            }
                        }
                    }
                    drawOutline(outline, indicatorColor)
                }
                drawContent()
            },
        horizontalArrangement = Arrangement.spacedBy(segmentSpacing)
    ) {
        items.forEachIndexed { index, item ->
            Segment(
                text = item,
                selected = index == selected,
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SegmentedControlHeader(text: String, iconRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = AppTheme.colors.primary,
        )
        Text(
            text = text,
            style = AppTheme.typography.body,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun Slider(
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
    val colors = AppTheme.colors

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
                        color = colors.scrimMedium,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = trackCornerRadius
                    )
                    if (thumb.x > 0f) {
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset(0f, trackTop),
                            size = Size(thumb.x, trackHeight),
                            cornerRadius = trackCornerRadius
                        )
                    }
                    if (steps > 0) {
                        val tickCount = steps + 2
                        val tickY = start.y + 20.dp.toPx()
                        repeat(tickCount) { index ->
                            drawCircle(
                                color = colors.scrimMedium,
                                radius = 2.dp.toPx(),
                                center = Offset(
                                    x = start.x +
                                            (end.x - start.x) * index / (tickCount - 1),
                                    y = tickY,
                                )
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

fun snapSliderValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    if (steps <= 0) return coercedValue
    val intervalCount = steps + 1
    val interval = (valueRange.endInclusive - valueRange.start) / intervalCount
    return valueRange.start +
            ((coercedValue - valueRange.start) / interval).roundToInt() * interval
}

@Composable
fun AdvancedSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    extraBottomPadding: Dp = 0.dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .padding(bottom = extraBottomPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = AppTheme.typography.subHeadline,
                color = colors.content,
            )
            Text(
                text = valueLabel,
                style = AppTheme.typography.subHeadline,
                color = colors.contentVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun TwoSideDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(AppTheme.colors.scrimMedium),
    )
}

@Composable
private fun SwitchDivider() {
    Spacer(
        modifier = Modifier
            .padding(start = 52.dp, end = 16.dp)
            .height(1.dp)
            .fillMaxWidth()
            .background(AppTheme.colors.scrimMedium),
    )
}