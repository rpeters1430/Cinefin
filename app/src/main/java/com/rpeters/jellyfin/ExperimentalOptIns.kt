@file:Suppress("unused")

package com.rpeters.jellyfin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.annotation.ExperimentalCoilApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

/**
 * Centralized opt-in annotation for common experimental APIs used across the app.
 * Apply this to files/classes/functions instead of repeating long @OptIn lists.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FILE,
)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalComposeApi::class,
    ExperimentalUnitApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalCoilApi::class,
    ExperimentalCoroutinesApi::class,
    FlowPreview::class,
)
annotation class OptInAppExperimentalApis
