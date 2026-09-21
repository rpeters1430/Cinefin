package com.rpeters.jellyfin.ui.screens.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpeters.jellyfin.ui.components.OfficialRatingBadge
import com.rpeters.jellyfin.ui.components.RatingRow
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.ImmersiveShapes
import org.jellyfin.sdk.model.api.BaseItemDto

/** Density-pass poster overlapping the collapsing backdrop (item 4 of the redesign spec). */
private val DetailPosterWidth = 104.dp
private val DetailPosterHeight = 156.dp

@Composable
fun MovieHeroContent(
    movie: BaseItemDto,
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Poster overlapping the backdrop above it.
        OptimizedImage(
            imageUrl = posterUrl,
            contentDescription = movie.name,
            contentScale = ContentScale.Crop,
            size = ImageSize.CARD,
            quality = ImageQuality.HIGH,
            modifier = Modifier
                .width(DetailPosterWidth)
                .height(DetailPosterHeight)
                .clip(ImmersiveShapes.PosterImage),
        )

        // Left-aligned title block.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = movie.name ?: "Unknown Movie",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Start,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Primary metadata row (Year, Duration, Rating)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                movie.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }

                movie.runTimeTicks?.let { ticks ->
                    val minutes = (ticks / 10_000 / 1000 / 60).toInt()
                    if (minutes > 0) {
                        val hours = minutes / 60
                        val remainingMinutes = minutes % 60
                        val durationText = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }

                movie.officialRating?.let { rating ->
                    OfficialRatingBadge(rating = rating)
                }
            }

            RatingRow(
                communityRating = movie.communityRating,
                criticRating = movie.criticRating,
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
