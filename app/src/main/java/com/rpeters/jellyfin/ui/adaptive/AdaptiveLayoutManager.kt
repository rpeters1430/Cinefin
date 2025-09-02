package com.rpeters.jellyfin.ui.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Device form factor detection and adaptive layout management
 */
enum class DeviceFormFactor {
    PHONE,
    TABLET,
    TV,
    DESKTOP,
    FOLDABLE,
}

/**
 * Screen orientation with TV-specific considerations
 */
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
    TV_LANDSCAPE, // Always landscape for TV, optimized for viewing distance
}

/**
 * Layout configuration for different screen sizes and form factors
 */
data class AdaptiveLayoutConfig(
    val formFactor: DeviceFormFactor,
    val orientation: ScreenOrientation,
    val windowSizeClass: WindowSizeClass,
    val isTV: Boolean,
    val isTablet: Boolean,
    val isFoldable: Boolean,
    val contentPadding: PaddingValues,
    val navigationSuiteType: NavigationSuiteType,
    val gridColumns: Int,
    val carouselItemWidth: Dp,
    val carouselItemHeight: Dp,
    val useLargeText: Boolean,
    val useCondensedLayout: Boolean,
) {

    /**
     * Determine if this layout supports TV-optimized focus navigation
     */
    val supportsFocusNavigation: Boolean
        get() = isTV || formFactor == DeviceFormFactor.DESKTOP

    /**
     * Determine if this layout should use TV-specific components
     */
    val useTvComponents: Boolean
        get() = isTV

    /**
     * Get the appropriate spacing for this layout
     */
    val spacing: Dp
        get() = when {
            isTV -> 32.dp
            isTablet -> 24.dp
            else -> 16.dp
        }

    /**
     * Get the appropriate header size for this layout
     */
    val headerPadding: PaddingValues
        get() = when {
            isTV -> PaddingValues(horizontal = 56.dp, vertical = 24.dp)
            isTablet -> PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            else -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        }
}

/**
 * Adaptive layout manager that detects device type and provides appropriate configurations
 */
@Composable
fun rememberAdaptiveLayoutConfig(windowSizeClass: WindowSizeClass): AdaptiveLayoutConfig {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    return remember(windowSizeClass, configuration.orientation) {
        createAdaptiveLayoutConfig(context, windowSizeClass, configuration)
    }
}

/**
 * Create adaptive layout configuration based on device characteristics
 */
private fun createAdaptiveLayoutConfig(
    context: Context,
    windowSizeClass: WindowSizeClass,
    configuration: Configuration,
): AdaptiveLayoutConfig {
    val isTV = detectTVDevice(context)
    val isTablet = detectTabletDevice(windowSizeClass, configuration)
    val isFoldable = detectFoldableDevice(context)

    val formFactor = determineFormFactor(isTV, isTablet, isFoldable, windowSizeClass)
    val orientation = determineOrientation(configuration, isTV)

    return AdaptiveLayoutConfig(
        formFactor = formFactor,
        orientation = orientation,
        windowSizeClass = windowSizeClass,
        isTV = isTV,
        isTablet = isTablet,
        isFoldable = isFoldable,
        contentPadding = calculateContentPadding(formFactor, isTV),
        navigationSuiteType = determineNavigationSuiteType(windowSizeClass, isTV),
        gridColumns = calculateGridColumns(windowSizeClass, isTV),
        carouselItemWidth = calculateCarouselItemWidth(formFactor, isTV),
        carouselItemHeight = calculateCarouselItemHeight(formFactor, isTV),
        useLargeText = isTV || (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded),
        useCondensedLayout = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact && !isTV,
    )
}

/**
 * Detect if the device is a TV/Android TV
 */
private fun detectTVDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Detect if the device is a tablet
 */
private fun detectTabletDevice(windowSizeClass: WindowSizeClass, configuration: Configuration): Boolean {
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
            // Additional checks for tablet characteristics
            val isLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
                Configuration.SCREENLAYOUT_SIZE_LARGE
            isLargeScreen || windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
        }
        else -> false
    }
}

/**
 * Detect if the device is foldable
 */
