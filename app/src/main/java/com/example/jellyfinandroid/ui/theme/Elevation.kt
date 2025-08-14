package com.example.jellyfinandroid.ui.theme

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Elevation tokens following the new elevation system
 * Provides consistent elevation levels across components
 */
object ElevationTokens {
    
    // Material 3 elevation levels
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 3.dp
    val Level3: Dp = 6.dp
    val Level4: Dp = 8.dp
    val Level5: Dp = 12.dp
    
    // Component-specific elevations
    val Card: Dp = Level1
    val CardElevated: Dp = Level1
    val CardFilled: Dp = Level0
    val CardOutlined: Dp = Level0
    
    val Dialog: Dp = Level3
    val BottomSheet: Dp = Level1
    val NavigationBar: Dp = Level2
    val NavigationRail: Dp = Level0
    val TopAppBar: Dp = Level0
    val TopAppBarScrolled: Dp = Level2
    
    val Fab: Dp = Level3
    val FabPressed: Dp = Level1
    
    val Menu: Dp = Level2
    val Tooltip: Dp = Level2
    val Snackbar: Dp = Level3
}

/**
 * Convenient elevation helpers for cards
 */
object CardElevations {
    
    @Composable
    fun default(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = ElevationTokens.Card,
        pressedElevation = ElevationTokens.Level0,
        focusedElevation = ElevationTokens.Level1,
        hoveredElevation = ElevationTokens.Level2,
        draggedElevation = ElevationTokens.Level4,
        disabledElevation = ElevationTokens.Level0
    )
    
    @Composable
    fun elevated(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = ElevationTokens.CardElevated,
        pressedElevation = ElevationTokens.Level1,
        focusedElevation = ElevationTokens.Level1,
        hoveredElevation = ElevationTokens.Level2,
        draggedElevation = ElevationTokens.Level4,
        disabledElevation = ElevationTokens.Level0
    )
    
    @Composable
    fun filled(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = ElevationTokens.CardFilled
    )
}
