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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

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
 * Posture hints derived from WindowLayoutInfo
 */
sealed class DevicePosture {
    data object Normal : DevicePosture()
    data object TableTop : DevicePosture()
    data object Book : DevicePosture()
    data class Separating(val orientation: FoldingFeature.Orientation) : DevicePosture()
    data object Flat : DevicePosture()
}

/**
 * Visibility behaviour for secondary/detail panes
 */
enum class DetailPaneVisibility {
    NONE,
    CONTEXTUAL,
    DUAL_PANE,
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
    val posture: DevicePosture,
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
        get() = isTV || formFactor == DeviceFormFactor.DESKTOP || isTablet || isFoldable

    /**
     * Determine if this layout should use TV-specific components
     */
    val useTvComponents: Boolean
        get() = isTV

    /**
     * Determine if landscape spacing rules should be preferred
     */
    val isLandscapeFirst: Boolean
        get() = orientation != ScreenOrientation.PORTRAIT || posture != DevicePosture.Normal

    /**
     * Get the appropriate spacing for this layout
     */
    val spacing: Dp
        get() = when {
            isTV -> if (orientation == ScreenOrientation.TV_LANDSCAPE) 32.dp else 28.dp
            isLandscapeFirst && (isTablet || isFoldable) -> 24.dp
            isTablet || isFoldable -> 20.dp
            else -> 16.dp
        }

    /**
     * Get the appropriate header size for this layout
     */
    val headerPadding: PaddingValues
        get() = when {
            isTV -> if (orientation == ScreenOrientation.TV_LANDSCAPE) {
                PaddingValues(horizontal = 64.dp, vertical = 28.dp)
            } else {
                PaddingValues(horizontal = 56.dp, vertical = 24.dp)
            }
            isLandscapeFirst && (isTablet || isFoldable) -> PaddingValues(horizontal = 40.dp, vertical = 20.dp)
            isTablet || isFoldable -> PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            else -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        }

    /**
     * Maximum rows that should be visible at a time for carousels or grids
     */
    val maxRows: Int
        get() = when {
            isTV && windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> 4
            isTV -> 3
            isLandscapeFirst -> 3
            else -> 2
        }

    /**
     * Determine how the detail pane should behave
     */
    val detailPaneVisibility: DetailPaneVisibility
        get() = when {
            isTV -> DetailPaneVisibility.NONE
            isFoldable && posture is DevicePosture.Separating -> DetailPaneVisibility.DUAL_PANE
            isFoldable && posture is DevicePosture.Book -> DetailPaneVisibility.DUAL_PANE
            isTablet && windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> DetailPaneVisibility.DUAL_PANE
            isLandscapeFirst && (isTablet || isFoldable) -> DetailPaneVisibility.CONTEXTUAL
            isTablet || isFoldable -> DetailPaneVisibility.CONTEXTUAL
            else -> DetailPaneVisibility.NONE
        }

    /**
     * Convenience flag for dual-pane experiences
     */
    val shouldShowDualPane: Boolean
        get() = detailPaneVisibility == DetailPaneVisibility.DUAL_PANE

    /**
     * Section spacing between major content sections
     */
    val sectionSpacing: Dp
        get() = spacing

    /**
     * Hero carousel height
     */
    val heroHeight: Dp
        get() = when {
            isTV -> if (orientation == ScreenOrientation.TV_LANDSCAPE) 540.dp else 480.dp
            isTablet || isFoldable -> if (isLandscapeFirst) 520.dp else 480.dp
            else -> 400.dp
        }

    /**
     * Hero carousel horizontal padding
     */
    val heroHorizontalPadding: Dp
        get() = when {
            isTV -> 32.dp
            isTablet || isFoldable -> 16.dp
            windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact -> 12.dp
            else -> 16.dp
        }

    /**
     * Space between hero carousel pages
     */
    val heroPageSpacing: Dp
        get() = when {
            isTV -> 16.dp
            isTablet || isFoldable -> 12.dp
            else -> 8.dp
        }

    /**
     * Featured items limit for hero carousel
     */
    val featuredItemsLimit: Int
        get() = when {
            isTV -> 12
            isTablet || isFoldable -> 10
            else -> 6
        }

    /**
     * Row item limit for horizontal scrolling rows
     */
    val rowItemLimit: Int
        get() = when {
            isTV -> 20
            isTablet || isFoldable -> 15
            else -> 12
        }

    /**
     * Continue watching section item limit
     */
    val continueWatchingLimit: Int
        get() = when {
            isTV -> 12
            isTablet || isFoldable -> 8
            else -> 6
        }

    /**
     * Continue watching card width
     */
    val continueWatchingCardWidth: Dp
        get() = when {
            isTV -> 240.dp
            isTablet || isFoldable -> if (isLandscapeFirst) 200.dp else 180.dp
            windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact -> {
                // Check for ultra-compact phones
                if (carouselItemWidth < 150.dp) 138.dp else 160.dp
            }
            else -> 180.dp
        }

