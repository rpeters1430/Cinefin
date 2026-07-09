package com.rpeters.jellyfin.data.preferences

/**
 * Theme mode options for the application.
 */
enum class ThemeMode {
    /** Follow system dark mode setting */
    SYSTEM,

    /** Always use light theme */
    LIGHT,

    /** Always use dark theme */
    DARK,

    /** Always use pure black theme (AMOLED-optimized) */
    AMOLED_BLACK,
}

/**
 * Contrast level for theme colors (Material 3 accessibility).
 */
enum class ContrastLevel {
    /** Standard contrast (default) */
    STANDARD,

    /** Medium contrast for better readability */
    MEDIUM,

    /** High contrast for maximum accessibility */
    HIGH,
}

/**
 * Custom accent color options when dynamic color is disabled.
 */
enum class AccentColor {
    /** Matches the actual jellyfin-web client palette (accent blue, near-black surfaces) */
    JELLYFIN_CLASSIC,

    /** Default Jellyfin purple */
    JELLYFIN_PURPLE,

    /** Jellyfin blue */
    JELLYFIN_BLUE,

    /** Jellyfin teal */
    JELLYFIN_TEAL,

    /** Material purple */
    MATERIAL_PURPLE,

    /** Material blue */
    MATERIAL_BLUE,

    /** Material green */
    MATERIAL_GREEN,

    /** Material red */
    MATERIAL_RED,

    /** Material orange */
    MATERIAL_ORANGE,

    /** User-picked custom seed color (see [ThemePreferences.customAccentColorArgb]) */
    CUSTOM,
}

/**
 * Font options for the application.
 */
enum class AppFont {
    /** System default font */
    DEFAULT,

    /** Sans-serif font */
    SANS_SERIF,

    /** Serif font */
    SERIF,

    /** Monospace font */
    MONOSPACE,
}

/**
 * Data class representing user theme preferences.
 */
data class ThemePreferences(
    /**
     * Theme mode selection (System, Light, Dark, AMOLED Black).
     */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /**
     * Whether to use Material You dynamic colors on Android 12+.
     * When enabled, colors are extracted from the system wallpaper.
     */
    val useDynamicColors: Boolean = true,

    /**
     * Custom accent color to use when dynamic colors are disabled.
     */
    val accentColor: AccentColor = AccentColor.JELLYFIN_PURPLE,

    /**
     * ARGB value of the user-picked seed color, used when [accentColor] is [AccentColor.CUSTOM].
     * Defaults to the Jellyfin expressive primary purple.
     */
    val customAccentColorArgb: Int = DEFAULT_CUSTOM_ACCENT_ARGB,

    /**
     * Contrast level for theme colors.
     */
    val contrastLevel: ContrastLevel = ContrastLevel.STANDARD,
    
    /**
     * Application font family.
     */
    val appFont: AppFont = AppFont.DEFAULT,

    /**
     * Whether to use themed app icon on Android 13+.
     * Themed icons adapt to wallpaper colors.
     */
    val useThemedIcon: Boolean = true,

    /**
     * Whether to enable edge-to-edge layout.
     */
    val enableEdgeToEdge: Boolean = true,

    /**
     * Whether to respect system animation settings (reduce motion).
     */
    val respectReduceMotion: Boolean = true,
) {
    companion object {
        /**
         * Default theme preferences.
         */
        val DEFAULT = ThemePreferences()
    }
}

/** ARGB for [ThemePreferences.customAccentColorArgb]'s default; matches ui.theme.ExpressivePrimary. */
const val DEFAULT_CUSTOM_ACCENT_ARGB: Int = 0xFF6442D6.toInt()
