package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.getCommunityRatingColor
import com.rpeters.jellyfin.ui.theme.getCriticRatingColor
import com.rpeters.jellyfin.ui.theme.getOfficialRatingColor
import java.util.Locale

/**
 * Community rating (e.g. TMDb/IMDb-style, 0-10 scale) as a colored gradient pill.
 * Gradient color reflects score tier via [getCommunityRatingColor].
 */
@Composable
fun CommunityRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    val tint = getCommunityRatingColor(rating)
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(colors = listOf(tint.copy(alpha = 0.85f), tint)),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
    }
}

/**
 * Critic rating (e.g. Rotten Tomatoes-style, 0-100 scale) as a colored gradient pill.
 * Only meaningful when the source data is non-null — callers should guard with `?.let`.
 */
@Composable
fun CriticRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
) {
    val tint = getCriticRatingColor(rating)
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(colors = listOf(tint.copy(alpha = 0.85f), tint)),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "🍅")
            Text(
                text = "${rating.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
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
        shape = RoundedCornerShape(6.dp),
        color = tintColor.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.6f)),
    ) {
        Text(
            text = rating,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tintColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
