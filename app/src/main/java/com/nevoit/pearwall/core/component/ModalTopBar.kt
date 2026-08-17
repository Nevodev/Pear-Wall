package com.nevoit.pearwall.core.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import com.kyant.shapes.Capsule
import com.nevoit.pearwall.core.animation.Springs
import com.nevoit.pearwall.core.theme.AppTheme
import com.nevoit.pearwall.core.theme.LocalContentColor

interface ModalTopBarScope {
    @Composable
    fun Action(
        modifier: Modifier = Modifier,
        icon: Painter,
        iconSize: Dp = 28.dp,
        contentDescription: String?,
        onClick: () -> Unit,
        enabled: Boolean = true,
        shape: Shape = CircleShape,
        colors: GlasenseButtonColors = AppButtonColors.action()
    )
}

private object ModalTopBarScopeImpl : ModalTopBarScope {
    @Composable
    override fun Action(
        modifier: Modifier,
        icon: Painter,
        iconSize: Dp,
        contentDescription: String?,
        onClick: () -> Unit,
        enabled: Boolean,
        shape: Shape,
        colors: GlasenseButtonColors
    ) {
        GlasenseButton(
            enabled = enabled,
            shape = shape,
            onClick = onClick,
            modifier = modifier.size(48.dp),
            colors = colors
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun ModalTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    showTitle: () -> Boolean = { true },
    leading: (@Composable ModalTopBarScope.() -> Unit)? = null,
    trailing: (@Composable ModalTopBarScope.() -> Unit)? = null
) {
    val isVisible = showTitle()
    val textAlpha = animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(if (isVisible) 300 else 200)
    )
    val scale = animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = Springs.smooth(if (isVisible) 300 else 400)
    )

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        content = {
            if (leading != null) {
                Box(modifier = Modifier.layoutId("leading")) { ModalTopBarScopeImpl.leading() }
            }
            if (trailing != null) {
                Box(modifier = Modifier.layoutId("trailing")) { ModalTopBarScopeImpl.trailing() }
            }

            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier
                        .layoutId("title")
                        .graphicsLayer {
                            this.scaleX = scale.value
                            this.scaleY = scale.value
                            this.alpha = textAlpha.value
                        },
                    color = LocalContentColor.current,
                    style = AppTheme.typography.headline,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) { measurables, constraints ->
        val sideConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = constraints.maxWidth / 2
        )

        val leadingPlaceable =
            measurables.find { it.layoutId == "leading" }?.measure(sideConstraints)
        val trailingPlaceable =
            measurables.find { it.layoutId == "trailing" }?.measure(sideConstraints)

        val leadingWidth = leadingPlaceable?.width ?: 0
        val trailingWidth = trailingPlaceable?.width ?: 0

        val safePaddingPx = 12.dp.roundToPx()
        val maxSideWidth = maxOf(leadingWidth, trailingWidth)
        val sidePadding = if (maxSideWidth > 0) maxSideWidth + safePaddingPx else 24.dp.roundToPx()

        val titleMaxWidth = (constraints.maxWidth - sidePadding * 2).fastCoerceAtLeast(0)

        val titlePlaceable = measurables.find { it.layoutId == "title" }?.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = titleMaxWidth,
                minHeight = 0
            )
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            fun centerVertically(childHeight: Int) = (constraints.maxHeight - childHeight) / 2

            leadingPlaceable?.placeRelative(
                x = 0,
                y = centerVertically(leadingPlaceable.height)
            )

            trailingPlaceable?.placeRelative(
                x = constraints.maxWidth - trailingWidth,
                y = centerVertically(trailingPlaceable.height)
            )

            titlePlaceable?.placeRelative(
                x = (constraints.maxWidth - titlePlaceable.width) / 2,
                y = centerVertically(titlePlaceable.height)
            )
        }
    }
}

@Composable
fun GlasenseButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Capsule(),
    onClick: () -> Unit,
    colors: GlasenseButtonColors,
    animated: Boolean = true,
    content: @Composable () -> Unit,
) {
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    val backgroundColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val interactionSource = remember { MutableInteractionSource() }

    // Animate scale and alpha for press feedback.
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.2f else 1.0f,
        animationSpec = spring(0.5f, 300f, 0.0001f)
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.2f else 0f,
        animationSpec = spring(0.5f, 300f, 0.001f)
    )
    Box(
        modifier = modifier
            // Apply scale animation for press effect.
            .then(
                if (animated) Modifier.graphicsLayer {
                    scaleY = scale
                    scaleX = scale
                    transformOrigin = TransformOrigin.Center
                } else Modifier
            )
            .then(if (animated) Modifier.clip(shape) else Modifier)
            // Handle click events.
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = { onClick() },
                indication = null,
                role = Role.Button
            )
            .height(48.dp)
            .background(color = backgroundColor, shape = shape)
            // Draw a white flash overlay on press.
            .then(
                if (animated) {
                    Modifier.drawBehind {
                        drawRect(
                            size = this.size,
                            color = Color.White,
                            alpha = alpha,
                            blendMode = BlendMode.Plus
                        )
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}

@Immutable
data class GlasenseButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
)

object AppButtonColors {
    @Composable
    fun action() = GlasenseButtonColors(
        containerColor = AppTheme.colors.scrimNormal,
        contentColor = AppTheme.colors.primary,
        disabledContainerColor = AppTheme.colors.scrimNormal.copy(0.5f),
        disabledContentColor = AppTheme.colors.primary.copy(0.5f)
    )

}