    /**
     * Poster card width for vertical poster cards
     */
    val posterCardWidth: Dp
        get() = when {
            isTV -> 220.dp
            isTablet || isFoldable -> if (isLandscapeFirst) 180.dp else 160.dp
            windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact -> {
                // Check for ultra-compact phones
                if (carouselItemWidth < 150.dp) 132.dp else 144.dp
            }
            else -> 160.dp
        }

    /**
     * Media card width for horizontal backdrop cards
     */
    val mediaCardWidth: Dp
        get() = when {
            isTV -> 360.dp
            isTablet || isFoldable -> if (isLandscapeFirst) 320.dp else 300.dp
            else -> 260.dp
        }
}

/**
 * Convenience helper to remember WindowLayoutInfo for adaptive decisions
 */
@Composable
fun rememberWindowLayoutInfo(): WindowLayoutInfo {
    val context = LocalContext.current
    val windowInfoTracker = remember { WindowInfoTracker.getOrCreate(context) }
    val windowLayoutInfoState = windowInfoTracker
        .windowLayoutInfo(context)
        .collectAsState(initial = WindowLayoutInfo(emptyList()))
    return windowLayoutInfoState.value
}

/**
 * Adaptive layout manager that detects device type and provides appropriate configurations
 */
@Composable
fun rememberAdaptiveLayoutConfig(windowSizeClass: WindowSizeClass): AdaptiveLayoutConfig {
    val windowLayoutInfo = rememberWindowLayoutInfo()
    return rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)
}

/**
 * Adaptive layout manager that accepts an explicit WindowLayoutInfo
 */
@Composable
fun rememberAdaptiveLayoutConfig(
    windowSizeClass: WindowSizeClass,
    windowLayoutInfo: WindowLayoutInfo,
): AdaptiveLayoutConfig {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    return remember(windowSizeClass, configuration.orientation, windowLayoutInfo) {
        createAdaptiveLayoutConfig(context, windowSizeClass, configuration, windowLayoutInfo)
    }
}

/**
 * Create adaptive layout configuration based on device characteristics
 */
private fun createAdaptiveLayoutConfig(
    context: Context,
    windowSizeClass: WindowSizeClass,
    configuration: Configuration,
    windowLayoutInfo: WindowLayoutInfo,
): AdaptiveLayoutConfig {
    val posture = deriveDevicePosture(windowLayoutInfo)
    val isTV = detectTVDevice(context)
    val orientation = determineOrientation(configuration, isTV, posture)
    val isTablet = detectTabletDevice(windowSizeClass, configuration, posture, orientation)
    val isFoldable = detectFoldableDevice(windowLayoutInfo, context)
    val formFactor = determineFormFactor(isTV, isTablet, isFoldable, windowSizeClass, posture)

    return AdaptiveLayoutConfig(
        formFactor = formFactor,
        orientation = orientation,
        windowSizeClass = windowSizeClass,
        isTV = isTV,
        isTablet = isTablet,
        isFoldable = isFoldable,
        posture = posture,
        contentPadding = calculateContentPadding(formFactor, orientation, posture, isTV),
        navigationSuiteType = determineNavigationSuiteType(windowSizeClass, isTV),
        gridColumns = calculateGridColumns(windowSizeClass, isTV, posture, orientation, formFactor),
        carouselItemWidth = calculateCarouselItemWidth(formFactor, isTV, orientation),
        carouselItemHeight = calculateCarouselItemHeight(formFactor, isTV, orientation),
        useLargeText = isTV || (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded),
        useCondensedLayout = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact &&
            !isTV && posture != DevicePosture.TableTop,
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
 * Detect if the device is a tablet, considering posture hints
 */
private fun detectTabletDevice(
    windowSizeClass: WindowSizeClass,
    configuration: Configuration,
    posture: DevicePosture,
    orientation: ScreenOrientation,
): Boolean {
    val baseTablet = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
            val isLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
                Configuration.SCREENLAYOUT_SIZE_LARGE
            isLargeScreen || windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
        }
        else -> false
    }

    val postureSuggestsTablet = posture is DevicePosture.Book || posture is DevicePosture.Separating
    val landscapeTableTop = posture is DevicePosture.TableTop && orientation != ScreenOrientation.PORTRAIT

    return baseTablet || postureSuggestsTablet || landscapeTableTop
}

/**
 * Detect if the device is foldable based on WindowLayoutInfo
 */
private fun detectFoldableDevice(windowLayoutInfo: WindowLayoutInfo, context: Context): Boolean {
    if (windowLayoutInfo.displayFeatures.any { it is FoldingFeature }) {
        return true
    }

    val display = context.resources.displayMetrics
    val aspectRatio = maxOf(display.widthPixels, display.heightPixels).toFloat() /
        minOf(display.widthPixels, display.heightPixels).toFloat()

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
    posture: DevicePosture,
): DeviceFormFactor {
    return when {
        isTV -> DeviceFormFactor.TV
        isFoldable || posture != DevicePosture.Normal -> DeviceFormFactor.FOLDABLE
        isTablet -> DeviceFormFactor.TABLET
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> DeviceFormFactor.DESKTOP
        else -> DeviceFormFactor.PHONE
    }
}

