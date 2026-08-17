package com.nevoit.pearwall.core.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nevoit.pearwall.core.theme.BrandPosterTextStyle

@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        style = BrandPosterTextStyle,
        modifier = modifier
            .height(160.dp)
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Bottom)
            .padding(
                start = 24.dp,
                bottom = 12.dp,
                end = 24.dp
            ),
        textAlign = textAlign
    )
}