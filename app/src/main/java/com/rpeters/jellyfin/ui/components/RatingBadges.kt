package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.getCommunityRatingColor
import com.rpeters.jellyfin.ui.theme.getCriticRatingColor
import com.rpeters.jellyfin.ui.theme.getOfficialRatingColor
import java.util.Locale

/**
 * Community rating (e.g. TMDb/IMDb-style, 0-10 scale) as a clean tonal badge.
 * Badge tint reflects score tier via [getCommunityRatingColor].
 */
@Composable
fun CommunityRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    val tint = getCommunityRatingColor(rating)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

/**
 * Critic rating (e.g. Rotten Tomatoes-style, 0-100 scale) as a clean tonal badge.
 * Only meaningful when the source data is non-null — callers should guard with `?.let`.
 */
@Composable
fun CriticRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    val tint = getCriticRatingColor(rating)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${rating.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

/**
 * Official (age/content) rating, e.g. "PG-13", as an outlined pill colored per [getOfficialRatingColor].
 */
@Composable
fun OfficialRatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
) {
    val tintColor = getOfficialRatingColor(rating)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = tintColor.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.5f)),
    ) {
        Text(
            text = rating,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tintColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Lays out [CommunityRatingBadge] and [CriticRatingBadge] side by side for whichever
 * rating values are non-null. Renders nothing if both are null.
 */
@Composable
fun RatingRow(
    communityRating: Float?,
    criticRating: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (communityRating == null && criticRating == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        communityRating?.let { CommunityRatingBadge(rating = it) }
        criticRating?.let { CriticRatingBadge(rating = it) }
    }
}
