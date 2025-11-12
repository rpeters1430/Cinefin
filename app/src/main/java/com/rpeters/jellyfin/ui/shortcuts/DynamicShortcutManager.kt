package com.rpeters.jellyfin.ui.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rpeters.jellyfin.MainActivity
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.navigation.Screen
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Helper responsible for publishing dynamic launcher shortcuts that mirror the user's
 * "Continue Watching" queue. The shortcuts deep link into the correct detail screen so
 * playback can resume with a single tap from the launcher.
 */
object DynamicShortcutManager {

    private const val CONTINUE_PREFIX = "continue_watching_"
    private const val MAX_CONTINUE_WATCHING_SHORTCUTS = 4
    private const val SHORT_LABEL_MAX_LENGTH = 24

    fun updateContinueWatchingShortcuts(context: Context, items: List<BaseItemDto>) {
        val maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        if (maxShortcuts <= 0) return

        val shortcuts = items
            .filter { it.id != null && !it.name.isNullOrBlank() }
            .take(maxShortcuts.coerceAtMost(MAX_CONTINUE_WATCHING_SHORTCUTS))
            .mapNotNull { item ->
                buildShortcut(context, item)
            }

        val existingDynamic = ShortcutManagerCompat.getDynamicShortcuts(context)
        val preservedShortcuts = existingDynamic.filterNot { it.id.startsWith(CONTINUE_PREFIX) }
        val continueShortcutIds = existingDynamic
            .map { it.id }
            .filter { it.startsWith(CONTINUE_PREFIX) }

        val availableSlots = (maxShortcuts - preservedShortcuts.size).coerceAtLeast(0)
        val trimmedShortcuts = shortcuts.take(availableSlots)

        if (trimmedShortcuts.isEmpty()) {
            if (continueShortcutIds.isNotEmpty()) {
                ShortcutManagerCompat.setDynamicShortcuts(context, preservedShortcuts)
            }
            return
        }

        val newIds = trimmedShortcuts.map { it.id }.toSet()
        val toRemove = continueShortcutIds.filterNot { it in newIds }
        if (toRemove.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, toRemove)
        }

        ShortcutManagerCompat.setDynamicShortcuts(context, preservedShortcuts + trimmedShortcuts)
    }

    private fun buildShortcut(context: Context, item: BaseItemDto): ShortcutInfoCompat? {
        val id = item.id?.toString() ?: return null
        val route = when (item.type) {
            BaseItemKind.MOVIE -> Screen.MovieDetail.createRoute(id)
            BaseItemKind.EPISODE -> Screen.TVEpisodeDetail.createRoute(id)
            else -> Screen.ItemDetail.createRoute(id)
        }

        val shortLabel = item.name?.take(SHORT_LABEL_MAX_LENGTH) ?: return null
        val longLabel = when (item.type) {
            BaseItemKind.EPISODE ->
                item.seriesName ?: context.getString(R.string.shortcut_continue_watching_long)

            else -> context.getString(R.string.shortcut_continue_watching_long)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_SHORTCUT_DESTINATION, route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return ShortcutInfoCompat.Builder(context, "$CONTINUE_PREFIX$id")
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_continue))
            .setIntent(intent)
            .build()
    }
}
