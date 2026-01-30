package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
fun MaterialText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    autoSize: Boolean = false,
    minFontSize: TextUnit = 12.sp,
    maxFontSize: TextUnit = TextUnit.Unspecified,
) {
    if (!autoSize) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            minLines = minLines,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val resolvedMaxFontSize = when {
            maxFontSize != TextUnit.Unspecified -> maxFontSize
            style.fontSize != TextUnit.Unspecified -> style.fontSize
            else -> 18.sp
        }
        val targetFontSize = remember(
            text,
            constraints.maxWidth,
            style,
            minLines,
            maxLines,
            minFontSize,
            resolvedMaxFontSize,
            overflow,
            density.fontScale,
        ) {
            val minPx = with(density) { minFontSize.toPx() }
            val maxPx = with(density) { resolvedMaxFontSize.toPx() }
            var low = min(minPx, maxPx)
            var high = max(minPx, maxPx)
            var best = low
            repeat(8) {
                val mid = (low + high) / 2f
                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = style.copy(fontSize = with(density) { mid.toSp() }),
                    maxLines = maxLines,
                    overflow = overflow,
                    constraints = Constraints(maxWidth = constraints.maxWidth),
                )
                val fits = !layoutResult.didOverflowWidth && !layoutResult.didOverflowHeight
                if (fits) {
                    best = mid
                    low = mid + 0.5f
                } else {
                    high = mid - 0.5f
                }
            }
            with(density) { best.toSp() }
        }

        Text(
            text = text,
            style = style.copy(fontSize = targetFontSize),
            color = color,
            minLines = minLines,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
