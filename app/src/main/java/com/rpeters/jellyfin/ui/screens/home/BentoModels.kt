package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.lazy.grid.GridItemSpan

/**
 * Represents the types of items in the Expressive Bento Grid home screen.
 */
sealed class BentoItemType {
    /**
     * Large featured items that span the full width of the grid.
     */
    object Featured : BentoItemType()

    /**
     * Smaller square or rectangular tiles that span a single column.
     */
    object Action : BentoItemType()

    /**
     * Wider utility banners that span the full width of the grid.
     */
    object Wide : BentoItemType()
}

/**
 * Calculates the [GridItemSpan] for a given [BentoItemType] based on the total [columns] in the grid.
 *
 * @param itemType The type of bento item.
 * @param columns The total number of columns in the lazy grid.
 * @return The appropriate [GridItemSpan] for the item type.
 */
fun getBentoSpan(itemType: BentoItemType, columns: Int): GridItemSpan {
    return when (itemType) {
        BentoItemType.Featured -> GridItemSpan(columns)
        BentoItemType.Action -> GridItemSpan(1)
        BentoItemType.Wide -> GridItemSpan(columns)
    }
}