private fun detectFoldableDevice(context: Context): Boolean {
    // This is a simplified check - in production you'd use WindowManager APIs
    // or device-specific checks for foldable detection
    val display = context.resources.displayMetrics
    val aspectRatio = maxOf(display.widthPixels, display.heightPixels).toFloat() /
        minOf(display.widthPixels, display.heightPixels).toFloat()

    // Foldables often have unusual aspect ratios when unfolded
    return aspectRatio > 2.1f
}

/**
 * Determine the primary form factor
 */
private fun determineFormFactor(
    isTV: Boolean,
    isTablet: Boolean,
    isFoldable: Boolean,
    windowSizeClass: WindowSizeClass,
): DeviceFormFactor {
    return when {
        isTV -> DeviceFormFactor.TV
        isFoldable -> DeviceFormFactor.FOLDABLE
        isTablet -> DeviceFormFactor.TABLET
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> DeviceFormFactor.DESKTOP
        else -> DeviceFormFactor.PHONE
    }
}

/**
 * Determine screen orientation with TV considerations
 */
private fun determineOrientation(configuration: Configuration, isTV: Boolean): ScreenOrientation {
    return when {
        isTV -> ScreenOrientation.TV_LANDSCAPE
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> ScreenOrientation.LANDSCAPE
        else -> ScreenOrientation.PORTRAIT
    }
}

/**
 * Calculate content padding based on form factor
 */
private fun calculateContentPadding(formFactor: DeviceFormFactor, isTV: Boolean): PaddingValues {
    return when {
        isTV -> PaddingValues(horizontal = 48.dp, vertical = 24.dp)
        formFactor == DeviceFormFactor.TABLET -> PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        formFactor == DeviceFormFactor.DESKTOP -> PaddingValues(horizontal = 32.dp, vertical = 20.dp)
        else -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    }
}

/**
 * Determine the appropriate navigation suite type
 */
private fun determineNavigationSuiteType(windowSizeClass: WindowSizeClass, isTV: Boolean): NavigationSuiteType {
    return when {
        isTV -> NavigationSuiteType.None // TV uses custom navigation
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> NavigationSuiteType.NavigationDrawer
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }
}

/**
 * Calculate grid columns based on screen size and device type
 */
private fun calculateGridColumns(windowSizeClass: WindowSizeClass, isTV: Boolean): Int {
    return when {
        isTV -> when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 6
            else -> 4
        }
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> 4
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium -> 3
        else -> 2
    }
}

/**
 * Calculate carousel item width based on form factor
 */
private fun calculateCarouselItemWidth(formFactor: DeviceFormFactor, isTV: Boolean): Dp {
    return when {
        isTV -> 240.dp
        formFactor == DeviceFormFactor.TABLET -> 200.dp
        formFactor == DeviceFormFactor.DESKTOP -> 220.dp
        else -> 160.dp
    }
}

/**
 * Calculate carousel item height based on form factor
 */
private fun calculateCarouselItemHeight(formFactor: DeviceFormFactor, isTV: Boolean): Dp {
    return when {
        isTV -> 360.dp
        formFactor == DeviceFormFactor.TABLET -> 300.dp
        formFactor == DeviceFormFactor.DESKTOP -> 330.dp
        else -> 240.dp
    }
}

/**
 * Extension functions for window size class checks
 */
val WindowSizeClass.isCompact: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Compact

val WindowSizeClass.isMedium: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Medium

val WindowSizeClass.isExpanded: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded

val WindowSizeClass.isHeightCompact: Boolean
    get() = heightSizeClass == WindowHeightSizeClass.Compact

/**
 * Responsive breakpoint values
 */
object ResponsiveBreakpoints {
    const val COMPACT_WIDTH_DP = 600
    const val MEDIUM_WIDTH_DP = 840
    const val COMPACT_HEIGHT_DP = 480

    // TV-specific breakpoints
    const val TV_MIN_WIDTH_DP = 960
    const val TV_OPTIMAL_WIDTH_DP = 1920

    // Content sizing breakpoints
    const val MIN_ITEM_SIZE_DP = 120
    const val MAX_ITEM_SIZE_DP = 300
}
