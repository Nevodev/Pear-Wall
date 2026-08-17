package com.nevoit.pearwall.core.component

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.nevoit.pearwall.core.theme.AppTheme
import androidx.compose.animation.Animatable as ColorAnimatable
import androidx.compose.animation.core.Animatable as FloatAnimatable

@Composable
fun Switch(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    disabledAlpha: Float = 0.5f
) {
    SwitchImpl(
        enabled = enabled,
        interactionSource = interactionSource,
        checked = checked,
        onCheckedChange = onCheckedChange,
        disabledAlpha = disabledAlpha,
    )
}

@Composable
internal fun SwitchImpl(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    disabledAlpha: Float,
) {
    val haptic = LocalHapticFeedback.current
    val colors = AppTheme.colors
    val currentOnCheckedChange = rememberUpdatedState(onCheckedChange)

    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val targetTrackColor = when {
        !enabled && checked -> colors.activeTrack.copy(alpha = colors.activeTrack.alpha * disabledAlpha)
        !enabled -> colors.inactiveTrack.copy(alpha = colors.inactiveTrack.alpha * disabledAlpha)
        checked -> colors.activeTrack
        else -> colors.inactiveTrack
    }

    val targetThumbColor = when {
        !enabled && checked -> colors.activeThumb.copy(alpha = colors.activeThumb.alpha * disabledAlpha)
        !enabled -> colors.inactiveThumb.copy(alpha = colors.inactiveThumb.alpha * disabledAlpha)
        checked -> colors.activeThumb
        else -> colors.inactiveThumb
    }

    val targetOverlayColor = when {
        enabled && hovered && !pressed -> Color.White.copy(alpha = 0.2f)
        else -> Color.White.copy(alpha = 0f)
    }

    val thumbProgress = remember {
        FloatAnimatable(if (checked) 1f else 0f)
    }
    val trackColor = remember {
        ColorAnimatable(targetTrackColor)
    }
    val thumbColor = remember {
        ColorAnimatable(targetThumbColor)
    }
    val overlayColor = remember {
        ColorAnimatable(targetOverlayColor)
    }

    LaunchedEffect(checked) {
        thumbProgress.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = 500f,
            ),
        )
    }

    LaunchedEffect(targetTrackColor) {
        trackColor.animateTo(targetTrackColor, tween(durationMillis = 200))
    }

    LaunchedEffect(targetThumbColor) {
        thumbColor.animateTo(targetThumbColor, tween(durationMillis = 200))
    }

    LaunchedEffect(targetOverlayColor) {
        overlayColor.animateTo(targetOverlayColor, tween(durationMillis = 200))
    }

    val capsule = remember { Capsule() }

    Box(
        modifier = Modifier
            .width(46.dp)
            .height(28.dp)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                currentOnCheckedChange.value(!checked)
            }
            .drawWithCache {
                val thumbRadius = 11.dp.toPx()
                val startPadding = 3.dp.toPx()
                val moveDistance = 18.dp.toPx()
                val leftX = startPadding + thumbRadius

                val trackOutline = capsule.createOutline(
                    size = size,
                    layoutDirection = layoutDirection,
                    density = this,
                )
                val shadowPainter = obtainShadowContext().createDropShadowPainter(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 4.dp,
                        color = Color.Black.copy(alpha = 0.16f),
                        offset = DpOffset(0.dp, 2.dp),
                    ),
                )
                val thumbSize = Size(thumbRadius * 2, thumbRadius * 2)

                onDrawBehind {
                    drawOutline(trackOutline, color = trackColor.value)
                    drawOutline(trackOutline, color = overlayColor.value)

                    val thumbLeft = leftX + moveDistance * thumbProgress.value - thumbRadius
                    translate(left = thumbLeft, top = startPadding) {
                        with(shadowPainter) {
                            draw(thumbSize)
                        }
                        drawCircle(
                            color = thumbColor.value,
                            radius = thumbRadius,
                            center = Offset(thumbRadius, thumbRadius),
                        )
                    }
                }
            },
    )
}