/**
 * Determine screen orientation with TV and posture considerations
 */
private fun determineOrientation(
    configuration: Configuration,
    isTV: Boolean,
    posture: DevicePosture,
): ScreenOrientation {
    return when {
        isTV -> ScreenOrientation.TV_LANDSCAPE
        posture is DevicePosture.TableTop -> ScreenOrientation.LANDSCAPE
        posture is DevicePosture.Book -> ScreenOrientation.LANDSCAPE
        posture is DevicePosture.Separating -> ScreenOrientation.LANDSCAPE
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> ScreenOrientation.LANDSCAPE
        else -> ScreenOrientation.PORTRAIT
    }
}

/**
 * Derive posture hints from WindowLayoutInfo
 */
private fun deriveDevicePosture(windowLayoutInfo: WindowLayoutInfo): DevicePosture {
    val foldingFeature = windowLayoutInfo.displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull() ?: return DevicePosture.Normal

    return when {
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> DevicePosture.TableTop
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> DevicePosture.Book
        foldingFeature.isSeparating -> DevicePosture.Separating(foldingFeature.orientation)
        foldingFeature.state == FoldingFeature.State.FLAT -> DevicePosture.Flat
        else -> DevicePosture.Normal
    }
}

/**
 * Calculate content padding based on form factor and orientation
 */
private fun calculateContentPadding(
    formFactor: DeviceFormFactor,
    orientation: ScreenOrientation,
    posture: DevicePosture,
    isTV: Boolean,
): PaddingValues {
    val prefersLandscapeSpacing = orientation != ScreenOrientation.PORTRAIT || posture != DevicePosture.Normal
    return when {
        isTV -> if (orientation == ScreenOrientation.TV_LANDSCAPE) {
            PaddingValues(horizontal = 56.dp, vertical = 28.dp)
        } else {
            PaddingValues(horizontal = 48.dp, vertical = 24.dp)
        }
        formFactor == DeviceFormFactor.TABLET || formFactor == DeviceFormFactor.FOLDABLE -> {
            if (prefersLandscapeSpacing) {
                PaddingValues(horizontal = 32.dp, vertical = 20.dp)
            } else {
                PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            }
        }
        formFactor == DeviceFormFactor.DESKTOP -> PaddingValues(horizontal = 32.dp, vertical = 20.dp)
        else -> PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    }
}

/**
 * Determine the appropriate navigation suite type
 */
private fun determineNavigationSuiteType(
    windowSizeClass: WindowSizeClass,
    isTV: Boolean,
): NavigationSuiteType {
    return when {
        isTV -> NavigationSuiteType.None // TV uses custom navigation
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> NavigationSuiteType.NavigationDrawer
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }
}

/**
 * Calculate grid columns based on screen size, posture, and device type
 */
private fun calculateGridColumns(
    windowSizeClass: WindowSizeClass,
    isTV: Boolean,
    posture: DevicePosture,
    orientation: ScreenOrientation,
    formFactor: DeviceFormFactor,
): Int {
    if (isTV) {
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 6
            else -> 4
        }
    }

    val baseColumns = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 4
        WindowWidthSizeClass.Medium -> 3
        else -> 2
    }

    val postureBonus = when (posture) {
        is DevicePosture.Book, is DevicePosture.Separating -> 1
        else -> 0
    }
    val landscapeBonus = if (orientation != ScreenOrientation.PORTRAIT && baseColumns < 4) 1 else 0
    val target = baseColumns + postureBonus + landscapeBonus
    val maxColumns = if (formFactor == DeviceFormFactor.TABLET || formFactor == DeviceFormFactor.FOLDABLE) 5 else 4

    return target.coerceAtMost(maxColumns)
}

/**
 * Calculate carousel item width based on form factor and orientation
 */
private fun calculateCarouselItemWidth(
    formFactor: DeviceFormFactor,
    isTV: Boolean,
    orientation: ScreenOrientation,
): Dp {
    return when {
        isTV -> if (orientation == ScreenOrientation.TV_LANDSCAPE) 240.dp else 220.dp
        formFactor == DeviceFormFactor.TABLET || formFactor == DeviceFormFactor.FOLDABLE -> {
            if (orientation == ScreenOrientation.PORTRAIT) 200.dp else 220.dp
        }
        formFactor == DeviceFormFactor.DESKTOP -> 220.dp
        else -> 160.dp
    }
}

/**
 * Calculate carousel item height based on form factor and orientation
 */
private fun calculateCarouselItemHeight(
    formFactor: DeviceFormFactor,
    isTV: Boolean,
    orientation: ScreenOrientation,
): Dp {
    return when {
        isTV -> if (orientation == ScreenOrientation.TV_LANDSCAPE) 360.dp else 320.dp
        formFactor == DeviceFormFactor.TABLET || formFactor == DeviceFormFactor.FOLDABLE -> {
            if (orientation == ScreenOrientation.PORTRAIT) 300.dp else 320.dp
        }
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
