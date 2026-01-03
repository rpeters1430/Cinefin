package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.sdk.model.api.BaseItemDto

/** Available filtering options for library items. */
enum class FilterType(val displayName: String) {
    ALL("All"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    ALPHABETICAL("A-Z"),
    ;

    companion object {
        fun getDefault() = ALL
        fun getAllFilters() = entries
    }
}

/** Simple filter implementation used by multiple screens. */
fun applyFilter(items: List<BaseItemDto>, filter: FilterType): List<BaseItemDto> = when (filter) {
    FilterType.ALL -> items
    FilterType.RECENT -> items.sortedByDescending { it.dateCreated }
    FilterType.FAVORITES -> items.filter { it.userData?.isFavorite == true }
    FilterType.ALPHABETICAL -> items.sortedBy { it.sortName ?: it.name }
}

/** Row of chips allowing the user to select a filter. */
@Composable
fun LibraryFilterRow(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    libraryType: LibraryType,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.FilterChipSpacing),
        contentPadding = PaddingValues(
            horizontal = LibraryScreenDefaults.ContentPadding,
            vertical = LibraryScreenDefaults.FilterChipSpacing,
        ),
    ) {
        items(
            items = FilterType.getAllFilters(),
            key = { filter -> filter.name },
            contentType = { "library_filter_chip" },
        ) { filter ->
            FilterChip(
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.displayName) },
                selected = selectedFilter == filter,
                leadingIcon = if (filter == FilterType.FAVORITES) {
                    {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = libraryType.color.copy(alpha = LibraryScreenDefaults.ColorAlpha),
                    selectedLabelColor = libraryType.color,
                    selectedLeadingIconColor = libraryType.color,
                ),
            )
        }
    }
}
