package com.nevoit.pearwall.core.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Modifier.smoothGradient(
    color: Color,
    start: Float,
    end: Float,
    intensity: Float
): Modifier = smoothGradientBrushMask(color, start, end, intensity)

@Composable
private fun Modifier.smoothGradientBrushMask(
    color: Color,
    start: Float,
    end: Float,
    intensity: Float
): Modifier {
    val colors = remember(color, start, end, intensity) {
        smoothStepGradientColors(color, start, end, intensity)
    }

    return this.drawWithCache {
        val brush = Brush.verticalGradient(
            colors = colors,
            startY = 0f,
            endY = size.height
        )

        onDrawBehind {
            drawRect(brush = brush)
        }
    }
}

private const val SmoothStepGradientSamples = 32

private fun smoothStepGradientColors(
    color: Color,
    start: Float,
    end: Float,
    intensity: Float
): List<Color> =
    List(SmoothStepGradientSamples + 1) { index ->
        val p = index / SmoothStepGradientSamples.toFloat()
        val mask = smoothStep(start, end, p)
        val alpha = (intensity * mask).coerceIn(0f, 1f)

        color.copy(alpha = alpha)
    }

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) {
        return if (x < edge0) 0f else 1f
    }

    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
