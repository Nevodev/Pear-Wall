package com.nevoit.pearwall.page

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.shapes.RoundedRectangle
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.nevoit.pearwall.R
import com.nevoit.pearwall.core.component.BottomSheet
import com.nevoit.pearwall.core.component.Icon
import com.nevoit.pearwall.core.component.ListColors
import com.nevoit.pearwall.core.component.ListRowAccessory
import com.nevoit.pearwall.core.component.ListStack
import com.nevoit.pearwall.core.component.ModalTopBar
import com.nevoit.pearwall.core.component.Text
import com.nevoit.pearwall.core.component.VGap
import com.nevoit.pearwall.core.modifier.smoothGradient
import com.nevoit.pearwall.core.theme.AppTheme
import com.nevoit.pearwall.core.theme.LocalAppColors
import com.nevoit.pearwall.core.theme.tokens.Slate500

@Composable
fun CreditsBottomSheet(
    onDismissed: () -> Unit
) {
    val colors = LocalAppColors.current
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val uriHandler = LocalUriHandler.current
    val visible by listState.isScrolledPast(0.dp)

    val backgroundColor = AppTheme.colors.elevatedPageBackground
    val backdrop = rememberLayerBackdrop {
        drawRect(
            backgroundColor,
            size = Size(this.size.width * 3, this.size.height * 3),
            topLeft = Offset(-this.size.width, -this.size.height)
        )
        drawContent()
    }

    BottomSheet(
        onDismissed = onDismissed
    ) { slideOut ->
        ListStack(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .layerBackdrop(backdrop),
            colors = ListColors(
                background = colors.elevatedPageBackground,
                rowBackground = colors.elevatedCardBackground,
                headerText = colors.contentVariant,
                footerText = colors.contentVariant.copy(alpha = .3f)
            ),
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(bottom = navigationBarHeight)
        ) {
            item { VGap(72.dp) }
            item {
                ConfigInfoHeader(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = Slate500,
                    backgroundColor = AppTheme.colors.elevatedCardBackground,
                    icon = painterResource(R.drawable.ic_twotone_info),
                    title = "致谢",
                    info = "Pear Wall能够顺利运行，离不开这些优秀的开源库。"
                )
            }
            libraries?.let { libs ->
                Section(key = "libraries", topSpacing = 24.dp) {
                    libs.libraries.forEach { library ->
                        Row(
                            key = library.uniqueId,
                            onClick = {
                                val url = library.website ?: library.scm?.url
                                url?.let { uriHandler.openUri(it) }
                            }, accessory = ListRowAccessory.Chevron
                        ) {
                            LibraryItem(library = library)
                        }
                    }
                }
            }
            item { VGap() }
        }

        TopBar(visible = visible, backdrop = backdrop, onClose = slideOut)
    }
}

@Composable
fun LibraryItem(library: Library) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = library.name,
                style = AppTheme.typography.body,
                color = AppTheme.colors.content,
                modifier = Modifier.weight(1f)
            )
            library.artifactVersion?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.contentVariant.copy(.3f)
                )
            }
        }
        val developers = library.developers.joinToString(", ") { it.name ?: "" }
        if (developers.isNotEmpty()) {
            Text(
                text = developers,
                style = AppTheme.typography.body,
                color = AppTheme.colors.contentVariant
            )
        }
    }
}

@Composable
fun ConfigInfoHeader(
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    color: Color? = null,
    backgroundColor: Color,
    icon: Painter,
    title: String,
    info: String
) {
    Column(
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedRectangle(24.dp))
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(48.dp)
                .then(
                    if (brush != null) Modifier.background(
                        brush = brush,
                        shape = CircleShape
                    ) else if (color != null) Modifier.background(
                        color = color,
                        shape = CircleShape
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                tint = Color.White,
                contentDescription = null,
                modifier = Modifier
                    .graphicsLayer { blendMode = BlendMode.Plus }
                    .fillMaxSize()
            )
        }

        Text(
            text = title,
            style = AppTheme.typography.title3Emphasized,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Text(
            text = info,
            fontWeight = FontWeight.Normal,
            style = AppTheme.typography.subHeadline,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 20.dp)
                .fillMaxWidth()
                .alpha(.5f)
        )
    }
}

@Composable
private fun TopBar(
    backdrop: LayerBackdrop,
    visible: Boolean,
    onClose: () -> Unit
) {
    val surfaceColor = AppTheme.colors.elevatedPageBackground

    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
            }
            .fillMaxWidth()
            .then(
                if (supportsRuntimeShaderEffect()) Modifier.drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        blur(3f.dp.toPx())
                        runtimeShaderEffect(
                            "AlphaMask", """
uniform shader content;

uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
}""", "content"
                        ) {
                            apply {
                                setFloatUniform("size", size.width, size.height)
                                setColorUniform("tint", surfaceColor)
                                setFloatUniform("tintIntensity", 0.7f)
                            }
                        }
                    }
                ) else Modifier.smoothGradient(
                    color = surfaceColor,
                    start = 1f,
                    end = 0.5f,
                    intensity = 0.7f
                ))
            .padding(bottom = 32.dp + 48.dp)) {
    }
    ModalTopBar(
        title = "致谢",
        leading = {
            Action(
                icon = painterResource(id = R.drawable.ic_forward_nav),
                contentDescription = "返回",
                onClick = onClose
            )
        },
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp),
        showTitle = { visible }
    )
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun supportsRuntimeShaderEffect(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

@Composable
fun LazyListState.isScrolledPast(threshold: Dp): androidx.compose.runtime.State<Boolean> {
    val density = LocalDensity.current

    val thresholdPx = remember(threshold, density) {
        with(density) { threshold.toPx() }
    }

    return remember(this, thresholdPx) {
        derivedStateOf {
            firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > thresholdPx
        }
    }
}