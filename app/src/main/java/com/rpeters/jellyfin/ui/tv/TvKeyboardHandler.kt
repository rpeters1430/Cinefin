@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation.NavController
import com.rpeters.jellyfin.OptInAppExperimentalApis

/**
 * TV remote control shortcuts and keyboard navigation handler
 */
object TvKeyboardHandler {

    /**
     * Handle global TV remote shortcuts
     */
    fun handleGlobalTvKeys(
        keyEvent: KeyEvent,
        navController: NavController? = null,
        focusManager: FocusManager? = null,
        onBack: (() -> Unit)? = null,
        onHome: (() -> Unit)? = null,
        onMenu: (() -> Unit)? = null,
        onPlayPause: (() -> Unit)? = null,
        onStop: (() -> Unit)? = null,
        onSearch: (() -> Unit)? = null,
        onSeekForward: (() -> Unit)? = null,
        onSeekBackward: (() -> Unit)? = null,
        onInfo: (() -> Unit)? = null,
        onGuide: (() -> Unit)? = null,
        onChannelUp: (() -> Unit)? = null,
        onChannelDown: (() -> Unit)? = null,
        onQuickAccess: ((Int) -> Unit)? = null,
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        return when (keyEvent.key) {
            // Navigation keys
            Key.Back, Key.Escape -> {
                when {
                    onBack != null -> {
                        onBack.invoke()
                        true
                    }
                    navController != null -> navController.navigateUp()
                    else -> false
                }
            }
            Key.H -> {
                if (onHome != null) {
                    onHome.invoke()
                    true
                } else {
                    false
                }
            }
            Key.Menu -> {
                if (onMenu != null) {
                    onMenu.invoke()
                    true
                } else {
                    false
                }
            }

            // Media control keys
            Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause, Key.Spacebar -> {
                if (onPlayPause != null) {
                    onPlayPause.invoke()
                    true
                } else {
                    false
                }
            }
            Key.MediaStop -> {
                if (onStop != null) {
                    onStop.invoke()
                    true
                } else {
                    false
                }
            }
            Key.MediaSkipForward, Key.MediaNext -> {
                if (onSeekForward != null) {
                    onSeekForward.invoke()
                    true
                } else {
                    false
                }
            }
            Key.MediaSkipBackward, Key.MediaPrevious -> {
                if (onSeekBackward != null) {
                    onSeekBackward.invoke()
                    true
                } else {
                    false
                }
            }
            Key.MediaFastForward -> {
                if (onSeekForward != null) {
                    onSeekForward.invoke()
                    true
                } else {
                    false
                }
            }
            Key.MediaRewind -> {
                if (onSeekBackward != null) {
                    onSeekBackward.invoke()
                    true
                } else {
                    false
                }
            }

            // Search key
            Key.Search, Key.F -> {
                if (onSearch != null) {
                    onSearch.invoke()
                    true
                } else {
                    false
                }
            }

            // Info/Details key
            Key.Info, Key.I -> {
                if (onInfo != null) {
                    onInfo.invoke()
                    true
                } else {
                    false
                }
            }

            // Guide key
            Key.Guide, Key.G -> {
                if (onGuide != null) {
                    onGuide.invoke()
                    true
                } else {
                    false
                }
            }

            // Channel up/down (for library navigation)
            Key.ChannelUp -> {
                if (onChannelUp != null) {
                    onChannelUp.invoke()
                    true
                } else {
                    false
                }
            }
            Key.ChannelDown -> {
                if (onChannelDown != null) {
                    onChannelDown.invoke()
                    true
                } else {
                    false
                }
            }

            // Number keys for quick navigation
            Key.One -> {
                if (onQuickAccess != null) {
                    onQuickAccess.invoke(1)
                    true
                } else {
                    false
                }
            }
            Key.Two -> {
                if (onQuickAccess != null) {
                    onQuickAccess.invoke(2)
                    true
                } else {
                    false
                }
            }
            Key.Three -> {
                if (onQuickAccess != null) {
                    onQuickAccess.invoke(3)
                    true
                } else {
                    false
                }
            }
            Key.Four -> {
                if (onQuickAccess != null) {
                    onQuickAccess.invoke(4)
                    true
                } else {
                    false
                }
            }
            Key.Five -> {
                if (onQuickAccess != null) {
                    onQuickAccess.invoke(5)
                    true
                } else {
                    false
                }
            }

            // Color keys (red, green, yellow, blue)
            Key.Unknown -> {
                // Handle color keys if needed
                false
            }

            else -> false
        }
    }

    /**
     * Handle contextual navigation within content screens
     */
    fun handleContentNavigationKeys(
        keyEvent: KeyEvent,
        focusManager: FocusManager,
        onFavorite: (() -> Unit)? = null,
        onWatched: (() -> Unit)? = null,
        onMore: (() -> Unit)? = null,
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        return when (keyEvent.key) {
            // Quick actions
            Key.F -> {
                onFavorite?.invoke()
                true
            }
            Key.W -> {
                onWatched?.invoke()
                true
            }
            Key.M -> {
                onMore?.invoke()
                true
            }

            else -> false
        }
    }
}

