package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.models.EventStatus
import com.example.domain.models.SportEvent
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvPrimary
import com.example.ui.theme.TvSecondary
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEventCard(
    event: SportEvent,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 264.dp,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .testTag("event_card_${event.id}"),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = TvSurface,
            focusedContainerColor = TvSurface,
            pressedContainerColor = TvSurface,
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, TvBorderSubtle)),
            focusedBorder = Border(BorderStroke(3.dp, TvFocusRing)),
            pressedBorder = Border(BorderStroke(3.dp, TvPrimary)),
        ),
        scale = CardDefaults.scale(
            focusedScale = 1.05f,
            pressedScale = 0.98f,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    model = event.posterUrl,
                    contentDescription = event.title,
                    placeholder = painterResource(id = R.drawable.img_sports_fallback),
                    error = painterResource(id = R.drawable.img_sports_fallback),
                    fallback = painterResource(id = R.drawable.img_sports_fallback),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    TvBackground.copy(alpha = 0.9f),
                                ),
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = event.status)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (event.sources.size > 1) {
                            Text(
                                text = "${event.sources.size} SRC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TvTextPrimary,
                                modifier = Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.68f),
                                        shape = RoundedCornerShape(5.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                        if (isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Favorited",
                                tint = TvSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (event.viewers > 0 && (event.status == EventStatus.LIVE || event.status == EventStatus.ALWAYS_LIVE)) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.68f),
                                shape = RoundedCornerShape(5.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Viewers",
                            tint = TvTextPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "${event.viewers}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TvTextPrimary,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val subtitle = buildString {
                    append(event.leagueTag ?: event.categoryName)
                    when (event.status) {
                        EventStatus.UPCOMING -> {
                            append(" • ")
                            append(TimeFormatters.formatLocalEventTime(event.startsAtEpochSec))
                        }
                        EventStatus.ALWAYS_LIVE -> {
                            event.primarySource?.broadcaster?.let {
                                append(" • ")
                                append(it)
                            }
                        }
                        EventStatus.LIVE -> {
                            event.primarySource?.broadcaster?.let {
                                append(" • ")
                                append(it)
                            }
                        }
                        EventStatus.ENDED -> {
                            append(" • Ended")
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
