package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ALPHABET = ("#ABCDEFGHIJKLMNOPQRSTUVWXYZ").map { it.toString() }

/**
 * A fast-scroll index scroller that displays A-Z on the right side of the screen.
 * Allows users to jump to sections by dragging or tapping.
 */
@Composable
fun AlphabetScroller(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val haptic = LocalHapticFeedback.current
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var containerHeight by remember { mutableStateOf(0) }

    fun updateSelection(y: Float) {
        if (containerHeight <= 0) return
        
        val sectionHeight = containerHeight.toFloat() / ALPHABET.size
        val index = (y / sectionHeight).toInt().coerceIn(0, ALPHABET.size - 1)
        val letter = ALPHABET[index]
        
        if (selectedLetter != letter) {
            selectedLetter = letter
            onLetterSelected(letter)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp) // Large touch target width
            .onGloballyPositioned { containerHeight = it.size.height }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        updateSelection(offset.y)
                        try {
                            awaitRelease()
                        } finally {
                            selectedLetter = null
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> updateSelection(offset.y) },
                    onDragEnd = { selectedLetter = null },
                    onDragCancel = { selectedLetter = null },
                    onDrag = { change, _ ->
                        updateSelection(change.position.y)
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        // The alphabet column
        Column(
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            ALPHABET.forEach { letter ->
                val isSelected = selectedLetter == letter
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Indicator Popup
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 60.dp), // Show to the left of the scroller
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = selectedLetter != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(activeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedLetter ?: "",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