/**
 * Modifier extension for TV remote keyboard handling
 */
@Composable
fun Modifier.tvKeyboardHandler(
    navController: NavController? = null,
    focusManager: FocusManager? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onPlayPause: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onWatched: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onSeekForward: (() -> Unit)? = null,
    onSeekBackward: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onGuide: (() -> Unit)? = null,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    onQuickAccess: ((Int) -> Unit)? = null,
): Modifier {
    return this.onKeyEvent { keyEvent ->
        // First try global TV keys
        val globalHandled = TvKeyboardHandler.handleGlobalTvKeys(
            keyEvent = keyEvent,
            navController = navController,
            focusManager = focusManager,
            onBack = onBack,
            onHome = onHome,
            onMenu = onMenu,
            onPlayPause = onPlayPause,
            onStop = onStop,
            onSearch = onSearch,
            onSeekForward = onSeekForward,
            onSeekBackward = onSeekBackward,
            onInfo = onInfo,
            onGuide = onGuide,
            onChannelUp = onChannelUp,
            onChannelDown = onChannelDown,
            onQuickAccess = onQuickAccess,
        )

        if (globalHandled) {
            true
        } else {
            // Then try content navigation keys
            TvKeyboardHandler.handleContentNavigationKeys(
                keyEvent = keyEvent,
                focusManager = focusManager ?: return@onKeyEvent false,
                onFavorite = onFavorite,
                onWatched = onWatched,
                onMore = onMore,
            )
        }
    }
}

/**
 * TV-specific key bindings and shortcuts guide
 */
object TvKeyBindings {
    const val NAVIGATION_HELP = """
        TV Remote Navigation:
        • D-pad/Arrow keys: Navigate through content
        • Center/Enter: Select item
        • Back/Escape: Go back
        • Home/H: Return to home screen
        • Menu: Open context menu
        
        Media Controls:
        • Play/Pause/Space: Toggle playback
        • Stop: Stop playback
        • Fast Forward: Skip forward
        • Rewind: Skip backward
        
        Quick Actions:
        • Search/F: Open search
        • Info/I: Show item details
        • F: Toggle favorite
        • W: Toggle watched status
        • M: More options
        
        Quick Access (Number keys):
        • 1: Home
        • 2: Movies
        • 3: TV Shows
        • 4: Music
        • 5: Settings
        
        Channel Keys:
        • Channel Up: Previous library
        • Channel Down: Next library
    """

    /**
     * Get help text for current screen context
     */
    fun getContextualHelp(screenType: TvScreenType): String {
        return when (screenType) {
            TvScreenType.HOME -> "Use D-pad to navigate carousels. Press Center to select items."
            TvScreenType.LIBRARY -> "Use D-pad to browse content. Press Info for details."
            TvScreenType.DETAILS -> "Press Play to start, Star to favorite, W to mark watched."
            TvScreenType.PLAYER -> "Use media keys for playback control. Back to return."
            TvScreenType.SEARCH -> "Type to search, use D-pad to select results."
            TvScreenType.SETTINGS -> "Use D-pad to navigate settings. Center to select."
        }
    }
}

/**
 * Screen types for contextual keyboard handling
 */
enum class TvScreenType {
    HOME,
    LIBRARY,
    DETAILS,
    PLAYER,
    SEARCH,
    SETTINGS,
}

/**
 * TV-specific accessibility announcements for keyboard navigation
 */
object TvAccessibilityAnnouncements {
    const val FOCUS_MOVED_TO_CAROUSEL = "Focus moved to %s carousel"
    const val FOCUS_MOVED_TO_ITEM = "Focused on %s"
    const val ACTION_PERFORMED = "%s performed"
    const val NAVIGATION_HINT = "Use arrow keys to navigate, enter to select"
}

/**
 * TV gamepad/remote control configuration
 */
data class TvControllerConfig(
    val enableDpadNavigation: Boolean = true,
    val enableMediaKeys: Boolean = true,
    val enableQuickAccess: Boolean = true,
    val enableColorKeys: Boolean = false,
    val enableVoiceSearch: Boolean = true,
    val customKeyBindings: Map<Key, String> = emptyMap(),
)

/**
 * Store TV controller configuration
 */
class TvControllerManager {
    private var config = TvControllerConfig()

    fun updateConfig(newConfig: TvControllerConfig) {
        config = newConfig
    }

    fun getConfig(): TvControllerConfig = config

    fun isKeyEnabled(key: Key): Boolean {
        return when {
            key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight) ->
                config.enableDpadNavigation
            key in listOf(Key.MediaPlay, Key.MediaPause, Key.MediaStop) ->
                config.enableMediaKeys
            key in listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five) ->
                config.enableQuickAccess
            else -> true
        }
    }
}
